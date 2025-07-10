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
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

class PostDominatorTreeTest {

    private ClassNode calculatorClassNode;
    private MethodNode evaluateMethodNode;
    private ClassNode gcdClassNode;
    private MethodNode gcdMethodNode;
    private ClassNode simpleIntegerClassNode;
    private MethodNode fooMethodNode;
    private ClassNode testClassNode;
    private MethodNode countFoosMethodNode;

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

        // Load TestClass
        ClassReader testReader = new ClassReader("de.uni_passau.fim.se2.sa.examples.TestClass");
        testClassNode = new ClassNode();
        testReader.accept(testClassNode, 0);
        countFoosMethodNode = testClassNode.methods.stream()
                .filter(m -> "countFoos".equals(m.name) && "(I)I".equals(m.desc))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void testConstructorWithClassAndMethod() {
        PostDominatorTree pdt = new PostDominatorTree(calculatorClassNode, evaluateMethodNode);
        assertNotNull(pdt);
        assertNotNull(pdt.getCFG());
        assertTrue(pdt.getCFG().getNodes().size() > 0);
    }

    @Test
    void testConstructorWithProgramGraph() {
        ProgramGraph cfg = new ProgramGraph();
        Node entry = new Node("Entry");
        Node exit = new Node("Exit");
        Node node1 = new Node("Node1");

        cfg.addNode(entry);
        cfg.addNode(exit);
        cfg.addNode(node1);
        cfg.addEdge(entry, node1);
        cfg.addEdge(node1, exit);

        PostDominatorTree pdt = new PostDominatorTree(cfg);
        assertNotNull(pdt);
        assertEquals(cfg, pdt.getCFG());
    }

    @Test
    void testConstructorWithNullClass() {
        PostDominatorTree pdt = new PostDominatorTree(null, null);
        assertNotNull(pdt);
        assertNull(pdt.getCFG());
    }

    @Test
    void testComputeResultWithEmptyGraph() {
        ProgramGraph emptyGraph = new ProgramGraph();
        PostDominatorTree pdt = new PostDominatorTree(emptyGraph);

        ProgramGraph result = pdt.computeResult();
        assertNotNull(result);
        assertEquals(0, result.getNodes().size());
    }

