package de.uni_passau.fim.se2.sa.slicing.instrumentation;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class LineCoverageTransformer implements ClassFileTransformer {

  private final String instrumentationTarget;

  public LineCoverageTransformer(String pInstrumentationTarget) {
    instrumentationTarget = pInstrumentationTarget.replace('.', '/');
  }

  @Override
  public byte[] transform(
      ClassLoader pClassLoader,
      String pClassName,
      Class<?> pClassBeingRedefined,
      ProtectionDomain pProtectionDomain,
      byte[] pClassFileBuffer) {
    if (isIgnored(pClassName)) {
      return pClassFileBuffer;
    }
    try {
      ClassReader classReader = new ClassReader(pClassFileBuffer);

      ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

      InstrumentationAdapter instrumentationAdapter =
              new InstrumentationAdapter(Opcodes.ASM9, classWriter);

      classReader.accept(instrumentationAdapter, 0);

      return classWriter.toByteArray();

    } catch (Exception e) {
      e.printStackTrace();
      return pClassFileBuffer;
    }
  }

  private boolean isIgnored(String pClassName) {
    return !pClassName.startsWith(instrumentationTarget) || pClassName.endsWith("Test");
  }
}
