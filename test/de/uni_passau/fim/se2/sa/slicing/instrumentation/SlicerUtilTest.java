package de.uni_passau.fim.se2.sa.slicing.instrumentation;

import de.uni_passau.fim.se2.sa.slicing.SlicerUtil;
import de.uni_passau.fim.se2.sa.slicing.cfg.Node;
import de.uni_passau.fim.se2.sa.slicing.cfg.ProgramGraph;
import de.uni_passau.fim.se2.sa.slicing.coverage.CoverageTracker;
import de.uni_passau.fim.se2.sa.slicing.graph.ProgramDependenceGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlicerUtilTest {

    private ByteArrayOutputStream errorStream;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        // Capture System.err for testing error output
        errorStream = new ByteArrayOutputStream();
        originalErr = System.err;
        System.setErr(new PrintStream(errorStream));
    }

    @Test
    void testExecuteTest_ValidClassAndTest() {
        // This test verifies that executeTest runs without throwing exceptions
        // when given valid parameters
        assertDoesNotThrow(() -> {
            SlicerUtil.executeTest("ValidClass", "testMethod");
        });
    }

    @Test
    void testExecuteTest_NullClassName() {
        // Test behavior with null className
        assertDoesNotThrow(() -> {
            SlicerUtil.executeTest(null, "testMethod");
        });

        // Verify error was logged
        String errorOutput = errorStream.toString();
        assertTrue(errorOutput.contains("Test execution failed"));
    }

    @Test
    void testExecuteTest_NullTestCase() {
        // Test behavior with null test case
        assertDoesNotThrow(() -> {
            SlicerUtil.executeTest("ValidClass", null);
        });

        String errorOutput = errorStream.toString();
        assertTrue(errorOutput.contains("Test execution failed"));
    }

    @Test
    void testExecuteTest_EmptyClassName() {
        // Test behavior with empty className
        assertDoesNotThrow(() -> {
            SlicerUtil.executeTest("", "testMethod");
        });

        String errorOutput = errorStream.toString();
        assertTrue(errorOutput.contains("Test execution failed"));
    }

    @Test
    void testExecuteTest_EmptyTestCase() {
        // Test behavior with empty test case
        assertDoesNotThrow(() -> {
            SlicerUtil.executeTest("ValidClass", "");
        });

        String errorOutput = errorStream.toString();
        assertTrue(errorOutput.contains("Test execution failed"));
    }

    @Test
    void testExecuteTest_NonExistentClass() {
        // Test with a class that doesn't exist
        assertDoesNotThrow(() -> {
            SlicerUtil.executeTest("NonExistentClass", "testMethod");
        });

        String errorOutput = errorStream.toString();
        assertTrue(errorOutput.contains("Test execution failed"));
        assertTrue(errorOutput.contains("NonExistentClass"));
        assertTrue(errorOutput.contains("testMethod"));
    }

    @Test
    void testSimplify_EmptyCoverage() {
        // Create mock objects
        ProgramDependenceGraph mockPDG = mock(ProgramDependenceGraph.class);
        ProgramGraph mockFullGraph = mock(ProgramGraph.class);
        ProgramDependenceGraph result;

        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            // Setup mocks
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(Collections.emptySet());

            // Execute test
            result = SlicerUtil.simplify(mockPDG);

            // Verify that the original PDG is returned when no coverage
            assertEquals(mockPDG, result);
            verify(mockPDG, never()).computeResult();
        }
    }

    @Test
    void testSimplify_WithCoverage() {
        // Create mock objects
        ProgramDependenceGraph mockPDG = mock(ProgramDependenceGraph.class);
        ProgramGraph mockFullGraph = mock(ProgramGraph.class);

        // Create test nodes
        Node node1 = mock(Node.class);
        Node node2 = mock(Node.class);
        Node node3 = mock(Node.class);

        when(node1.getLineNumber()).thenReturn(10);
        when(node2.getLineNumber()).thenReturn(20);
        when(node3.getLineNumber()).thenReturn(30);

        List<Node> allNodes = Arrays.asList(node1, node2, node3);
        Set<Integer> coveredLines = Set.of(10, 20);

        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            // Setup mocks
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(coveredLines);
            when(mockPDG.computeResult()).thenReturn(mockFullGraph);
            when(mockFullGraph.getNodes()).thenReturn(allNodes);
            when(mockFullGraph.getSuccessors(node1)).thenReturn(Arrays.asList(node2));
            when(mockFullGraph.getSuccessors(node2)).thenReturn(Collections.emptyList());

            // Execute test
            ProgramDependenceGraph result = SlicerUtil.simplify(mockPDG);

            // Verify result is not the same object as input
            assertNotNull(result);
            assertNotEquals(mockPDG, result);

            // Verify computeResult was called
            verify(mockPDG).computeResult();
        }
    }

    @Test
    void testSimplify_NodeWithZeroLineNumber() {
        // Test nodes with line number 0 (should be excluded)
        ProgramDependenceGraph mockPDG = mock(ProgramDependenceGraph.class);
        ProgramGraph mockFullGraph = mock(ProgramGraph.class);

        Node nodeZeroLine = mock(Node.class);
        Node nodeValidLine = mock(Node.class);

        when(nodeZeroLine.getLineNumber()).thenReturn(0);
        when(nodeValidLine.getLineNumber()).thenReturn(10);

        List<Node> allNodes = Arrays.asList(nodeZeroLine, nodeValidLine);
        Set<Integer> coveredLines = Set.of(0, 10);

        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(coveredLines);
            when(mockPDG.computeResult()).thenReturn(mockFullGraph);
            when(mockFullGraph.getNodes()).thenReturn(allNodes);
            when(mockFullGraph.getSuccessors(any())).thenReturn(Collections.emptyList());

            ProgramDependenceGraph result = SlicerUtil.simplify(mockPDG);

            assertNotNull(result);
            verify(mockPDG).computeResult();
        }
    }

    @Test
    void testSimplify_NodeWithNegativeLineNumber() {
        // Test nodes with negative line numbers (should be excluded)
        ProgramDependenceGraph mockPDG = mock(ProgramDependenceGraph.class);
        ProgramGraph mockFullGraph = mock(ProgramGraph.class);

        Node nodeNegativeLine = mock(Node.class);
        Node nodeValidLine = mock(Node.class);

        when(nodeNegativeLine.getLineNumber()).thenReturn(-5);
        when(nodeValidLine.getLineNumber()).thenReturn(15);

        List<Node> allNodes = Arrays.asList(nodeNegativeLine, nodeValidLine);
        Set<Integer> coveredLines = Set.of(-5, 15);

        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(coveredLines);
            when(mockPDG.computeResult()).thenReturn(mockFullGraph);
            when(mockFullGraph.getNodes()).thenReturn(allNodes);
            when(mockFullGraph.getSuccessors(any())).thenReturn(Collections.emptyList());

            ProgramDependenceGraph result = SlicerUtil.simplify(mockPDG);

            assertNotNull(result);
            verify(mockPDG).computeResult();
        }
    }

    @Test
    void testSimplify_ComplexGraphWithEdges() {
        // Test a more complex scenario with multiple nodes and edges
        ProgramDependenceGraph mockPDG = mock(ProgramDependenceGraph.class);
        ProgramGraph mockFullGraph = mock(ProgramGraph.class);

        Node node1 = mock(Node.class);
        Node node2 = mock(Node.class);
        Node node3 = mock(Node.class);
        Node node4 = mock(Node.class);

        when(node1.getLineNumber()).thenReturn(10);
        when(node2.getLineNumber()).thenReturn(20);
        when(node3.getLineNumber()).thenReturn(30);
        when(node4.getLineNumber()).thenReturn(40);

        List<Node> allNodes = Arrays.asList(node1, node2, node3, node4);
        Set<Integer> coveredLines = Set.of(10, 30); // Only lines 10 and 30 are covered

        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(coveredLines);
            when(mockPDG.computeResult()).thenReturn(mockFullGraph);
            when(mockFullGraph.getNodes()).thenReturn(allNodes);

            // Setup edges: only for covered nodes (node1 and node3)
            when(mockFullGraph.getSuccessors(node1)).thenReturn(Arrays.asList(node2, node3));
            when(mockFullGraph.getSuccessors(node3)).thenReturn(Collections.emptyList());

            ProgramDependenceGraph result = SlicerUtil.simplify(mockPDG);

            assertNotNull(result);
            verify(mockPDG).computeResult();
        }
    }

    @Test
    void testSimplify_NullPDG() {
        // Test with null input - the method actually handles null gracefully
        // by calling getVisitedLines() first, so it doesn't immediately throw NPE
        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(Set.of(10));

            // This will throw NPE when trying to call computeResult() on null
            assertThrows(NullPointerException.class, () -> {
                SlicerUtil.simplify(null);
            });
        }
    }

    @Test
    void testSimplify_SingleNodeCovered() {
        // Test with only one node covered
        ProgramDependenceGraph mockPDG = mock(ProgramDependenceGraph.class);
        ProgramGraph mockFullGraph = mock(ProgramGraph.class);

        Node singleNode = mock(Node.class);
        when(singleNode.getLineNumber()).thenReturn(5);

        List<Node> allNodes = Arrays.asList(singleNode);
        Set<Integer> coveredLines = Set.of(5);

        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(coveredLines);
            when(mockPDG.computeResult()).thenReturn(mockFullGraph);
            when(mockFullGraph.getNodes()).thenReturn(allNodes);
            when(mockFullGraph.getSuccessors(singleNode)).thenReturn(Collections.emptyList());

            ProgramDependenceGraph result = SlicerUtil.simplify(mockPDG);

            assertNotNull(result);
            verify(mockPDG).computeResult();
        }
    }

    @Test
    void testSimplify_NoCoveredNodes() {
        // Test where coverage exists but no nodes match the covered lines
        ProgramDependenceGraph mockPDG = mock(ProgramDependenceGraph.class);
        ProgramGraph mockFullGraph = mock(ProgramGraph.class);

        Node node1 = mock(Node.class);
        when(node1.getLineNumber()).thenReturn(10);

        List<Node> allNodes = Arrays.asList(node1);
        Set<Integer> coveredLines = Set.of(20); // Different line than the node

        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(coveredLines);
            when(mockPDG.computeResult()).thenReturn(mockFullGraph);
            when(mockFullGraph.getNodes()).thenReturn(allNodes);

            ProgramDependenceGraph result = SlicerUtil.simplify(mockPDG);

            assertNotNull(result);
            verify(mockPDG).computeResult();
        }
    }

    @Test
    void testExecuteTest_SpecialCharactersInNames() {
        // Test with special characters in class and test names
        assertDoesNotThrow(() -> {
            SlicerUtil.executeTest("Class$With$Special", "test_with_underscores");
        });

        String errorOutput = errorStream.toString();
        assertTrue(errorOutput.contains("Test execution failed"));
    }

    @Test
    void testExecuteTest_VeryLongNames() {
        // Test with very long class and test names
        String longClassName = "VeryLongClassName".repeat(10);
        String longTestName = "veryLongTestMethodName".repeat(10);

        assertDoesNotThrow(() -> {
            SlicerUtil.executeTest(longClassName, longTestName);
        });

        String errorOutput = errorStream.toString();
        assertTrue(errorOutput.contains("Test execution failed"));
    }

    @Test
    void testSimplify_LargeLineNumbers() {
        // Test with very large line numbers
        ProgramDependenceGraph mockPDG = mock(ProgramDependenceGraph.class);
        ProgramGraph mockFullGraph = mock(ProgramGraph.class);

        Node node = mock(Node.class);
        when(node.getLineNumber()).thenReturn(Integer.MAX_VALUE);

        List<Node> allNodes = Arrays.asList(node);
        Set<Integer> coveredLines = Set.of(Integer.MAX_VALUE);

        try (MockedStatic<CoverageTracker> mockedTracker = mockStatic(CoverageTracker.class)) {
            mockedTracker.when(CoverageTracker::getVisitedLines).thenReturn(coveredLines);
            when(mockPDG.computeResult()).thenReturn(mockFullGraph);
            when(mockFullGraph.getNodes()).thenReturn(allNodes);
            when(mockFullGraph.getSuccessors(node)).thenReturn(Collections.emptyList());

            ProgramDependenceGraph result = SlicerUtil.simplify(mockPDG);

            assertNotNull(result);
            verify(mockPDG).computeResult();
        }
    }

    void tearDown() {
        // Restore original System.err
        System.setErr(originalErr);
    }
}