package de.uni_passau.fim.se2.sa.slicing.graph;

import static org.junit.jupiter.api.Assertions.*;

import de.uni_passau.fim.se2.sa.slicing.cfg.Node;
import de.uni_passau.fim.se2.sa.slicing.cfg.ProgramGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.Set;

class ProgramDependenceGraphTest {

    private ClassNode calculatorClassNode;
    private MethodNode evaluateMethodNode;
    private ClassNode gcdClassNode;
    private MethodNode gcdMethodNode;
    private ClassNode simpleIntegerClassNode;
    private MethodNode fooMethodNode;

    @BeforeEach
    void setUp() throws IOException {
        // Load Calculator class
        ClassReader calculatorReader = new ClassReader("de.uni_passau.fim.se2.sa.examples.Calculator");
        calculatorClassNode = new ClassNode();
        calculatorReader.accept(calculatorClassNode, 0);
        evaluateMethodNode = calculatorClassNode.methods.stream()
                .filter(m -> "evaluate".equals(m.name) && "(Ljava/lang/String;)I".equals(m.desc))
                .findFirst()
                .orElseThrow();

        // Load GCD class
        ClassReader gcdReader = new ClassReader("de.uni_passau.fim.se2.sa.examples.GCD");
        gcdClassNode = new ClassNode();
        gcdReader.accept(gcdClassNode, 0);
        gcdMethodNode = gcdClassNode.methods.stream()
                .filter(m -> "gcd".equals(m.name) && "(II)I".equals(m.desc))
                .findFirst()
                .orElseThrow();

        // Load SimpleInteger class
        ClassReader simpleReader = new ClassReader("de.uni_passau.fim.se2.sa.examples.SimpleInteger");
        simpleIntegerClassNode = new ClassNode();
        simpleReader.accept(simpleIntegerClassNode, 0);
        fooMethodNode = simpleIntegerClassNode.methods.stream()
                .filter(m -> "foo".equals(m.name) && "()I".equals(m.desc))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void testConstructorWithClassAndMethod() {
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(calculatorClassNode, evaluateMethodNode);
        assertNotNull(pdg);
        assertNotNull(pdg.getCFG());
        assertTrue(pdg.getCFG().getNodes().size() > 0);
    }

    @Test
    void testConstructorWithProgramGraph() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("test1");
        Node node2 = new Node("test2");
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addEdge(node1, node2);

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        assertNotNull(pdg);

        ProgramGraph result = pdg.computeResult();
        assertNotNull(result);
        assertEquals(2, result.getNodes().size());
        assertTrue(result.getNodes().contains(node1));
        assertTrue(result.getNodes().contains(node2));
        assertTrue(result.getSuccessors(node1).contains(node2));
    }

    @Test
    void testConstructorWithNullClass() {
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(null, null);
        assertNotNull(pdg);
        assertNull(pdg.getCFG());

        ProgramGraph result = pdg.computeResult();
        assertNotNull(result);
        assertEquals(0, result.getNodes().size());
    }

    @Test
    void testComputeResult() {
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = pdg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // Verify that nodes from both CDG and DDG are included
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertFalse(nodes.isEmpty());

        // Verify that some control and data dependencies exist
        boolean hasEdges = false;
        for (Node node : nodes) {
            if (!result.getSuccessors(node).isEmpty()) {
                hasEdges = true;
                break;
            }
        }
        assertTrue(hasEdges, "PDG should have some dependency edges");
    }

    @Test
    void testComputeResultWithComplexMethod() {
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = pdg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // GCD method has loops and conditions, should have many dependencies
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "GCD method should have multiple nodes");

