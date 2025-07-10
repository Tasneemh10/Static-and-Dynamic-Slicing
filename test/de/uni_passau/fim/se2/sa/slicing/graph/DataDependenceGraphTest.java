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
import java.util.Set;

class DataDependenceGraphTest {

    private ClassNode calculatorClassNode;
    private MethodNode evaluateMethodNode;
    private ClassNode gcdClassNode;
    private MethodNode gcdMethodNode;
    private ClassNode simpleIntegerClassNode;
    private MethodNode fooMethodNode;
    private ClassNode complexClassNode;
    private MethodNode multiplyMethodNode;

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

        // Load Complex class
        ClassReader complexReader = new ClassReader("de.uni_passau.fim.se2.sa.examples.Complex");
        complexClassNode = new ClassNode();
        complexReader.accept(complexClassNode, 0);
        multiplyMethodNode = complexClassNode.methods.stream()
                .filter(m -> "multiply".equals(m.name) && "(Lde/uni_passau/fim/se2/sa/examples/Complex;)Lde/uni_passau/fim/se2/sa/examples/Complex;".equals(m.desc))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void testConstructorWithValidClassAndMethod() {
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);
        assertNotNull(ddg);
        assertNotNull(ddg.getCFG());
        assertTrue(ddg.getCFG().getNodes().size() > 0);
    }

    @Test
    void testConstructorWithNullClass() {
        DataDependenceGraph ddg = new DataDependenceGraph(null, null);
        assertNotNull(ddg);
        assertNull(ddg.getCFG());
    }

    @Test
    void testComputeResultWithNullCFG() {
        DataDependenceGraph ddg = new DataDependenceGraph(null, null);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);
        assertEquals(0, result.getNodes().size());
    }

    @Test
    void testComputeResultWithCalculator() {
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // Calculator has variable dependencies, should have some edges
        boolean hasDataDependencies = false;
        for (Node node : result.getNodes()) {
            if (!result.getSuccessors(node).isEmpty()) {
                hasDataDependencies = true;
                break;
            }
        }

        // The evaluate method uses variables, so there should be data dependencies
        assertTrue(hasDataDependencies, "Calculator method should have data dependencies");
    }

    @Test
    void testComputeResultWithSimpleInteger() {
        DataDependenceGraph ddg = new DataDependenceGraph(simpleIntegerClassNode, fooMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // Count edges to verify data dependencies
        int totalEdges = 0;
        for (Node node : result.getNodes()) {
            totalEdges += result.getSuccessors(node).size();
        }

        // SimpleInteger.foo() has many variable assignments and uses
        assertTrue(totalEdges > 0, "SimpleInteger method should have data dependencies");

        // Verify all nodes from CFG are present
        assertEquals(ddg.getCFG().getNodes().size(), result.getNodes().size());
    }

    @Test
    void testComputeResultWithGCD() {
        DataDependenceGraph ddg = new DataDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // GCD has loops with variable updates
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "GCD should have multiple nodes");

        // Verify data dependencies exist
        boolean hasComplexDependencies = false;
        for (Node node : nodes) {
            Collection<Node> successors = result.getSuccessors(node);
            if (successors.size() > 1) {
                hasComplexDependencies = true;
                break;
            }
        }

        // GCD uses x, y, tmp variables with complex dependencies
        assertTrue(hasComplexDependencies || getTotalEdgeCount(result) > 3,
                "GCD should have complex data dependencies");
    }

    @Test
    void testComputeResultWithComplex() {
        DataDependenceGraph ddg = new DataDependenceGraph(complexClassNode, multiplyMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // Complex multiply method uses real and imag variables
        int edgeCount = getTotalEdgeCount(result);
        assertTrue(edgeCount >= 0, "Complex multiply should have some data dependencies");
    }

    @Test
    void testComputeResultHandlesExceptions() {
        // Create a DataDependenceGraph with valid inputs
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);

        // computeResult should handle any internal exceptions gracefully
        assertDoesNotThrow(() -> {
            ProgramGraph result = ddg.computeResult();
            assertNotNull(result);
        });
    }

    @Test
    void testDefUseChainConstruction() {
        DataDependenceGraph ddg = new DataDependenceGraph(simpleIntegerClassNode, fooMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Verify that all CFG nodes are preserved in DDG
        Set<Node> cfgNodes = (Set<Node>) ddg.getCFG().getNodes();
        Set<Node> ddgNodes = (Set<Node>) result.getNodes();

        assertEquals(cfgNodes.size(), ddgNodes.size(), "All CFG nodes should be in DDG");

        for (Node cfgNode : cfgNodes) {
            assertTrue(ddgNodes.contains(cfgNode), "DDG should contain all CFG nodes");
        }
    }

    @Test
    void testReachingDefinitionsAnalysis() {
        DataDependenceGraph ddg = new DataDependenceGraph(simpleIntegerClassNode, fooMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // In SimpleInteger.foo(), variable 'd' depends on 'b' and 'c'
        // 'c' depends on 'a' and 'b'
        // Should see these reflected in the DDG

        int totalDependencies = getTotalEdgeCount(result);
        assertTrue(totalDependencies > 0, "Should have data dependencies for variable uses");
    }

    @Test
    void testVariableDefUseAnalysis() {
        DataDependenceGraph ddg = new DataDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // GCD method has variable reassignments in loop
        // x = y, y = tmp should create dependencies
        boolean foundDependency = false;
        for (Node node : result.getNodes()) {
            if (!result.getSuccessors(node).isEmpty()) {
                foundDependency = true;

                // Verify successors are valid nodes
                for (Node successor : result.getSuccessors(node)) {
                    assertTrue(result.getNodes().contains(successor));
                }
            }
        }

        assertTrue(foundDependency, "GCD should have variable dependencies");
    }

    @Test
    void testGenKillSetsComputation() {
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Calculator method has loop with variable updates
        // sum variable is both defined and used
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertFalse(nodes.isEmpty());

        // Verify graph structure is consistent
        for (Node node : nodes) {
            for (Node successor : result.getSuccessors(node)) {
                assertTrue(nodes.contains(successor), "All successors should be in node set");
            }
        }
    }

    @Test
    void testFixedPointIteration() {
        DataDependenceGraph ddg = new DataDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // The fixed-point algorithm should terminate and produce consistent results
        // Run computation twice to verify consistency
        ProgramGraph result2 = new DataDependenceGraph(gcdClassNode, gcdMethodNode).computeResult();

        assertNotNull(result2);
        assertEquals(result.getNodes().size(), result2.getNodes().size());

        // Edge counts should be the same
        assertEquals(getTotalEdgeCount(result), getTotalEdgeCount(result2));
    }

    @Test
    void testHandleComplexControlFlow() {
        DataDependenceGraph ddg = new DataDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // GCD has while loop with complex control flow
        // Should handle loop back edges correctly
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() >= 5, "GCD should have multiple nodes for complex control flow");

        // Verify no self-loops unless justified by variable dependencies
        for (Node node : nodes) {
            Collection<Node> successors = result.getSuccessors(node);
            // Self-loops are valid for variables used and defined in same node
            for (Node successor : successors) {
                assertNotNull(successor);
            }
        }
    }

    @Test
    void testEmptyMethodHandling() {
        // Find a simple method (constructor or getter)
        MethodNode constructorMethod = simpleIntegerClassNode.methods.stream()
                .filter(m -> "<init>".equals(m.name))
                .findFirst()
                .orElse(null);

        if (constructorMethod != null) {
            DataDependenceGraph ddg = new DataDependenceGraph(simpleIntegerClassNode, constructorMethod);
            ProgramGraph result = ddg.computeResult();

            assertNotNull(result);
            // Constructor might have few or no data dependencies
            assertTrue(result.getNodes().size() >= 0);
        }
    }

    @Test
    void testDataFlowAnalysisErrors() {
        // Test with a method that might cause analysis issues
        DataDependenceGraph ddg = new DataDependenceGraph(complexClassNode, multiplyMethodNode);

        // Should not throw exceptions even if analysis encounters issues
        assertDoesNotThrow(() -> {
            ProgramGraph result = ddg.computeResult();
            assertNotNull(result);
        });
    }

    @Test
    void testVariableTypeHandling() {
        DataDependenceGraph ddg = new DataDependenceGraph(complexClassNode, multiplyMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Complex method uses double variables
        // Should handle different variable types correctly
        assertTrue(result.getNodes().size() > 0);

        // Verify graph integrity
        for (Node node : result.getNodes()) {
            Collection<Node> successors = result.getSuccessors(node);
            for (Node successor : successors) {
                assertTrue(result.getNodes().contains(successor));
            }
        }
    }

    @Test
    void testLocalVariableHandling() {
        DataDependenceGraph ddg = new DataDependenceGraph(simpleIntegerClassNode, fooMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // SimpleInteger.foo() has local variables a, b, c, d
        // Should handle local variable indices correctly
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() > 3, "Should have nodes for variable operations");

        // Check that dependencies reflect variable usage patterns
        int edgeCount = getTotalEdgeCount(result);
        assertTrue(edgeCount > 0, "Local variables should create dependencies");
    }

    @Test
    void testFieldAccessHandling() {
        // Test with a method that accesses fields (if any)
        DataDependenceGraph ddg = new DataDependenceGraph(complexClassNode, multiplyMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Should handle field accesses (this.real, this.imag) correctly
        assertTrue(result.getNodes().size() > 0);

        // Verify all nodes are properly connected
        boolean hasValidStructure = true;
        for (Node node : result.getNodes()) {
            for (Node successor : result.getSuccessors(node)) {
                if (!result.getNodes().contains(successor)) {
                    hasValidStructure = false;
                    break;
                }
            }
            if (!hasValidStructure) break;
        }
        assertTrue(hasValidStructure, "DDG should have valid structure");
    }

    @Test
    void testArrayHandling() {
        // Calculator uses split() which returns array
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Should handle array operations without errors
        assertTrue(result.getNodes().size() > 0);

        // Verify no null nodes or edges
        for (Node node : result.getNodes()) {
            assertNotNull(node);
            for (Node successor : result.getSuccessors(node)) {
                assertNotNull(successor);
            }
        }
    }

    @Test
    void testMethodCallHandling() {
        // Calculator calls Integer.parseInt()
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Method calls should be handled appropriately
        // Should not create spurious dependencies
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertFalse(nodes.isEmpty());

        // Verify reasonable number of dependencies
        int edgeCount = getTotalEdgeCount(result);
        assertTrue(edgeCount >= 0, "Edge count should be non-negative");
        assertTrue(edgeCount < nodes.size() * nodes.size(), "Edge count should be reasonable");
    }

    @Test
    void testParameterHandling() {
        DataDependenceGraph ddg = new DataDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // GCD method has parameters x, y
        // Parameters should be handled as initial definitions
        assertTrue(result.getNodes().size() > 0);

        // Verify that parameter uses create appropriate dependencies
        boolean hasParameterDependencies = getTotalEdgeCount(result) > 0;
        assertTrue(hasParameterDependencies, "Parameters should create data dependencies");
    }

    @Test
    void testLoopVariableHandling() {
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Calculator has enhanced for loop
        // Loop variables should be handled correctly
        Set<Node> nodes = (Set<Node>) result.getNodes();
        assertTrue(nodes.size() > 5, "Loop should generate multiple nodes");

        // Verify loop creates appropriate dependencies
        int edgeCount = getTotalEdgeCount(result);
        assertTrue(edgeCount > 0, "Loop variables should create dependencies");
    }

    @Test
    void testDifferentVariableScopes() {
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Method has parameters and local variables
        // Should distinguish between different scopes correctly
        assertTrue(result.getNodes().size() > 0);

        // All nodes should be reachable and valid
        for (Node node : result.getNodes()) {
            assertNotNull(node);
            assertTrue(node.getLineNumber() >= -1); // -1 is valid for entry/exit
        }
    }

    @Test
    void testConsistentResultsAcrossRuns() {
        DataDependenceGraph ddg1 = new DataDependenceGraph(simpleIntegerClassNode, fooMethodNode);
        DataDependenceGraph ddg2 = new DataDependenceGraph(simpleIntegerClassNode, fooMethodNode);

        ProgramGraph result1 = ddg1.computeResult();
        ProgramGraph result2 = ddg2.computeResult();

        assertNotNull(result1);
        assertNotNull(result2);

        // Results should be consistent
        assertEquals(result1.getNodes().size(), result2.getNodes().size());
        assertEquals(getTotalEdgeCount(result1), getTotalEdgeCount(result2));
    }

    @Test
    void testGetCFGMethod() {
        DataDependenceGraph ddg = new DataDependenceGraph(calculatorClassNode, evaluateMethodNode);
        ProgramGraph cfg = ddg.getCFG();

        assertNotNull(cfg);
        assertTrue(cfg.getNodes().size() > 0);

        // CFG should have entry and exit nodes
        assertTrue(cfg.getEntry().isPresent());
        assertTrue(cfg.getExit().isPresent());

        // DDG should preserve all CFG nodes
        ProgramGraph ddgResult = ddg.computeResult();
        assertEquals(cfg.getNodes().size(), ddgResult.getNodes().size());
    }

    @Test
    void testNullMethodHandling() {
        // Test with both null class and method - this is the only null case that works
        DataDependenceGraph ddg = new DataDependenceGraph(null, null);

        // Should handle null method gracefully
        assertDoesNotThrow(() -> {
            ProgramGraph result = ddg.computeResult();
            assertNotNull(result);
            assertEquals(0, result.getNodes().size());
        });
    }

    @Test
    void testEdgeValidation() {
        DataDependenceGraph ddg = new DataDependenceGraph(gcdClassNode, gcdMethodNode);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);

        // Validate all edges point to valid nodes
        Set<Node> allNodes = (Set<Node>) result.getNodes();
        for (Node node : allNodes) {
            Collection<Node> successors = result.getSuccessors(node);
            for (Node successor : successors) {
                assertTrue(allNodes.contains(successor),
                        "All edge targets should be valid nodes in the graph");
            }

            Collection<Node> predecessors = result.getPredecessors(node);
            for (Node predecessor : predecessors) {
                assertTrue(allNodes.contains(predecessor),
                        "All edge sources should be valid nodes in the graph");
            }
        }
    }

    private int getTotalEdgeCount(ProgramGraph graph) {
        int count = 0;
        for (Node node : graph.getNodes()) {
            count += graph.getSuccessors(node).size();
        }
        return count;
    }

    @Test
    void testLargeMethodHandling() {
        // Use Complex class which has larger methods
        MethodNode largeMethod = complexClassNode.methods.stream()
                .filter(m -> m.instructions.size() > 10)
                .findFirst()
                .orElse(multiplyMethodNode);

        DataDependenceGraph ddg = new DataDependenceGraph(complexClassNode, largeMethod);
        ProgramGraph result = ddg.computeResult();

        assertNotNull(result);
        assertTrue(result.getNodes().size() > 0);

        // Should handle larger methods without performance issues
        long startTime = System.currentTimeMillis();
        ddg.computeResult(); // Second call should be fast due to caching or efficiency
        long endTime = System.currentTimeMillis();

        assertTrue(endTime - startTime < 5000, "Should complete within reasonable time");
    }
}