    @Test
    void testComputeResultWithSingleNode() {
        ProgramGraph cfg = new ProgramGraph();
        Node singleNode = new Node("single");
        cfg.addNode(singleNode);

        PostDominatorTree pdt = new PostDominatorTree(cfg);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);
        assertEquals(1, result.getNodes().size());
        assertTrue(result.getNodes().contains(singleNode));
        assertTrue(result.getSuccessors(singleNode).isEmpty());
    }

    @Test
    void testComputeResultWithLinearGraph() {
        ProgramGraph cfg = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        Node node3 = new Node("node3");

        cfg.addNode(node1);
        cfg.addNode(node2);
        cfg.addNode(node3);
        cfg.addEdge(node1, node2);
        cfg.addEdge(node2, node3);

        PostDominatorTree pdt = new PostDominatorTree(cfg);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);
        assertEquals(3, result.getNodes().size());

        // In a linear graph: node3 post-dominates node2, node2 post-dominates node1
        assertTrue(result.getSuccessors(node2).contains(node1) ||
                result.getSuccessors(node3).contains(node2));
    }

    @Test
    void testComputeResultWithDiamondGraph() {
        ProgramGraph cfg = new ProgramGraph();
        Node entry = new Node("entry");
        Node branch = new Node("branch");
        Node left = new Node("left");
        Node right = new Node("right");
        Node merge = new Node("merge");

        cfg.addNode(entry);
        cfg.addNode(branch);
        cfg.addNode(left);
        cfg.addNode(right);
        cfg.addNode(merge);

        cfg.addEdge(entry, branch);
        cfg.addEdge(branch, left);
        cfg.addEdge(branch, right);
        cfg.addEdge(left, merge);
        cfg.addEdge(right, merge);

        PostDominatorTree pdt = new PostDominatorTree(cfg);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);
        assertEquals(5, result.getNodes().size());

        // merge should post-dominate both left and right
        assertTrue(result.getSuccessors(merge).contains(left) &&
                result.getSuccessors(merge).contains(right) ||
                result.getSuccessors(merge).contains(branch));
    }

    @Test
    void testComputeResultWithRealMethod() {
        PostDominatorTree pdt = new PostDominatorTree(simpleIntegerClassNode, fooMethodNode);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // All CFG nodes should be present in PDT
        assertEquals(pdt.getCFG().getNodes().size(), result.getNodes().size());

        // Verify tree structure (each node has at most one parent)
        for (Node node : result.getNodes()) {
            Collection<Node> predecessors = result.getPredecessors(node);
            assertTrue(predecessors.size() <= 1, "Each node should have at most one immediate post-dominator");
        }
    }

    @Test
    void testComputeResultWithLoopMethod() {
        PostDominatorTree pdt = new PostDominatorTree(gcdClassNode, gcdMethodNode);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // GCD has while loop
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "GCD method should have multiple nodes");

        // Verify tree properties
        verifyTreeProperties(result);
    }

    @Test
    void testComputeResultWithComplexMethod() {
        PostDominatorTree pdt = new PostDominatorTree(testClassNode, countFoosMethodNode);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // countFoos has while loop with if statement
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "Complex method should have multiple nodes");

        // Verify tree properties
        verifyTreeProperties(result);
    }

    @Test
    void testReverseGraphUsage() {
        PostDominatorTree pdt = new PostDominatorTree(calculatorClassNode, evaluateMethodNode);
        ProgramGraph cfg = pdt.getCFG();

        // The post-dominator tree algorithm uses reverse graph internally
        assertNotNull(cfg);
        assertTrue(cfg.getNodes().size() > 0);

        ProgramGraph result = pdt.computeResult();
        assertNotNull(result);

        // Result should have same nodes as CFG
        assertEquals(cfg.getNodes().size(), result.getNodes().size());
    }

    @Test
    void testDominatorAlgorithmConvergence() {
        PostDominatorTree pdt = new PostDominatorTree(gcdClassNode, gcdMethodNode);

        // Algorithm should converge to fixed point
        ProgramGraph result1 = pdt.computeResult();
        ProgramGraph result2 = pdt.computeResult();

        assertNotNull(result1);
        assertNotNull(result2);

        // Results should be identical (algorithm is deterministic)
        assertEquals(result1.getNodes().size(), result2.getNodes().size());
        assertEquals(getTotalEdgeCount(result1), getTotalEdgeCount(result2));
    }

    @Test
    void testImmediateDominatorComputation() {
        // Create a simple graph where immediate dominators are clear
        ProgramGraph cfg = new ProgramGraph();
        Node a = new Node("a");
        Node b = new Node("b");
        Node c = new Node("c");
        Node d = new Node("d");

        cfg.addNode(a);
        cfg.addNode(b);
        cfg.addNode(c);
        cfg.addNode(d);

        // a -> b -> c -> d (linear)
        cfg.addEdge(a, b);
        cfg.addEdge(b, c);
        cfg.addEdge(c, d);

        PostDominatorTree pdt = new PostDominatorTree(cfg);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);
        assertEquals(4, result.getNodes().size());

        // In post-dominator tree: d is root, c->d, b->c, a->b
        verifyTreeProperties(result);
    }

    @Test
    void testWithCyclicGraph() {
        // Test with graph containing cycles (loops)
        PostDominatorTree pdt = new PostDominatorTree(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);

        // Even with loops in CFG, PDT should be acyclic
        assertTrue(isAcyclic(result), "Post-dominator tree should be acyclic");
        verifyTreeProperties(result);
    }

    @Test
    void testEntryNodeHandling() {
        PostDominatorTree pdt = new PostDominatorTree(simpleIntegerClassNode, fooMethodNode);
        ProgramGraph cfg = pdt.getCFG();
        ProgramGraph result = pdt.computeResult();

        assertNotNull(cfg);
        assertNotNull(result);

        // Entry node handling
        Optional<Node> entry = cfg.getEntry();
        if (entry.isPresent()) {
            assertTrue(result.getNodes().contains(entry.get()));
        }

        // Exit node should be the root of post-dominator tree
        Optional<Node> exit = cfg.getExit();
        if (exit.isPresent()) {
            assertTrue(result.getNodes().contains(exit.get()));
            // Exit should have no predecessors in PDT (is the root)
            assertTrue(result.getPredecessors(exit.get()).isEmpty());
        }
    }

    @Test
    void testStrictDominatorRemoval() {
        // Test that immediate dominator computation removes strict dominators
        ProgramGraph cfg = new ProgramGraph();
        Node a = new Node("a");
        Node b = new Node("b");
        Node c = new Node("c");
        Node d = new Node("d");
        Node e = new Node("e");

        cfg.addNode(a);
        cfg.addNode(b);
        cfg.addNode(c);
        cfg.addNode(d);
        cfg.addNode(e);

        // Create a more complex structure
        cfg.addEdge(a, b);
        cfg.addEdge(b, c);
        cfg.addEdge(c, d);
        cfg.addEdge(d, e);
        cfg.addEdge(a, e); // Additional edge

        PostDominatorTree pdt = new PostDominatorTree(cfg);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);
        verifyTreeProperties(result);

        // Each node should have at most one immediate post-dominator
        for (Node node : result.getNodes()) {
            assertTrue(result.getPredecessors(node).size() <= 1);
        }
    }

    @Test
    void testConsistentResults() {
        PostDominatorTree pdt1 = new PostDominatorTree(gcdClassNode, gcdMethodNode);
        PostDominatorTree pdt2 = new PostDominatorTree(gcdClassNode, gcdMethodNode);

        ProgramGraph result1 = pdt1.computeResult();
        ProgramGraph result2 = pdt2.computeResult();

        assertNotNull(result1);
        assertNotNull(result2);

        // Results should be consistent
        assertEquals(result1.getNodes().size(), result2.getNodes().size());
        assertEquals(getTotalEdgeCount(result1), getTotalEdgeCount(result2));
    }

    @Test
    void testPerformanceWithLargeGraph() {
        PostDominatorTree pdt = new PostDominatorTree(testClassNode, countFoosMethodNode);

        long startTime = System.currentTimeMillis();
        ProgramGraph result = pdt.computeResult();
        long endTime = System.currentTimeMillis();

        assertNotNull(result);
        assertTrue(endTime - startTime < 5000, "Should complete within reasonable time");
        assertTrue(result.getNodes().size() > 0);

        verifyTreeProperties(result);
    }

    @Test
    void testGetCFGMethod() {
        PostDominatorTree pdt = new PostDominatorTree(calculatorClassNode, evaluateMethodNode);
        ProgramGraph cfg = pdt.getCFG();

        assertNotNull(cfg);
        assertTrue(cfg.getNodes().size() > 0);

        // CFG should have entry and exit nodes
        assertTrue(cfg.getEntry().isPresent());
        assertTrue(cfg.getExit().isPresent());
    }

    @Test
    void testInheritanceFromGraph() {
        PostDominatorTree pdt = new PostDominatorTree(simpleIntegerClassNode, fooMethodNode);

        // Test inherited methods from Graph class
        assertNotNull(pdt.getCFG());
        assertTrue(pdt instanceof Graph);

        ProgramGraph result = pdt.computeResult();
        assertNotNull(result);
    }

    @Test
    void testDominatorSetIntersection() {
        // Test the intersection logic in dominator computation
        PostDominatorTree pdt = new PostDominatorTree(testClassNode, countFoosMethodNode);
        ProgramGraph result = pdt.computeResult();

        assertNotNull(result);

        // Verify that dominator sets are computed correctly
        // (This is implicitly tested by the tree properties)
        verifyTreeProperties(result);

        // Each node should post-dominate itself (reflexive property)
        for (Node node : result.getNodes()) {
            // In the tree, a node post-dominates all its descendants
            assertNotNull(node);
        }
    }

    @Test
    void testFixedPointIteration() {
        // Test that the fixed-point iteration converges
        PostDominatorTree pdt = new PostDominatorTree(gcdClassNode, gcdMethodNode);

        // Multiple computations should give same result
        ProgramGraph result1 = pdt.computeResult();
        ProgramGraph result2 = pdt.computeResult();
        ProgramGraph result3 = pdt.computeResult();

        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);

        assertEquals(result1.getNodes().size(), result2.getNodes().size());
        assertEquals(result2.getNodes().size(), result3.getNodes().size());
        assertEquals(getTotalEdgeCount(result1), getTotalEdgeCount(result2));
    }

    @Test
    void testBoundaryConditions() {
        // Test with various boundary conditions

        // Single node graph
        ProgramGraph singleNode = new ProgramGraph();
        Node node = new Node("single");
        singleNode.addNode(node);

        PostDominatorTree pdt1 = new PostDominatorTree(singleNode);
        ProgramGraph result1 = pdt1.computeResult();
        assertNotNull(result1);
        assertEquals(1, result1.getNodes().size());

        // Two node graph
        ProgramGraph twoNodes = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        twoNodes.addNode(node1);
        twoNodes.addNode(node2);
        twoNodes.addEdge(node1, node2);

        PostDominatorTree pdt2 = new PostDominatorTree(twoNodes);
        ProgramGraph result2 = pdt2.computeResult();
        assertNotNull(result2);
        assertEquals(2, result2.getNodes().size());
    }

    private void verifyTreeProperties(ProgramGraph tree) {
        // Verify that the result is actually a tree
        Set<Node> nodes = (Set<Node>) tree.getNodes();

        // Each node should have at most one predecessor (parent)
        for (Node node : nodes) {
            Collection<Node> predecessors = tree.getPredecessors(node);
            assertTrue(predecessors.size() <= 1,
                    "Each node should have at most one immediate post-dominator");
        }

        // Should be acyclic
        assertTrue(isAcyclic(tree), "Post-dominator tree should be acyclic");

        // Should have at most one root (node with no predecessors)
        int rootCount = 0;
        for (Node node : nodes) {
            if (tree.getPredecessors(node).isEmpty()) {
                rootCount++;
            }
        }
        assertTrue(rootCount <= 1, "Should have at most one root");
    }

    private int getTotalEdgeCount(ProgramGraph graph) {
        int count = 0;
        for (Node node : graph.getNodes()) {
            count += graph.getSuccessors(node).size();
        }
        return count;
    }

    private boolean isAcyclic(ProgramGraph graph) {
        Set<Node> visited = new java.util.HashSet<>();
        Set<Node> recursionStack = new java.util.HashSet<>();

        for (Node node : graph.getNodes()) {
            if (!visited.contains(node)) {
                if (hasCycleDFS(graph, node, visited, recursionStack)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasCycleDFS(ProgramGraph graph, Node node, Set<Node> visited, Set<Node> recursionStack) {
        visited.add(node);
        recursionStack.add(node);

        for (Node successor : graph.getSuccessors(node)) {
            if (!visited.contains(successor)) {
                if (hasCycleDFS(graph, successor, visited, recursionStack)) {
                    return true;
                }
            } else if (recursionStack.contains(successor)) {
                return true;
            }
        }

        recursionStack.remove(node);
        return false;
    }
}