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
      return null;
    }

    PostDominatorTree pdtAnalysis = new PostDominatorTree(cfg);
    ProgramGraph pdt = pdtAnalysis.computeResult();

    Map<Node, Node> ipdomMap = buildIpdomMap(pdt);

    ProgramGraph cdg = new ProgramGraph();
    for (Node node : cfg.getNodes()) {
      cdg.addNode(node);
    }

    for (Node n : cfg.getNodes()) {
      Collection<Node> succs = cfg.getSuccessors(n);
      if (!succs.isEmpty()) {
        Node nIpdom = ipdomMap.get(n);
        for (Node s : succs) {
          addControlDependencies(cdg, n, s, nIpdom, ipdomMap);
        }
      }
    }

    return cdg;
  }

  private Map<Node, Node> buildIpdomMap(ProgramGraph pdt) {
    Map<Node, Node> ipdomMap = new HashMap<>();
    for (Node n : pdt.getNodes()) {
      Collection<Node> preds = pdt.getPredecessors(n);
      if (preds.size() == 1) {
        ipdomMap.put(n, preds.iterator().next());
      }
    }
    return ipdomMap;
  }

  private void addControlDependencies(ProgramGraph cdg, Node controller, Node start, Node ipdom, Map<Node, Node> ipdomMap) {
    Node walker = start;
    while (walker != null && !walker.equals(ipdom)) {
      cdg.addEdge(controller, walker);
      walker = ipdomMap.get(walker);
    }
  }
}