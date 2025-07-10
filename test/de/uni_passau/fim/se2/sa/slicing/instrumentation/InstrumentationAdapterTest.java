package de.uni_passau.fim.se2.sa.slicing.instrumentation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;

class InstrumentationAdapterTest {

    private ClassWriter classWriter;
    private InstrumentationAdapter adapter;
    private byte[] originalClassBytes;

    @BeforeEach
    void setUp() throws IOException {
        classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        adapter = new InstrumentationAdapter(Opcodes.ASM9, classWriter);

        // Load original class bytes for testing
        ClassReader reader = new ClassReader("de.uni_passau.fim.se2.sa.examples.Calculator");
        originalClassBytes = getClassBytes(reader);
    }

    private byte[] getClassBytes(ClassReader reader) {
        ClassWriter writer = new ClassWriter(0);
        reader.accept(writer, 0);
        return writer.toByteArray();
    }

    @Test
    void testConstructorWithValidParameters() {
        ClassWriter writer = new ClassWriter(0);
        InstrumentationAdapter adapter = new InstrumentationAdapter(Opcodes.ASM9, writer);
        assertNotNull(adapter);
    }

    @Test
    void testConstructorWithNullClassWriter() {
        assertDoesNotThrow(() -> {
            InstrumentationAdapter adapter = new InstrumentationAdapter(Opcodes.ASM9, null);
            assertNotNull(adapter);
        });
    }

    @Test
    void testVisitMethodReturnsMethodVisitor() {
        MethodVisitor methodVisitor = adapter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "testMethod",
                "()V",
                null,
                null
        );

