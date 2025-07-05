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

      Map<Node, Set<Variable>> genSets = new HashMap<>();
      Map<Node, Set<Variable>> killSets = new HashMap<>();

      Set<Variable> allDefinedVariables = new HashSet<>();
      Map<Variable, Set<Node>> variableToDefiningNodes = new HashMap<>();

      for (Node node : cfg.getNodes()) {
        AbstractInsnNode instruction = node.getInstruction();
        Set<Variable> gen = new HashSet<>();

        if (instruction != null) {
          try {
            Collection<Variable> definedVars = DataFlowAnalysis.definedBy(className, methodNode, instruction);
            gen.addAll(definedVars);
            allDefinedVariables.addAll(definedVars);

            for (Variable var : definedVars) {
              variableToDefiningNodes.computeIfAbsent(var, k -> new HashSet<>()).add(node);
            }
          } catch (AnalyzerException ignored) {
          }
        }
        genSets.put(node, gen);
      }

      for (Node node : cfg.getNodes()) {
        Set<Variable> kill = new HashSet<>();
        Set<Variable> gen = genSets.get(node);

        for (Variable genVar : gen) {
          Set<Node> definingNodes = variableToDefiningNodes.get(genVar);
          if (definingNodes != null) {
            for (Node defNode : definingNodes) {
              if (!defNode.equals(node)) {
                for (Variable otherVar : allDefinedVariables) {
                  if (otherVar.equals(genVar) && variableToDefiningNodes.get(otherVar).contains(defNode)) {
                    kill.add(otherVar);
                  }
                }
              }
            }
          }
        }
        killSets.put(node, kill);
      }

      Map<Node, Set<Variable>> inSets = new HashMap<>();
      Map<Node, Set<Variable>> outSets = new HashMap<>();

      for (Node node : cfg.getNodes()) {
        inSets.put(node, new HashSet<>());
        outSets.put(node, new HashSet<>());
      }

      boolean changed = true;
      while (changed) {
        changed = false;

        for (Node node : cfg.getNodes()) {
          Set<Variable> newIn = new HashSet<>();
          for (Node pred : cfg.getPredecessors(node)) {
            newIn.addAll(outSets.get(pred));
          }

          Set<Variable> newOut = new HashSet<>(genSets.get(node));
          Set<Variable> inMinusKill = new HashSet<>(newIn);
          inMinusKill.removeAll(killSets.get(node));
          newOut.addAll(inMinusKill);

          if (!newIn.equals(inSets.get(node)) || !newOut.equals(outSets.get(node))) {
            changed = true;
            inSets.put(node, newIn);
            outSets.put(node, newOut);
          }
        }
      }

      for (Node useNode : cfg.getNodes()) {
        AbstractInsnNode useInstruction = useNode.getInstruction();
        if (useInstruction == null) continue;

        try {
          Collection<Variable> usedVars = DataFlowAnalysis.usedBy(className, methodNode, useInstruction);

          for (Variable usedVar : usedVars) {
            Set<Variable> reachingDefs = inSets.get(useNode);

            for (Variable reachingDef : reachingDefs) {
              if (usedVar.equals(reachingDef)) {
                for (Node defNode : cfg.getNodes()) {
                  Set<Variable> genSet = genSets.get(defNode);
                  if (genSet.contains(reachingDef)) {
                    ddg.addEdge(defNode, useNode);
                  }
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
  }