        // Verify dependencies exist
        int totalEdges = 0;
        for (Node node : nodes) {
            totalEdges += result.getSuccessors(node).size();
        }
        assertTrue(totalEdges > 0, "Should have dependency edges");
    }

    @Test
    void testComputeResultCaching() {
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(calculatorClassNode, evaluateMethodNode);

        ProgramGraph result1 = pdg.computeResult();
        ProgramGraph result2 = pdg.computeResult();

        assertSame(result1, result2, "computeResult should return cached result");
    }

    @Test
    void testBackwardSliceWithSingleNode() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("test1");
        graph.addNode(node1);

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice = pdg.backwardSlice(node1);

        assertNotNull(slice);
        assertEquals(1, slice.size());
        assertTrue(slice.contains(node1));
    }

    @Test
    void testBackwardSliceWithChain() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        Node node3 = new Node("node3");

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addEdge(node1, node2);
        graph.addEdge(node2, node3);

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice = pdg.backwardSlice(node3);

        assertNotNull(slice);
        assertEquals(3, slice.size());
        assertTrue(slice.contains(node1));
        assertTrue(slice.contains(node2));
        assertTrue(slice.contains(node3));
    }

    @Test
    void testBackwardSliceWithDiamond() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        Node node3 = new Node("node3");
        Node node4 = new Node("node4");

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);

        // Diamond pattern: 1 -> 2, 1 -> 3, 2 -> 4, 3 -> 4
        graph.addEdge(node1, node2);
        graph.addEdge(node1, node3);
        graph.addEdge(node2, node4);
        graph.addEdge(node3, node4);

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice = pdg.backwardSlice(node4);

        assertNotNull(slice);
        assertEquals(4, slice.size());
        assertTrue(slice.contains(node1));
        assertTrue(slice.contains(node2));
        assertTrue(slice.contains(node3));
        assertTrue(slice.contains(node4));
    }

    @Test
    void testBackwardSliceWithCycle() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        Node node3 = new Node("node3");

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // Cycle: 1 -> 2 -> 3 -> 1
        graph.addEdge(node1, node2);
        graph.addEdge(node2, node3);
        graph.addEdge(node3, node1);

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice = pdg.backwardSlice(node2);

        assertNotNull(slice);
        assertEquals(3, slice.size());
        assertTrue(slice.contains(node1));
        assertTrue(slice.contains(node2));
        assertTrue(slice.contains(node3));
    }

    @Test
    void testBackwardSliceWithNoIncomingEdges() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addEdge(node1, node2);

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice = pdg.backwardSlice(node1);

        assertNotNull(slice);
        assertEquals(1, slice.size());
        assertTrue(slice.contains(node1));
    }

    @Test
    void testBackwardSliceRealExample() {
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(simpleIntegerClassNode, fooMethodNode);
        ProgramGraph graph = pdg.computeResult();

        assertNotNull(graph);
        Set<Node> nodes = (Set<Node>) graph.getNodes();
        assertFalse(nodes.isEmpty());

        // Pick any node and test backward slice
        Node targetNode = nodes.iterator().next();
        Set<Node> slice = pdg.backwardSlice(targetNode);

        assertNotNull(slice);
        assertFalse(slice.isEmpty());
        assertTrue(slice.contains(targetNode));

        // All nodes in slice should be reachable from the graph
        for (Node sliceNode : slice) {
            assertTrue(nodes.contains(sliceNode));
        }
    }

    @Test
    void testBackwardSliceWithMultiplePredecessors() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        Node node3 = new Node("node3");
        Node node4 = new Node("node4");

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);

        // Multiple predecessors: 1 -> 4, 2 -> 4, 3 -> 4
        graph.addEdge(node1, node4);
        graph.addEdge(node2, node4);
        graph.addEdge(node3, node4);

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice = pdg.backwardSlice(node4);

        assertNotNull(slice);
        assertEquals(4, slice.size());
        assertTrue(slice.contains(node1));
        assertTrue(slice.contains(node2));
        assertTrue(slice.contains(node3));
        assertTrue(slice.contains(node4));
    }

    @Test
    void testBackwardSliceWithSelfLoop() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addEdge(node1, node2);
        graph.addEdge(node2, node2); // Self loop

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice = pdg.backwardSlice(node2);

        assertNotNull(slice);
        assertEquals(2, slice.size());
        assertTrue(slice.contains(node1));
        assertTrue(slice.contains(node2));
    }

    @Test
    void testGetCFG() {
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph cfg = pdg.getCFG();

        assertNotNull(cfg);
        assertTrue(cfg.getNodes().size() > 0);

        // Verify it's the actual CFG
        assertTrue(cfg.getEntry().isPresent());
        assertTrue(cfg.getExit().isPresent());
    }

    @Test
    void testGetCFGWithNullClass() {
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(null, null);
        ProgramGraph cfg = pdg.getCFG();

        assertNull(cfg);
    }

    @Test
    void testIntegrationWithRealMethods() {
        // Test with Calculator
        ProgramDependenceGraph calculatorPdg = new ProgramDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph calculatorResult = calculatorPdg.computeResult();
        assertNotNull(calculatorResult);
        assertTrue(calculatorResult.getNodes().size() > 5);

        // Test with GCD
        ProgramDependenceGraph gcdPdg = new ProgramDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph gcdResult = gcdPdg.computeResult();
        assertNotNull(gcdResult);
        assertTrue(gcdResult.getNodes().size() > 5);

        // Results should be different
        assertNotEquals(calculatorResult.getNodes().size(), gcdResult.getNodes().size());
    }

    @Test
    void testNodeEquality() {
        ProgramGraph graph = new ProgramGraph();
        Node node1 = new Node("test");
        Node node2 = new Node("test");

        graph.addNode(node1);
        graph.addNode(node2);

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice1 = pdg.backwardSlice(node1);
        Set<Node> slice2 = pdg.backwardSlice(node2);

        assertEquals(slice1, slice2);
    }

    @Test
    void testEmptyGraph() {
        ProgramGraph emptyGraph = new ProgramGraph();
        ProgramDependenceGraph pdg = new ProgramDependenceGraph(emptyGraph);

        ProgramGraph result = pdg.computeResult();
        assertNotNull(result);
        assertEquals(0, result.getNodes().size());
    }

    @Test
    void testLargeBackwardSlice() {
        ProgramGraph graph = new ProgramGraph();
        Node[] nodes = new Node[100];

        // Create a chain of 100 nodes
        for (int i = 0; i < 100; i++) {
            nodes[i] = new Node("node" + i);
            graph.addNode(nodes[i]);
            if (i > 0) {
                graph.addEdge(nodes[i-1], nodes[i]);
            }
        }

        ProgramDependenceGraph pdg = new ProgramDependenceGraph(graph);
        Set<Node> slice = pdg.backwardSlice(nodes[99]);

        assertNotNull(slice);
        assertEquals(100, slice.size());

        // Verify all nodes are in the slice
        for (Node node : nodes) {
            assertTrue(slice.contains(node));
        }
    }
}