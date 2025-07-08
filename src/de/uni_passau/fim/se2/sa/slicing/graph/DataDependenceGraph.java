package de.uni_passau.fim.se2.sa.slicing.graph;

import br.usp.each.saeg.asm.defuse.*;
import de.uni_passau.fim.se2.sa.slicing.cfg.Node;
import de.uni_passau.fim.se2.sa.slicing.cfg.ProgramGraph;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.*;
import java.util.stream.Collectors;

public class DataDependenceGraph extends Graph {

  DataDependenceGraph(ClassNode pClassNode, MethodNode pMethodNode) {
    super(pClassNode, pMethodNode);
  }

  /**
   * Computes the data-dependence graph from the control-flow graph.
   *
   * <p>This requires the computation of the reaching-definition algorithm. We recommend using the
   * provided {@link DataFlowAnalysis} implementation.
   *
   * <p>Remember that the CFG stores for each node the instruction at that node. With that, calling
   * {@link DataFlowAnalysis#definedBy(String, MethodNode, AbstractInsnNode)} provides a collection
   * of {@link Variable}s that are defined by this particular instruction; calling {@link
   * DataFlowAnalysis#usedBy(String, MethodNode, AbstractInsnNode)} provides a collection of {@link
   * Variable}s that are used by this particular instruction, respectively. From this information
   * you can compute for each node n in the CFG the GEN[n] and KILL[n] sets. Afterwards, it is
   * possible to compute the IN[n] and OUT[n] sets using the reaching-definitions algorithm.
   *
   * <p>Finally, you can compute all def-use pairs and construct the data-dependence graph from
   * these pairs.
   *
   * @return The data-dependence graph for a control-flow graph
   */
  @Override

  public ProgramGraph computeResult() {
    if (cfg == null || methodNode == null || classNode == null) {
      return new ProgramGraph();
    }

    try {
      ProgramGraph ddg = new ProgramGraph();
      String className = classNode.name;

      // Add all nodes from CFG to DDG
      cfg.getNodes().forEach(ddg::addNode);

      // Step 1: Collect all definitions and uses with proper node tracking
      Map<Node, Set<DefUse>> gen = new HashMap<>();
      Map<Node, Set<DefUse>> kill = new HashMap<>();
      Map<Node, Set<Variable>> uses = new HashMap<>();

      // Track all definitions in the program
      Set<DefUse> allDefs = new HashSet<>();

      collectDefUseInfo(className, gen, kill, uses, allDefs);

      // Step 2: Perform reaching definitions analysis
      Map<Node, Set<DefUse>> reachIn = new HashMap<>();
      Map<Node, Set<DefUse>> reachOut = new HashMap<>();

      performReachingDefsAnalysis(gen, kill, reachIn, reachOut);

      // Step 3: Build DDG edges based on def-use chains
      buildDDGEdges(ddg, reachIn, uses);

      return ddg;

    } catch (Exception e) {
      e.printStackTrace();
      // Fallback: return empty DDG with just nodes
      ProgramGraph ddg = new ProgramGraph();
      cfg.getNodes().forEach(ddg::addNode);
      return ddg;
    }
  }

  private void collectDefUseInfo(String className,
                                 Map<Node, Set<DefUse>> gen,
                                 Map<Node, Set<DefUse>> kill,
                                 Map<Node, Set<Variable>> uses,
                                 Set<DefUse> allDefs) throws AnalyzerException {

    // Initialize maps
    cfg.getNodes().forEach(node -> {
      gen.put(node, new HashSet<>());
      kill.put(node, new HashSet<>());
      uses.put(node, new HashSet<>());
    });

    // First pass: collect all definitions and uses
    for (Node node : cfg.getNodes()) {
      AbstractInsnNode insn = node.getInstruction();
      if (insn != null) {
        // Get variables defined by this instruction
        Collection<Variable> defs = DataFlowAnalysis.definedBy(className, methodNode, insn);
        for (Variable def : defs) {
          DefUse defUse = new DefUse(node, def);
          gen.get(node).add(defUse);
          allDefs.add(defUse);
        }

        // Get variables used by this instruction
        Collection<Variable> used = DataFlowAnalysis.usedBy(className, methodNode, insn);
        uses.get(node).addAll(used);
      }
    }

    // Second pass: build KILL sets
    for (Node node : cfg.getNodes()) {
      Set<DefUse> currentNodeDefs = gen.get(node);

      // For each definition in this node, kill all other definitions of the same variable
      for (DefUse otherDef : allDefs) {
        for (DefUse myDef : currentNodeDefs) {
          if (otherDef.variable.equals(myDef.variable) && !otherDef.node.equals(node)) {
            kill.get(node).add(otherDef);
          }
        }
      }
    }
  }

