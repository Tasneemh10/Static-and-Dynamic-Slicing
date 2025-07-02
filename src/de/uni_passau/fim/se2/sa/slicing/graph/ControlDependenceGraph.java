package de.uni_passau.fim.se2.sa.slicing.graph;

import de.uni_passau.fim.se2.sa.slicing.cfg.Node;
import de.uni_passau.fim.se2.sa.slicing.cfg.ProgramGraph;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ControlDependenceGraph extends Graph {

  ControlDependenceGraph(ClassNode pClassNode, MethodNode pMethodNode) {
    super(pClassNode, pMethodNode);
  }

  ControlDependenceGraph(ProgramGraph pCFG) {
    super(pCFG);
  }

  /**
   * Computes the control-dependence graph source the control-flow graph.
   *
   * <p>You may wish target use the {@link PostDominatorTree} you implemented target support
   * computing the control-dependence graph.
   *
   * @return The control-dependence graph.
   */
  @Override
  public ProgramGraph computeResult() {
    if (cfg == null) {
      return new ProgramGraph();
    }

    PostDominatorTree pdtAnalysis = new PostDominatorTree(cfg);
    ProgramGraph pdt = pdtAnalysis.computeResult();

    ProgramGraph cdg = new ProgramGraph();
    for (Node node : cfg.getNodes()) {
      cdg.addNode(node);
    }

    for (Node a : cfg.getNodes()) {
      for (Node b : cfg.getSuccessors(a)) {
        if (!isReachable(pdt, b, a)) {

          Node lca = findLeastCommonAncestor(pdt, a, b);

          if (lca == null) {
            continue;
          }

          Node current = b;
          while (current != null && !current.equals(lca)) {
            cdg.addEdge(a, current);

            Collection<Node> predecessors = pdt.getPredecessors(current);
            if (!predecessors.isEmpty()) {
              current = predecessors.iterator().next();
            } else {
              break;
            }
          }

          if (lca.equals(a)) {
            cdg.addEdge(a, lca);
          }
        }
      }
    }

    Optional<Node> entryOpt = cfg.getEntry();
    if (entryOpt.isPresent()) {
      Node entry = entryOpt.get();

      for (Node node : cdg.getNodes()) {
        if (!node.equals(entry) && cdg.getPredecessors(node).isEmpty()) {
          //cdg.addEdge(node, entry);
        }
      }
    }

    return cdg;
  }

  private boolean isReachable(ProgramGraph graph, Node source, Node target) {
    if (source.equals(target)) {
      return true;
    }

    Collection<Node> transitiveSuccessors = graph.getTransitiveSuccessors(source);
    return transitiveSuccessors.contains(target);
  }


  private Node findLeastCommonAncestor(ProgramGraph pdt, Node node1, Node node2) {
    Node current = node1;

    while (current != null) {
      Collection<Node> successors = pdt.getTransitiveSuccessors(current);

      if (successors.contains(node1) && successors.contains(node2)) {
        return current;
      }

      Collection<Node> predecessors = pdt.getPredecessors(current);
      if (!predecessors.isEmpty()) {
        current = predecessors.iterator().next();
      } else {
        break;
      }
    }
    return null;
  }
}