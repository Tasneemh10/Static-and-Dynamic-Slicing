package de.uni_passau.fim.se2.sa.slicing.instrumentation;

import org.objectweb.asm.*;

class InstrumentationAdapter extends ClassVisitor {

  InstrumentationAdapter(int pAPI, ClassWriter pClassWriter) {
    super(pAPI, pClassWriter);
  }

  @Override
  public MethodVisitor visitMethod(
      int pAccess, String pName, String pDescriptor, String pSignature, String[] pExceptions) {
    MethodVisitor mv = super.visitMethod(pAccess, pName, pDescriptor, pSignature, pExceptions);
    return new MethodVisitor(api, mv) {
      @Override
      public void visitLineNumber(int pLine, Label pStart) {
        super.visitLineNumber(pLine, pStart);
        visitLdcInsn(pLine);
        visitMethodInsn(
                org.objectweb.asm.Opcodes.INVOKESTATIC,
                "de/uni_passau/fim/se2/sa/slicing/coverage/CoverageTracker",
                "trackLineVisit",
                "(I)V",
                false);
      }
    };
  }
}
