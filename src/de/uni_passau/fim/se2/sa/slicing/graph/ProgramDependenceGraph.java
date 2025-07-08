package de.uni_passau.fim.se2.sa.slicing.graph;

import de.uni_passau.fim.se2.sa.slicing.cfg.Node;
import de.uni_passau.fim.se2.sa.slicing.cfg.ProgramGraph;

import java.util.*;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Provides an analysis that calculates the program-dependence graph. */
public class ProgramDependenceGraph extends Graph implements Sliceable<Node> {

  private ProgramGraph pdg;
  private final ProgramGraph cdg;
  private final ProgramGraph ddg;

  public ProgramDependenceGraph(ClassNode pClassNode, MethodNode pMethodNode) {
    super(pClassNode, pMethodNode);
    pdg = null;

    if (cfg != null) {
      cdg = new ControlDependenceGraph(pClassNode, pMethodNode).computeResult();
      ddg = new DataDependenceGraph(pClassNode, pMethodNode).computeResult();
    } else {
      cdg = null;
      ddg = null;
    }
  }

  public ProgramDependenceGraph(ProgramGraph pProgramGraph) {
    super(null);
    pdg = pProgramGraph;
    cdg = null;
    ddg = null;
  }

  /**
   * Computes the program-dependence graph from a control-flow graph.
   *
   * <p>You may wish to use the {@link ControlDependenceGraph} and {@link DataDependenceGraph} you
   * have already implemented to support computing the program-dependence graph.
   *
   * @return A program-dependence graph.
   */
  @Override
  public ProgramGraph computeResult() {
    if (pdg != null) {
      return pdg;
    }

    pdg = new ProgramGraph();

    if (cdg != null) {
      for (Node node : cdg.getNodes()) {
        pdg.addNode(node);
      }
    }

    if (ddg != null) {
      for (Node node : ddg.getNodes()) {
        pdg.addNode(node);
      }
    }

    if (cdg != null) {
      for (Node src : cdg.getNodes()) {
        for (Node target : cdg.getSuccessors(src)) {
          pdg.addEdge(src, target);
        }
      }
    }

    if (ddg != null) {
      for (Node src : ddg.getNodes()) {
        for (Node target : ddg.getSuccessors(src)) {
          pdg.addEdge(src, target);
        }
      }
    }

    return pdg;
  }



  /** {@inheritDoc} */
  @Override
  public Set<Node> backwardSlice(Node pCriterion) {
    ProgramGraph pdgGraph = computeResult();
    Set<Node> slice = new HashSet<>();
    Deque<Node> worklist = new ArrayDeque<>();
    worklist.add(pCriterion);
    while (!worklist.isEmpty()) {
      Node current = worklist.poll();
      if (slice.add(current)) { // add returns true if not already present
        for (Node pred : pdgGraph.getPredecessors(current)) {
          worklist.add(pred);
        }
      }
    }
    return slice;
  }
}
