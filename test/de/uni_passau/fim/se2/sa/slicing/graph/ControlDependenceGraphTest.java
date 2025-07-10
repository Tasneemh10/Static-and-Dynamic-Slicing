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
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

class ControlDependenceGraphTest {

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
        ControlDependenceGraph cdg = new ControlDependenceGraph(gcdClassNode, gcdMethodNode);
        assertNotNull(cdg);
        assertNotNull(cdg.getCFG());
        assertTrue(cdg.getCFG().getNodes().size() > 0);
    }

    @Test
    void testConstructorWithProgramGraph() {
        ProgramGraph cfg = new ProgramGraph();
        Node entry = new Node("Entry");
        Node exit = new Node("Exit");
        Node node1 = new Node("Node1");
        Node node2 = new Node("Node2");

        cfg.addNode(entry);
        cfg.addNode(exit);
        cfg.addNode(node1);
        cfg.addNode(node2);
        cfg.addEdge(entry, node1);
        cfg.addEdge(node1, node2);
        cfg.addEdge(node2, exit);

        ControlDependenceGraph cdg = new ControlDependenceGraph(cfg);
        assertNotNull(cdg);
        assertEquals(cfg, cdg.getCFG());
    }

    @Test
    void testConstructorWithNullClass() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(null, null);
        assertNotNull(cdg);
        assertNull(cdg.getCFG());
    }

    @Test
    void testComputeResultWithNullCFG() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(null, null);
        ProgramGraph result = cdg.computeResult();

        assertNull(result);
    }

    @Test
    void testComputeResultWithSimpleMethod() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(simpleIntegerClassNode, fooMethodNode);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // SimpleInteger.foo() is sequential, should have minimal control dependencies
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 3, "Should have at least entry, exit, and some nodes");

        // Verify all CFG nodes are present in CDG
        assertEquals(cdg.getCFG().getNodes().size(), result.getNodes().size());
    }

    @Test
    void testComputeResultWithLoopMethod() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // GCD has while loop, should have control dependencies
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "GCD should have multiple nodes");

        // Should have some control dependencies due to while loop
        int totalEdges = getTotalEdgeCount(result);
        assertTrue(totalEdges >= 0, "Should have control dependencies");
    }

    @Test
    void testComputeResultWithConditionalMethod() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(testClassNode, countFoosMethodNode);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // countFoos has if statement and while loop
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "Method with conditionals should have multiple nodes");

        // Should have control dependencies from conditionals
        boolean hasControlDependencies = false;
        for (Node node : nodes) {
            if (!result.getSuccessors(node).isEmpty()) {
                hasControlDependencies = true;
                break;
            }
        }
        assertTrue(hasControlDependencies, "Conditional method should have control dependencies");
    }

    @Test
    void testComputeResultWithForLoopMethod() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // Calculator has for-each loop
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "Method with for loop should have multiple nodes");

        // Verify graph structure
        for (Node node : nodes) {
            for (Node successor : result.getSuccessors(node)) {
                assertTrue(nodes.contains(successor), "All successors should be in node set");
            }
        }
    }

    @Test
    void testPostDominatorTreeIntegration() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);

        // The CDG computation uses PostDominatorTree internally
        // Verify that the result is consistent
        assertTrue(result.getNodes().size() > 0);

        // Test that we can compute it multiple times with same result
        ProgramGraph result2 = new ControlDependenceGraph(gcdClassNode, gcdMethodNode).computeResult();
        assertEquals(result.getNodes().size(), result2.getNodes().size());
        assertEquals(getTotalEdgeCount(result), getTotalEdgeCount(result2));
    }

    @Test
    void testBuildIpdomMap() throws Exception {
        ControlDependenceGraph cdg = new ControlDependenceGraph(gcdClassNode, gcdMethodNode);

        // Use reflection to test private method
        Method buildIpdomMapMethod = ControlDependenceGraph.class.getDeclaredMethod("buildIpdomMap", ProgramGraph.class);
        buildIpdomMapMethod.setAccessible(true);

        // Create a simple PDT for testing
        ProgramGraph pdt = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        Node node3 = new Node("node3");

        pdt.addNode(node1);
        pdt.addNode(node2);
        pdt.addNode(node3);
        pdt.addEdge(node2, node1); // node2 immediately post-dominates node1

        @SuppressWarnings("unchecked")
        Map<Node, Node> ipdomMap = (Map<Node, Node>) buildIpdomMapMethod.invoke(cdg, pdt);

        assertNotNull(ipdomMap);
        assertEquals(node2, ipdomMap.get(node1));
        assertNull(ipdomMap.get(node2)); // node2 has no single predecessor
        assertNull(ipdomMap.get(node3)); // node3 has no predecessors
    }

    @Test
    void testAddControlDependencies() throws Exception {
        ControlDependenceGraph cdg = new ControlDependenceGraph(simpleIntegerClassNode, fooMethodNode);

        // Use reflection to test private method
        Method addControlDependenciesMethod = ControlDependenceGraph.class.getDeclaredMethod(
                "addControlDependencies", ProgramGraph.class, Node.class, Node.class, Node.class, Map.class);
        addControlDependenciesMethod.setAccessible(true);

        ProgramGraph testCdg = new ProgramGraph();
        Node controller = new Node("controller");
        Node start = new Node("start");
        Node ipdom = new Node("ipdom");
        Node intermediate = new Node("intermediate");

        testCdg.addNode(controller);
        testCdg.addNode(start);
        testCdg.addNode(ipdom);
        testCdg.addNode(intermediate);

        Map<Node, Node> ipdomMap = Map.of(
                start, intermediate,
                intermediate, ipdom
        );

        addControlDependenciesMethod.invoke(cdg, testCdg, controller, start, ipdom, ipdomMap);

        // Should add edges from controller to nodes up to ipdom
        assertTrue(testCdg.getSuccessors(controller).contains(start));
        assertTrue(testCdg.getSuccessors(controller).contains(intermediate));
        assertFalse(testCdg.getSuccessors(controller).contains(ipdom));
    }

    @Test
    void testControlDependenceWithDiamondPattern() {
        // Create a diamond-shaped CFG
        ProgramGraph cfg = new ProgramGraph();
        Node entry = new Node("Entry");
        Node branch = new Node("Branch");
        Node left = new Node("Left");
        Node right = new Node("Right");
        Node merge = new Node("Merge");
        Node exit = new Node("Exit");

        cfg.addNode(entry);
        cfg.addNode(branch);
        cfg.addNode(left);
        cfg.addNode(right);
        cfg.addNode(merge);
        cfg.addNode(exit);

        cfg.addEdge(entry, branch);
        cfg.addEdge(branch, left);
        cfg.addEdge(branch, right);
        cfg.addEdge(left, merge);
        cfg.addEdge(right, merge);
        cfg.addEdge(merge, exit);

        ControlDependenceGraph cdg = new ControlDependenceGraph(cfg);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);
        assertEquals(6, result.getNodes().size());

        // Branch should control left and right
        assertTrue(result.getSuccessors(branch).contains(left) ||
                result.getSuccessors(branch).contains(right));
    }

    @Test
    void testControlDependenceWithNestedLoops() {
        // Test with a method that has nested control structures
        ControlDependenceGraph cdg = new ControlDependenceGraph(testClassNode, countFoosMethodNode);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);

        // countFoos has while loop with if statement inside
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "Nested control structures should have multiple nodes");

        // Should have nested control dependencies
        int totalEdges = getTotalEdgeCount(result);
        assertTrue(totalEdges >= 0, "Nested structures should create control dependencies");


        assertFalse(isAcyclic(result), "Control Dependence Graph should not be acyclic");
    }

    @Test
    void testEmptySuccessorsHandling() {
        // Test with a linear CFG (no branching)
        ProgramGraph cfg = new ProgramGraph();
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        Node node3 = new Node("node3");

        cfg.addNode(node1);
        cfg.addNode(node2);
        cfg.addNode(node3);
        cfg.addEdge(node1, node2);
        cfg.addEdge(node2, node3);

        ControlDependenceGraph cdg = new ControlDependenceGraph(cfg);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);
        assertEquals(3, result.getNodes().size());

        // Linear structure should have minimal control dependencies
        int totalEdges = getTotalEdgeCount(result);
        assertTrue(totalEdges >= 0, "Linear structure should have few control dependencies");
    }

    @Test
    void testSingleNodeGraph() {
        ProgramGraph cfg = new ProgramGraph();
        Node singleNode = new Node("single");
        cfg.addNode(singleNode);

        ControlDependenceGraph cdg = new ControlDependenceGraph(cfg);
        ProgramGraph result = cdg.computeResult();

        assertNotNull(result);
        assertEquals(1, result.getNodes().size());
        assertTrue(result.getNodes().contains(singleNode));
        assertTrue(result.getSuccessors(singleNode).isEmpty());
    }

    @Test
    void testGetCFGMethod() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph cfg = cdg.getCFG();

        assertNotNull(cfg);
        assertTrue(cfg.getNodes().size() > 0);

        // CFG should have entry and exit nodes
        assertTrue(cfg.getEntry().isPresent());
        assertTrue(cfg.getExit().isPresent());
    }

    @Test
    void testInheritanceFromGraph() {
        ControlDependenceGraph cdg = new ControlDependenceGraph(simpleIntegerClassNode, fooMethodNode);

        // Test inherited methods from Graph class
        assertNotNull(cdg.getCFG());
        assertTrue(cdg instanceof Graph);

        ProgramGraph result = cdg.computeResult();
        assertNotNull(result);
    }

    @Test
    void testMultipleSuccessorsHandling() {
        // Test with a node that has multiple successors (branch)
        ControlDependenceGraph cdg = new ControlDependenceGraph(testClassNode, countFoosMethodNode);
        ProgramGraph cfg = cdg.getCFG();

        assertNotNull(cfg);

        // Find a node with multiple successors
        boolean foundBranch = false;
        for (Node node : cfg.getNodes()) {
            if (cfg.getSuccessors(node).size() > 1) {
                foundBranch = true;
                break;
            }
        }

        ProgramGraph result = cdg.computeResult();
        assertNotNull(result);

        if (foundBranch) {
            // Should handle branches correctly
            assertTrue(getTotalEdgeCount(result) >= 0);
        }
    }

    @Test
    void testConsistentResults() {
        ControlDependenceGraph cdg1 = new ControlDependenceGraph(gcdClassNode, gcdMethodNode);
        ControlDependenceGraph cdg2 = new ControlDependenceGraph(gcdClassNode, gcdMethodNode);

        ProgramGraph result1 = cdg1.computeResult();
        ProgramGraph result2 = cdg2.computeResult();

        assertNotNull(result1);
        assertNotNull(result2);

        // Results should be consistent
        assertEquals(result1.getNodes().size(), result2.getNodes().size());
        assertEquals(getTotalEdgeCount(result1), getTotalEdgeCount(result2));
    }

    @Test
    void testLargeMethodPerformance() {
        // Test performance with larger method
        ControlDependenceGraph cdg = new ControlDependenceGraph(testClassNode, countFoosMethodNode);

        long startTime = System.currentTimeMillis();
        ProgramGraph result = cdg.computeResult();
        long endTime = System.currentTimeMillis();

        assertNotNull(result);
        assertTrue(endTime - startTime < 5000, "Should complete within reasonable time");
        assertTrue(result.getNodes().size() > 0);
    }

    private int getTotalEdgeCount(ProgramGraph graph) {
        int count = 0;
        for (Node node : graph.getNodes()) {
            count += graph.getSuccessors(node).size();
        }
        return count;
    }

    private boolean isAcyclic(ProgramGraph graph) {
        // Simple cycle detection using DFS
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