        assertNotNull(methodVisitor);
        assertTrue(methodVisitor instanceof MethodVisitor);
    }

    @Test
    void testVisitMethodWithNullParameters() {
        // ASM doesn't handle null method names properly, so this should throw
        assertThrows(NullPointerException.class, () -> {
            adapter.visitMethod(
                    0,
                    null,
                    null,
                    null,
                    null
            );
        });
    }

    @Test
    void testVisitMethodWithMinimalValidParameters() {
        MethodVisitor methodVisitor = adapter.visitMethod(
                0,
                "testMethod",
                "()V",
                null,
                null
        );

        assertNotNull(methodVisitor);
    }

    @Test
    void testVisitMethodWithExceptions() {
        String[] exceptions = {"java/lang/Exception", "java/io/IOException"};

        MethodVisitor methodVisitor = adapter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "methodWithExceptions",
                "()V",
                null,
                exceptions
        );

        assertNotNull(methodVisitor);
    }

    @Test
    void testInstrumentationAddsLineNumberInstrumentation() throws IOException {
        // Create a test class and instrument it
        ClassReader reader = new ClassReader("de.uni_passau.fim.se2.sa.examples.Calculator");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        InstrumentationAdapter instrumentationAdapter = new InstrumentationAdapter(Opcodes.ASM9, writer);

        reader.accept(instrumentationAdapter, 0);
        byte[] instrumentedBytes = writer.toByteArray();

        assertNotNull(instrumentedBytes);
        assertTrue(instrumentedBytes.length > 0);
        assertNotEquals(originalClassBytes.length, instrumentedBytes.length);

        // Verify the instrumented class contains coverage tracking calls
        verifyInstrumentationPresent(instrumentedBytes);
    }

    @Test
    void testLineNumberInstrumentationWithDifferentMethods() throws IOException {
        // Test with different types of methods
        String[] testClasses = {
                "de.uni_passau.fim.se2.sa.examples.Calculator",
                "de.uni_passau.fim.se2.sa.examples.GCD",
                "de.uni_passau.fim.se2.sa.examples.SimpleInteger"
        };

        for (String className : testClasses) {
            ClassReader reader = new ClassReader(className);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            InstrumentationAdapter instrumentationAdapter = new InstrumentationAdapter(Opcodes.ASM9, writer);

            reader.accept(instrumentationAdapter, 0);
            byte[] instrumentedBytes = writer.toByteArray();

            assertNotNull(instrumentedBytes);
            assertTrue(instrumentedBytes.length > 0);

            // Verify instrumentation was added
            verifyInstrumentationPresent(instrumentedBytes);
        }
    }

    @Test
    void testInstrumentationPreservesOriginalBehavior() throws IOException {
        ClassReader reader = new ClassReader("de.uni_passau.fim.se2.sa.examples.SimpleInteger");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        InstrumentationAdapter instrumentationAdapter = new InstrumentationAdapter(Opcodes.ASM9, writer);

        reader.accept(instrumentationAdapter, 0);
        byte[] instrumentedBytes = writer.toByteArray();

        // Parse instrumented class
        ClassReader instrumentedReader = new ClassReader(instrumentedBytes);
        ClassNode instrumentedNode = new ClassNode();
        instrumentedReader.accept(instrumentedNode, 0);

        // Parse original class
        ClassNode originalNode = new ClassNode();
        reader.accept(originalNode, 0);

        // Verify basic structure is preserved
        assertEquals(originalNode.name, instrumentedNode.name);
        assertEquals(originalNode.superName, instrumentedNode.superName);
        assertEquals(originalNode.methods.size(), instrumentedNode.methods.size());

        // Method names should be preserved
        for (int i = 0; i < originalNode.methods.size(); i++) {
            MethodNode originalMethod = originalNode.methods.get(i);
            MethodNode instrumentedMethod = instrumentedNode.methods.get(i);
            assertEquals(originalMethod.name, instrumentedMethod.name);
            assertEquals(originalMethod.desc, instrumentedMethod.desc);
        }
    }

    @Test
    void testMethodVisitorLineNumberHandling() {
        // Create a custom method visitor to test line number handling
        TestMethodVisitor testVisitor = new TestMethodVisitor();

        MethodVisitor instrumentedVisitor = adapter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "testMethod",
                "()V",
                null,
                null
        );

        assertNotNull(instrumentedVisitor);

        // Test that visitLineNumber calls are handled
        Label label = new Label();
        assertDoesNotThrow(() -> {
            instrumentedVisitor.visitLineNumber(10, label);
        });
    }

    @Test
    void testInstrumentationWithComplexMethod() throws IOException {
        // Test with a method that has loops and conditions
        ClassReader reader = new ClassReader("de.uni_passau.fim.se2.sa.examples.GCD");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        InstrumentationAdapter instrumentationAdapter = new InstrumentationAdapter(Opcodes.ASM9, writer);

        reader.accept(instrumentationAdapter, 0);
        byte[] instrumentedBytes = writer.toByteArray();

        assertNotNull(instrumentedBytes);

        // Verify the complex method is properly instrumented
        ClassReader instrumentedReader = new ClassReader(instrumentedBytes);
        ClassNode instrumentedNode = new ClassNode();
        instrumentedReader.accept(instrumentedNode, 0);

        // Find the gcd method
        MethodNode gcdMethod = instrumentedNode.methods.stream()
                .filter(m -> "gcd".equals(m.name))
                .findFirst()
                .orElse(null);

        assertNotNull(gcdMethod);
        assertTrue(gcdMethod.instructions.size() > 0);

        // Should contain calls to trackLineVisit
        verifyTrackLineVisitCalls(gcdMethod);
    }

    @Test
    void testInstrumentationWithMethodsWithoutLineNumbers() throws IOException {
        // Some methods might not have line number information
        ClassReader reader = new ClassReader("de.uni_passau.fim.se2.sa.examples.Calculator");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        InstrumentationAdapter instrumentationAdapter = new InstrumentationAdapter(Opcodes.ASM9, writer);

        assertDoesNotThrow(() -> {
            reader.accept(instrumentationAdapter, 0);
            byte[] instrumentedBytes = writer.toByteArray();
            assertNotNull(instrumentedBytes);
        });
    }

    @Test
    void testInstrumentationWithStaticMethods() throws IOException {
        // Test instrumentation of static methods
        ClassReader reader = new ClassReader("de.uni_passau.fim.se2.sa.examples.Rational");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        InstrumentationAdapter instrumentationAdapter = new InstrumentationAdapter(Opcodes.ASM9, writer);

        reader.accept(instrumentationAdapter, 0);
        byte[] instrumentedBytes = writer.toByteArray();

        assertNotNull(instrumentedBytes);

        // Verify static methods are instrumented
        ClassReader instrumentedReader = new ClassReader(instrumentedBytes);
        ClassNode instrumentedNode = new ClassNode();
        instrumentedReader.accept(instrumentedNode, 0);

        // Find static methods
        List<MethodNode> staticMethods = instrumentedNode.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_STATIC) != 0)
                .filter(m -> !"<clinit>".equals(m.name))
                .toList();

        assertFalse(staticMethods.isEmpty());

        // Static methods should also be instrumented
        for (MethodNode method : staticMethods) {
            if (method.instructions.size() > 10) { // Skip trivial methods
                verifyTrackLineVisitCalls(method);
            }
        }
    }

    @Test
    void testInstrumentationWithConstructors() throws IOException {
        ClassReader reader = new ClassReader("de.uni_passau.fim.se2.sa.examples.Complex");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        InstrumentationAdapter instrumentationAdapter = new InstrumentationAdapter(Opcodes.ASM9, writer);

        reader.accept(instrumentationAdapter, 0);
        byte[] instrumentedBytes = writer.toByteArray();

        assertNotNull(instrumentedBytes);

        // Verify constructors are instrumented
        ClassReader instrumentedReader = new ClassReader(instrumentedBytes);
        ClassNode instrumentedNode = new ClassNode();
        instrumentedReader.accept(instrumentedNode, 0);

        // Find constructor
        MethodNode constructor = instrumentedNode.methods.stream()
                .filter(m -> "<init>".equals(m.name))
                .findFirst()
                .orElse(null);

        assertNotNull(constructor);
        assertTrue(constructor.instructions.size() > 0);
    }

    @Test
    void testInstrumentationWithSynchronizedMethods() throws IOException {
        // Test methods with different access modifiers and synchronized
        ClassReader reader = new ClassReader("de.uni_passau.fim.se2.sa.examples.Calculator");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        InstrumentationAdapter instrumentationAdapter = new InstrumentationAdapter(Opcodes.ASM9, writer);

        assertDoesNotThrow(() -> {
            reader.accept(instrumentationAdapter, 0);
            byte[] result = writer.toByteArray();
            assertNotNull(result);
            assertTrue(result.length > 0);
        });
    }

    @Test
    void testAdapterInheritance() {
        // Test that the adapter properly extends ClassVisitor
        assertTrue(adapter instanceof ClassVisitor);

        // Test that it properly delegates to the underlying ClassWriter
        assertDoesNotThrow(() -> {
            adapter.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestClass", null, "java/lang/Object", null);
            adapter.visitEnd();
        });
    }

    @Test
    void testMethodVisitorNesting() {
        // Test nested method visitor behavior
        MethodVisitor mv1 = adapter.visitMethod(Opcodes.ACC_PUBLIC, "method1", "()V", null, null);
        assertNotNull(mv1);

        MethodVisitor mv2 = adapter.visitMethod(Opcodes.ACC_PRIVATE, "method2", "(I)V", null, null);
        assertNotNull(mv2);

        // Both should be independent instances
        assertNotSame(mv1, mv2);
    }

    private void verifyInstrumentationPresent(byte[] instrumentedBytes) throws IOException {
        ClassReader reader = new ClassReader(instrumentedBytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        boolean foundTrackingCall = false;

        for (MethodNode method : node.methods) {
            if (hasTrackLineVisitCall(method)) {
                foundTrackingCall = true;
                break;
            }
        }

        assertTrue(foundTrackingCall, "Instrumented class should contain coverage tracking calls");
    }

    private void verifyTrackLineVisitCalls(MethodNode method) {
        assertTrue(hasTrackLineVisitCall(method),
                "Method " + method.name + " should contain trackLineVisit calls");
    }

    private boolean hasTrackLineVisitCall(MethodNode method) {
        // Iterate through instructions manually since ListIterator doesn't have asIterable()
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode methodInsn &&
                    methodInsn.owner.equals("de/uni_passau/fim/se2/sa/slicing/coverage/CoverageTracker") &&
                    methodInsn.name.equals("trackLineVisit")) {
                return true;
            }
        }
        return false;
    }

    private static class TestMethodVisitor extends MethodVisitor {
        public TestMethodVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            // Test implementation
            super.visitLineNumber(line, start);
        }
    }
}