package de.uni_passau.fim.se2.sa.slicing.graph;

import br.usp.each.saeg.asm.defuse.*;
import de.uni_passau.fim.se2.sa.slicing.cfg.Node;
import de.uni_passau.fim.se2.sa.slicing.cfg.ProgramGraph;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.*;

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
      for (Node node : cfg.getNodes()) {
        ddg.addNode(node);
      }

      String className = classNode.name;

      Map<Node, Set<Variable>> nodeToDefinitions = new HashMap<>();

      for (Node node : cfg.getNodes()) {
        AbstractInsnNode instruction = node.getInstruction();
        if (instruction != null) {
          try {
            Collection<Variable> definedVars = DataFlowAnalysis.definedBy(className, methodNode, instruction);
            nodeToDefinitions.put(node, new HashSet<>(definedVars));
          } catch (AnalyzerException e) {
            nodeToDefinitions.put(node, new HashSet<>());
          }
        } else {
          nodeToDefinitions.put(node, new HashSet<>());
        }
      }

      Map<Node, Set<Variable>> inFacts = new HashMap<>();
      Map<Node, Set<Variable>> outFacts = new HashMap<>();

      for (Node node : cfg.getNodes()) {
        inFacts.put(node, new HashSet<>());
        outFacts.put(node, new HashSet<>());
      }

      boolean changed = true;
      while (changed) {
        changed = false;

        for (Node node : cfg.getNodes()) {
          Set<Variable> newIn = new HashSet<>();
          for (Node pred : cfg.getPredecessors(node)) {
            newIn.addAll(outFacts.get(pred));
          }

          Set<Variable> newOut = reachingDefinitionsTransfer(node, newIn, className);

          if (!newIn.equals(inFacts.get(node)) || !newOut.equals(outFacts.get(node))) {
            changed = true;
            inFacts.put(node, newIn);
            outFacts.put(node, newOut);
          }
        }
      }

      for (Node useNode : cfg.getNodes()) {
        AbstractInsnNode useInstruction = useNode.getInstruction();
        if (useInstruction == null) continue;

        try {
          Collection<Variable> usedVars = DataFlowAnalysis.usedBy(className, methodNode, useInstruction);

          for (Variable usedVar : usedVars) {
            Set<Variable> reachingDefs = inFacts.get(useNode);

            for (Variable reachingDef : reachingDefs) {
              if (sameVariable(usedVar, reachingDef)) {
                Node defNode = findDefiningNode(reachingDef, nodeToDefinitions);
                if (defNode != null) {
                  ddg.addEdge(defNode, useNode);
                }
              }
            }
          }
        } catch (AnalyzerException ignored) {
        }
      }

      return ddg;

    } catch (Exception e) {
      ProgramGraph ddg = new ProgramGraph();
      for (Node node : cfg.getNodes()) {
        ddg.addNode(node);
      }
      return ddg;
    }
  }

  private Set<Variable> reachingDefinitionsTransfer(Node node, Set<Variable> inFacts, String className) {
    try {
      Set<Variable> result = new HashSet<>(inFacts);

      AbstractInsnNode instruction = node.getInstruction();
      if (instruction != null) {
        Collection<Variable> definedVars = DataFlowAnalysis.definedBy(className, methodNode, instruction);

        result.removeIf(definition -> {
          for (Variable definedVar : definedVars) {
            if (sameVariable(definition, definedVar)) {
              return true;
            }
          }
          return false;
        });

        result.addAll(definedVars);
      }

      return result;

    } catch (AnalyzerException e) {
      return new HashSet<>(inFacts);
    }
  }


  private boolean sameVariable(Variable v1, Variable v2) {
    return Objects.equals(v1, v2) &&
            Objects.equals(v1.type, v2.type);
  }

  private Node findDefiningNode(Variable targetVar, Map<Node, Set<Variable>> nodeToDefinitions) {
    for (Map.Entry<Node, Set<Variable>> entry : nodeToDefinitions.entrySet()) {
      Node node = entry.getKey();
      Set<Variable> definedVars = entry.getValue();

      for (Variable definedVar : definedVars) {
        if (sameVariable(targetVar, definedVar)) {
          return node;
        }
      }
    }
    return null;
  }
}


