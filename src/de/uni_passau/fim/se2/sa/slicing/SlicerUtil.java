package de.uni_passau.fim.se2.sa.slicing;

import de.uni_passau.fim.se2.sa.slicing.cfg.Node;
import de.uni_passau.fim.se2.sa.slicing.cfg.ProgramGraph;
import de.uni_passau.fim.se2.sa.slicing.coverage.CoverageTracker;
import de.uni_passau.fim.se2.sa.slicing.graph.ProgramDependenceGraph;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.Set;

public class SlicerUtil {

    /**
     * Executes the provided test case on the given class via the JUnit test framework.
     *
     * @param className The name of the class to be tested.
     * @param testCase  The name of the test case to be executed.
     */
    public static void executeTest(String className, String testCase) {
        try {
            CoverageTracker.reset();

            Launcher junitLauncher = LauncherFactory.create();

            String assumedTestClass = className + "Test";

            LauncherDiscoveryRequest testRequest = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectMethod(assumedTestClass, testCase))
                    .build();

            junitLauncher.execute(testRequest);
        } catch (Exception ex) {
            System.err.println("Test execution failed for " + testCase + " in " + className + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Simplifies the given program dependence graph by removing all nodes and corresponding edges
     * that were not covered by the executed test.
     *
     * @param pPDG The program dependence graph to simplify.
     * @return The simplified program dependence graph.
     */
    public static ProgramDependenceGraph simplify(final ProgramDependenceGraph pPDG) {
        Set<Integer> coveredLines = CoverageTracker.getVisitedLines();

        if (coveredLines.isEmpty()) {
            return pPDG;
        }

        ProgramGraph fullGraph = pPDG.computeResult();
        ProgramGraph reducedGraph = new ProgramGraph();

        for (Node n : fullGraph.getNodes()) {
            int ln = n.getLineNumber();
            if (ln > 0 && coveredLines.contains(ln)) {
                reducedGraph.addNode(n);
            }
        }

        for (Node src : reducedGraph.getNodes()) {
            for (Node dst : fullGraph.getSuccessors(src)) {
                if (reducedGraph.getNodes().contains(dst)) {
                    reducedGraph.addEdge(src, dst);
                }
            }
        }

        return new ProgramDependenceGraph(reducedGraph);
    }
}