  private void performReachingDefsAnalysis(
          Map<Node, Set<DefUse>> gen,
          Map<Node, Set<DefUse>> kill,
          Map<Node, Set<DefUse>> reachIn,
          Map<Node, Set<DefUse>> reachOut) {

    // Initialize IN and OUT sets
    cfg.getNodes().forEach(node -> {
      reachIn.put(node, new HashSet<>());
      reachOut.put(node, new HashSet<>(gen.get(node))); // OUT[n] = GEN[n] initially
    });

    // Fixed-point iteration
    boolean changed = true;
    while (changed) {
      changed = false;

      for (Node node : cfg.getNodes()) {
        // Calculate new IN set (union of predecessors' OUT sets)
        Set<DefUse> newReachIn = new HashSet<>();
        for (Node pred : cfg.getPredecessors(node)) {
          newReachIn.addAll(reachOut.get(pred));
        }

        Set<DefUse> newReachOut = new HashSet<>(gen.get(node));
        Set<DefUse> inMinusKill = new HashSet<>(newReachIn);
        inMinusKill.removeAll(kill.get(node));
        newReachOut.addAll(inMinusKill);

        // Check if anything changed
        if (!newReachIn.equals(reachIn.get(node)) || !newReachOut.equals(reachOut.get(node))) {
          reachIn.put(node, newReachIn);
          reachOut.put(node, newReachOut);
          changed = true;
        }
      }
    }
  }

  private void buildDDGEdges(ProgramGraph ddg,
                             Map<Node, Set<DefUse>> reachIn,
                             Map<Node, Set<Variable>> uses) {

    // For each node and each variable use in that node,
    // find all reaching definitions and add edges
    for (Node useNode : cfg.getNodes()) {
      for (Variable usedVar : uses.get(useNode)) {
        // Find all reaching definitions for this use
        for (DefUse reachingDef : reachIn.get(useNode)) {
          if (reachingDef.variable.equals(usedVar)) {
            ddg.addEdge(reachingDef.node, useNode);
          }
        }
      }
    }
  }

  private boolean sameVariable(Variable v1, Variable v2) {
    if (v1 == v2) {
      return true;
    }
    if (v1 == null || v2 == null) {
      return false;
    }

    // For local variables, compare type and index
    if (v1 instanceof br.usp.each.saeg.asm.defuse.Local && v2 instanceof br.usp.each.saeg.asm.defuse.Local) {
      br.usp.each.saeg.asm.defuse.Local l1 = (br.usp.each.saeg.asm.defuse.Local) v1;
      br.usp.each.saeg.asm.defuse.Local l2 = (br.usp.each.saeg.asm.defuse.Local) v2;
      return l1.var == l2.var && Objects.equals(l1.type, l2.type);
    }

    // For other variable types, use equals method
    return v1.equals(v2);
  }

  // Helper class to track definition with its node
  private static class DefUse {
    final Node node;
    final Variable variable;

    DefUse(Node node, Variable variable) {
      this.node = node;
      this.variable = variable;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DefUse defUse = (DefUse) o;
      return Objects.equals(node, defUse.node) && Objects.equals(variable, defUse.variable);
    }

    @Override
    public int hashCode() {
      return Objects.hash(node, variable);
    }
  }
}


