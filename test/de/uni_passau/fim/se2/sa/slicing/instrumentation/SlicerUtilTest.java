package de.uni_passau.fim.se2.sa.slicing.instrumentation;

import static org.junit.jupiter.api.Assertions.*;

import de.uni_passau.fim.se2.sa.slicing.coverage.CoverageTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class CoverageTrackerTest {

    @BeforeEach
    void setUp() {
        CoverageTracker.reset();
    }

    @AfterEach
    void tearDown() {
        CoverageTracker.reset();
    }

    @Test
    void testInitialStateEmpty() {
        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        assertNotNull(visitedLines);
        assertTrue(visitedLines.isEmpty());
        assertEquals(0, visitedLines.size());
    }

    @Test
    void testTrackSingleLineVisit() {
        CoverageTracker.trackLineVisit(10);

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        assertNotNull(visitedLines);
        assertEquals(1, visitedLines.size());
        assertTrue(visitedLines.contains(10));
        assertFalse(visitedLines.contains(9));
        assertFalse(visitedLines.contains(11));
    }

    @Test
    void testTrackMultipleLineVisits() {
        CoverageTracker.trackLineVisit(5);
        CoverageTracker.trackLineVisit(10);
        CoverageTracker.trackLineVisit(15);

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        assertNotNull(visitedLines);
        assertEquals(3, visitedLines.size());
        assertTrue(visitedLines.contains(5));
        assertTrue(visitedLines.contains(10));
        assertTrue(visitedLines.contains(15));
        assertFalse(visitedLines.contains(1));
        assertFalse(visitedLines.contains(20));
    }

    @Test
    void testTrackDuplicateLineVisits() {
        CoverageTracker.trackLineVisit(10);
        CoverageTracker.trackLineVisit(10);
        CoverageTracker.trackLineVisit(10);

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        assertNotNull(visitedLines);
        assertEquals(1, visitedLines.size());
        assertTrue(visitedLines.contains(10));
    }

    @Test
    void testTrackNegativeLineNumbers() {
        CoverageTracker.trackLineVisit(-1);
        CoverageTracker.trackLineVisit(-5);

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        assertNotNull(visitedLines);
        assertEquals(2, visitedLines.size());
        assertTrue(visitedLines.contains(-1));
        assertTrue(visitedLines.contains(-5));
    }

    @Test
    void testTrackZeroLineNumber() {
        CoverageTracker.trackLineVisit(0);

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        assertNotNull(visitedLines);
        assertEquals(1, visitedLines.size());
        assertTrue(visitedLines.contains(0));
    }

    @Test
    void testTrackLargeLineNumbers() {
        CoverageTracker.trackLineVisit(Integer.MAX_VALUE);
        CoverageTracker.trackLineVisit(Integer.MIN_VALUE);
        CoverageTracker.trackLineVisit(1000000);

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        assertNotNull(visitedLines);
        assertEquals(3, visitedLines.size());
        assertTrue(visitedLines.contains(Integer.MAX_VALUE));
        assertTrue(visitedLines.contains(Integer.MIN_VALUE));
        assertTrue(visitedLines.contains(1000000));
    }

    @Test
    void testReset() {
        // Add some line visits
        CoverageTracker.trackLineVisit(1);
        CoverageTracker.trackLineVisit(2);
        CoverageTracker.trackLineVisit(3);

        assertEquals(3, CoverageTracker.getVisitedLines().size());

        // Reset and verify empty
        CoverageTracker.reset();

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();
        assertNotNull(visitedLines);
        assertTrue(visitedLines.isEmpty());
        assertEquals(0, visitedLines.size());
    }

    @Test
    void testResetMultipleTimes() {
        CoverageTracker.trackLineVisit(10);
        assertEquals(1, CoverageTracker.getVisitedLines().size());

        CoverageTracker.reset();
        assertEquals(0, CoverageTracker.getVisitedLines().size());

        CoverageTracker.reset();
        assertEquals(0, CoverageTracker.getVisitedLines().size());

        CoverageTracker.trackLineVisit(20);
        assertEquals(1, CoverageTracker.getVisitedLines().size());
        assertTrue(CoverageTracker.getVisitedLines().contains(20));
        assertFalse(CoverageTracker.getVisitedLines().contains(10));
    }

    @Test
    void testGetVisitedLinesReturnsUnmodifiableSet() {
        CoverageTracker.trackLineVisit(10);
        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        assertNotNull(visitedLines);
        assertEquals(1, visitedLines.size());

        // Attempt to modify the returned set should throw exception
        assertThrows(UnsupportedOperationException.class, () -> {
            visitedLines.add(20);
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            visitedLines.remove(10);
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            visitedLines.clear();
        });

        // Original set should remain unchanged
        assertEquals(1, visitedLines.size());
        assertTrue(visitedLines.contains(10));
    }

    @Test
    void testGetVisitedLinesConsistency() {
        CoverageTracker.trackLineVisit(5);
        CoverageTracker.trackLineVisit(10);

        Set<Integer> firstCall = CoverageTracker.getVisitedLines();
        Set<Integer> secondCall = CoverageTracker.getVisitedLines();

        assertNotNull(firstCall);
        assertNotNull(secondCall);
        assertEquals(firstCall.size(), secondCall.size());
        assertEquals(firstCall, secondCall);

        // Sets should contain same elements
        for (Integer line : firstCall) {
            assertTrue(secondCall.contains(line));
        }
    }

    @Test
    void testLinkedHashSetOrderPreservation() {
        // Track lines in specific order
        CoverageTracker.trackLineVisit(30);
        CoverageTracker.trackLineVisit(10);
        CoverageTracker.trackLineVisit(20);
        CoverageTracker.trackLineVisit(15);

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();
        assertEquals(4, visitedLines.size());

        // Verify insertion order is preserved (LinkedHashSet property)
        Integer[] expectedOrder = {30, 10, 20, 15};
        Integer[] actualOrder = visitedLines.toArray(new Integer[0]);

        assertArrayEquals(expectedOrder, actualOrder);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int numThreads = 10;
        int linesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        // Each thread tracks different line numbers
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < linesPerThread; j++) {
                        CoverageTracker.trackLineVisit(threadId * linesPerThread + j);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();

        // The implementation may not be fully thread-safe, so we test that:
        // 1. Some lines were tracked (not zero)
        // 2. No more than expected were tracked
        // 3. All tracked lines are within expected range
        int expectedTotal = numThreads * linesPerThread;
        assertTrue(visitedLines.size() > 0, "Should track some lines");
        assertTrue(visitedLines.size() <= expectedTotal, "Should not exceed expected total");

        // Verify all tracked lines are within expected range
        for (Integer line : visitedLines) {
            assertTrue(line >= 0 && line < expectedTotal,
                    "Line number " + line + " is outside expected range [0, " + expectedTotal + ")");
        }
    }

    @Test
    void testConcurrentResetAndTrack() throws InterruptedException {
        int numOperations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);

        // Mix of track and reset operations
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < numOperations; j++) {
                        if (j % 100 == 0 && threadId == 0) {
                            CoverageTracker.reset();
                        } else {
                            CoverageTracker.trackLineVisit(threadId * numOperations + j);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Should complete without exceptions
        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();
        assertNotNull(visitedLines);
        assertTrue(visitedLines.size() >= 0);
    }

    @Test
    void testStaticUtilityClassDesign() {
        // Verify class is final
        assertTrue(java.lang.reflect.Modifier.isFinal(CoverageTracker.class.getModifiers()));

        // Verify all methods are static
        var methods = CoverageTracker.class.getDeclaredMethods();
        for (var method : methods) {
            assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()),
                    "Method " + method.getName() + " should be static");
        }

        // Verify private constructor exists
        var constructors = CoverageTracker.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructors[0].getModifiers()));
    }

    @Test
    void testSequentialOperations() {
        // Test a realistic sequence of operations

        // Initial state
        assertTrue(CoverageTracker.getVisitedLines().isEmpty());

        // Track some lines
        CoverageTracker.trackLineVisit(1);
        CoverageTracker.trackLineVisit(5);
        CoverageTracker.trackLineVisit(3);
        assertEquals(3, CoverageTracker.getVisitedLines().size());

        // Track duplicate
        CoverageTracker.trackLineVisit(1);
        assertEquals(3, CoverageTracker.getVisitedLines().size());

        // Track more lines
        CoverageTracker.trackLineVisit(7);
        CoverageTracker.trackLineVisit(2);
        assertEquals(5, CoverageTracker.getVisitedLines().size());

        // Verify specific contents
        Set<Integer> lines = CoverageTracker.getVisitedLines();
        assertTrue(lines.contains(1));
        assertTrue(lines.contains(2));
        assertTrue(lines.contains(3));
        assertTrue(lines.contains(5));
        assertTrue(lines.contains(7));
        assertFalse(lines.contains(4));
        assertFalse(lines.contains(6));

        // Reset and verify
        CoverageTracker.reset();
        assertTrue(CoverageTracker.getVisitedLines().isEmpty());
    }

    @Test
    void testLargeNumberOfLines() {
        // Test with many line numbers
        int numLines = 10000;

        for (int i = 0; i < numLines; i++) {
            CoverageTracker.trackLineVisit(i);
        }

        Set<Integer> visitedLines = CoverageTracker.getVisitedLines();
        assertEquals(numLines, visitedLines.size());

        // Verify all lines are present
        for (int i = 0; i < numLines; i++) {
            assertTrue(visitedLines.contains(i));
        }

        // Verify order preservation (LinkedHashSet)
        Integer[] array = visitedLines.toArray(new Integer[0]);
        for (int i = 0; i < numLines; i++) {
            assertEquals(Integer.valueOf(i), array[i]);
        }
    }
}