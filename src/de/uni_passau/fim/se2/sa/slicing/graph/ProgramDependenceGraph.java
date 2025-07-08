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

    System.err.println("=== DEBUG PDG Construction ===");
    if (cdg != null) {
      System.err.println("CDG nodes: " + cdg.getNodes().size());
      for (Node n : cdg.getNodes()) {
        System.err.println("CDG node: " + n);
      }
    }
    if (ddg != null) {
      System.err.println("DDG nodes: " + ddg.getNodes().size());
      for (Node n : ddg.getNodes()) {
        System.err.println("DDG node: " + n);
      }
    }

    // Collect nodes from CFG first to ensure we have all nodes
    Set<Node> allNodes = new HashSet<>();
    if (cfg != null) {
      allNodes.addAll(cfg.getNodes());
    }

    // Then add nodes from CDG and DDG
    if (cdg != null) {
      allNodes.addAll(cdg.getNodes());
    }
    if (ddg != null) {
      allNodes.addAll(ddg.getNodes());
    }

    // Add nodes to PDG
    allNodes.forEach(pdg::addNode);

    // Transfer edges from control dependence graph
    if (cdg != null) {
      transferEdges(cdg, pdg);
    }

    // Transfer edges from data dependence graph
    if (ddg != null) {
      transferEdges(ddg, pdg);
    }

    System.err.println("Final PDG nodes: " + pdg.getNodes().size());
    System.err.println("=== END DEBUG ===");

    return pdg;
  }

  private void transferEdges(ProgramGraph source, ProgramGraph target) {
    for (Node sourceNode : source.getNodes()) {
      source.getSuccessors(sourceNode).forEach(successor -> {
        target.addEdge(sourceNode, successor);
      });
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<Node> backwardSlice(Node pCriterion) {
    ProgramGraph pdgGraph = computeResult();

    if (pdgGraph == null || pCriterion == null) {
      return new LinkedHashSet<>();
    }

    Set<Node> slice = new HashSet<>();
    collectDependencies(pCriterion, pdgGraph, slice);

    List<Node> sortedNodes = new ArrayList<>(slice);
    sortedNodes.sort((n1, n2) -> {
      try {
        int id1 = Integer.parseInt(n1.getID().replaceAll("\"", ""));
        int id2 = Integer.parseInt(n2.getID().replaceAll("\"", ""));
        return Integer.compare(id1, id2);
      } catch (NumberFormatException e) {
        return n1.getID().compareTo(n2.getID());
      }
    });

    return new LinkedHashSet<>(sortedNodes);
  }

  private void collectDependencies(Node node, ProgramGraph graph, Set<Node> visited) {
    if (visited.contains(node)) {
      return;
    }
    visited.add(node);
    for (Node dependency : graph.getPredecessors(node)) {
      collectDependencies(dependency, graph, visited);
    }
  }
}
