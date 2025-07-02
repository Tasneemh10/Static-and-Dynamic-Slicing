package de.uni_passau.fim.se2.sa.slicing.graph;

import de.uni_passau.fim.se2.sa.slicing.cfg.Node;
import de.uni_passau.fim.se2.sa.slicing.cfg.ProgramGraph;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

/** Provides an analysis computing a post-dominator tree for a CFG. */
public class PostDominatorTree extends Graph {

  PostDominatorTree(ClassNode pClassNode, MethodNode pMethodNode) {
    super(pClassNode, pMethodNode);
  }

  PostDominatorTree(ProgramGraph pCFG) {
    super(pCFG);
  }

  /**
   * Computes the post-dominator tree of the method.
   *
   * <p>The implementation uses the {@link #cfg} graph as the starting point.
   *
   * @return The post-dominator tree of the control-flow graph
   */
  @Override
  public ProgramGraph computeResult() {
    ProgramGraph reversedGraph = reverseGraph(cfg);
    Optional<Node> entryOptional = reversedGraph.getEntry();
    if (entryOptional.isEmpty()) {
      return new ProgramGraph();
    }
    Node entry = entryOptional.get();
    Map<Node, Set<Node>> D = new HashMap<>();

    Set<Node> entryDom = new LinkedHashSet<>();
    entryDom.add(entry);
    D.put(entry, entryDom);

    Collection<Node> nodes = reversedGraph.getNodes();
    for (Node n : nodes) {
      if (!n.equals(entry)) {
        Set<Node> allNodes = new LinkedHashSet<>(nodes);
        D.put(n, allNodes);
      }
    }

    boolean changed = true;
    while (changed) {
      changed = false;

      for (Node n : nodes) {
        if (n.equals(entry)) {
          continue;
        }

        Set<Node> currentDominators = D.get(n);

        Set<Node> newDominators = new LinkedHashSet<>();
        newDominators.add(n);

        Collection<Node> predecessors = reversedGraph.getPredecessors(n);
        if (!predecessors.isEmpty()) {
          Set<Node> intersection = new LinkedHashSet<>(D.get(predecessors.iterator().next()));

          for (Node p : predecessors) {
            intersection.retainAll(D.get(p));
          }

          newDominators.addAll(intersection);
        }

        if (!currentDominators.equals(newDominators)) {
          D.put(n, newDominators);
          changed = true;
        }
      }
    }


    Map<Node, Node> idom = new HashMap<>();
    for (Node n : nodes) {
      if (n.equals(entry)) {
        continue;
      }

      Set<Node> strictDom = new LinkedHashSet<>(D.get(n));
      strictDom.remove(n);

      Node immDom = null;
      Set<Node> candidateSet = new LinkedHashSet<>(strictDom);

      // Remove dominated candidates (keep dominators)
      for (Node candidate : strictDom) {
        for (Node other : strictDom) {
          if (!candidate.equals(other) && D.get(candidate).contains(other)) {
            candidateSet.remove(other);
          }
        }
      }

      if (candidateSet.size() == 1) {
        immDom = candidateSet.iterator().next();
      }

      if (immDom != null) {
        idom.put(n, immDom);
      }
    }

    ProgramGraph tree = new ProgramGraph();
    for (Node n : cfg.getNodes()) {
      tree.addNode(n);
    }

    for (Map.Entry<Node, Node> entry1 : idom.entrySet()) {
      Node dominated = entry1.getKey();
      Node dominator = entry1.getValue();
      tree.addEdge(dominated, dominator);
    }

    return tree;
  }
}
