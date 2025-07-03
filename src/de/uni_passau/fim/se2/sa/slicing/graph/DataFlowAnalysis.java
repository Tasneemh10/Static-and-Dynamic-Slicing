package de.uni_passau.fim.se2.sa.slicing.graph;

import br.usp.each.saeg.asm.defuse.DefUseAnalyzer;
import br.usp.each.saeg.asm.defuse.DefUseChain;
import br.usp.each.saeg.asm.defuse.DefUseFrame;
import br.usp.each.saeg.asm.defuse.Variable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

/** Provides a simple data-flow analysis. */
class DataFlowAnalysis {
  private static final DefUseAnalyzer ANALYZER = new DefUseAnalyzer();

  private DataFlowAnalysis() {}

  /**
   * Provides the collection of {@link Variable}s that are used by the given instruction.
   *
   * @param pOwningClass The class that owns the method
   * @param pMethodNode The method that contains the instruction
   * @param pInstruction The instruction
   * @return The collection of {@link Variable}s that are used by the given instruction
   * @throws AnalyzerException In case an error occurs during the analysis
   */
  static Collection<Variable> usedBy(
      String pOwningClass, MethodNode pMethodNode, AbstractInsnNode pInstruction)
      throws AnalyzerException {
    try {
      ANALYZER.analyze(pOwningClass, pMethodNode);

      DefUseFrame[] frames = ANALYZER.getDefUseFrames();

      Set<Variable> usedVariables = new HashSet<>();

      int instructionIndex = findInstructionIndex(pMethodNode, pInstruction);
      if (instructionIndex == -1 || instructionIndex >= frames.length) {
        return usedVariables;
      }

      DefUseFrame frame = frames[instructionIndex];
      Set<Variable> uses = frame.getUses();

      usedVariables.addAll(uses);

      return usedVariables;

    } catch (Exception e) {
      throw new AnalyzerException(null, "Error analyzing variable uses", e);
    }
  }

  private static int findInstructionIndex(MethodNode methodNode, AbstractInsnNode instruction) {
    AbstractInsnNode[] instructions = methodNode.instructions.toArray();
    for (int i = 0; i < instructions.length; i++) {
      if (instructions[i] == instruction) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Provides the collection of {@link Variable}s that are defined by the given instruction.
   *
   * @param pOwningClass The class that owns the method
   * @param pMethodNode The method that contains the instruction
   * @param pInstruction The instruction
   * @return The collection of {@link Variable}s that are defined by the given instruction
   * @throws AnalyzerException In case an error occurs during the analysis
   */
  static Collection<Variable> definedBy(
      String pOwningClass, MethodNode pMethodNode, AbstractInsnNode pInstruction)
      throws AnalyzerException {
    try {
      ANALYZER.analyze(pOwningClass, pMethodNode);

      DefUseFrame[] frames = ANALYZER.getDefUseFrames();

      Set<Variable> definedVariables = new HashSet<>();

      int instructionIndex = findInstructionIndex(pMethodNode, pInstruction);
      if (instructionIndex == -1 || instructionIndex >= frames.length) {
        return definedVariables; // Instruction not found or out of bounds
      }

      DefUseFrame frame = frames[instructionIndex];
      Set<Variable> definitions = frame.getDefinitions();

      definedVariables.addAll(definitions);

      return definedVariables;

    } catch (Exception e) {
      throw new AnalyzerException(null, "Error analyzing variable definitions", e);
    }
  }
}
