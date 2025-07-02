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
    Optional<Node> entryNode = reversedGraph.getEntry();
    if (entryNode.isEmpty()) {
      return new ProgramGraph();
    }
    Node entry = entryNode.get();

    Map<Node, Set<Node>> dom = new HashMap<>();
    Collection<Node> nodes = reversedGraph.getNodes();

    Set<Node> entryDom = new LinkedHashSet<>();
    entryDom.add(entry);
    dom.put(entry, entryDom);

    for (Node n : nodes) {
      if (!n.equals(entry)) {
        Set<Node> allNodesDom = new LinkedHashSet<>(nodes);
        dom.put(n, allNodesDom);
      }
    }

    boolean changed = true;
    while (changed) {
      changed = false;

      for (Node n : nodes) {
        if (n.equals(entry)) {
          continue;
        }

        Set<Node> currDom = dom.get(n);
        Set<Node> newDom = new LinkedHashSet<>();
        newDom.add(n);

        Collection<Node> preds = reversedGraph.getPredecessors(n);
        if (!preds.isEmpty()) {
          Set<Node> intersection = new LinkedHashSet<>(dom.get(preds.iterator().next()));
          for (Node p : preds) {
            intersection.retainAll(dom.get(p));
          }
          newDom.addAll(intersection);
        }

        if (!currDom.equals(newDom)) {
          dom.put(n, newDom);
          changed = true;
        }
      }
    }

    Map<Node, Node> idom = new HashMap<>();
    for (Node n : nodes) {
      if (n.equals(entry)) {
        continue;
      }

      Set<Node> strictDom = new LinkedHashSet<>(dom.get(n));
      strictDom.remove(n);

      Node immDom = null;
      for (Node candidate : strictDom) {
        boolean isImm = true;
        for (Node other : strictDom) {
          if (!candidate.equals(other) && dom.get(candidate).contains(other)) {
            isImm = false;
            break;
          }
        }
        if (isImm) {
          immDom = candidate;
          break;
        }
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
