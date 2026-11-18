import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.internal.analysis.ClassAnalyzer;
import org.jacoco.core.internal.analysis.ClassCoverageImpl;
import org.jacoco.core.internal.analysis.StringPool;
import org.jacoco.core.internal.analysis.filter.Filters;
import org.jacoco.core.internal.analysis.filter.IFilter;
import org.jacoco.core.internal.data.CRC64;
import org.jacoco.core.internal.flow.ClassProbesAdapter;
import org.jacoco.core.internal.instr.InstrSupport;
import org.jacoco.core.tools.ExecFileLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/**
 * Each source line's branches as JaCoCo counts them, without the branches that the Compose
 * compiler adds. It runs JaCoCo's own analysis of the classes and the coverage data, with one
 * more filter. That filter leaves out each conditional jump whose condition comes only from the
 * Composer (changed, rememberedValue, skipping, tracing), the compiler's masks ($changed,
 * $default, $dirty) and constants. Such jumps skip a recomposition or reuse a remembered value or
 * lambda: they say nothing about what the code does, and no test of it can reach them all.
 *
 * It uses JaCoCo's internal analysis classes, so it must match the JaCoCo version of the build.
 *
 * Usage: java -cp JACOCO_AND_ASM_JARS ComposeBranches.java CLASSES_DIR COVERAGE.exec
 * Prints "package/File.kt LINE MISSED COVERED" for each line with branches.
 */
public class ComposeBranches {
    private static final String COMPOSE_RUNTIME = "androidx/compose/runtime/Composer";

    public static void main(String[] args) throws Exception {
        ExecFileLoader loader = new ExecFileLoader();
        loader.load(new File(args[1]));
        ExecutionDataStore data = loader.getExecutionDataStore();
        CoverageBuilder builder = new CoverageBuilder();
        StringPool strings = new StringPool();
        try (Stream<Path> paths = Files.walk(Path.of(args[0]))) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".class"))::iterator) {
                analyze(Files.readAllBytes(path), data, builder, strings);
            }
        }
        for (ISourceFileCoverage file : builder.getSourceFiles()) {
            for (int nr = file.getFirstLine(); nr <= file.getLastLine() && nr > 0; nr++) {
                ICounter branches = file.getLine(nr).getBranchCounter();
                if (branches.getTotalCount() == 0) continue;
                String name = file.getPackageName().isEmpty() ? file.getName() : file.getPackageName() + "/" + file.getName();
                System.out.println(name + " " + nr + " " + branches.getMissedCount() + " " + branches.getCoveredCount());
            }
        }
    }

    /** As JaCoCo's Analyzer does it, with [composeFilter] after JaCoCo's own filters. */
    private static void analyze(byte[] bytes, ExecutionDataStore data, CoverageBuilder builder, StringPool strings) throws Exception {
        ClassReader reader = InstrSupport.classReaderFor(bytes);
        if ((reader.getAccess() & (Opcodes.ACC_MODULE | Opcodes.ACC_SYNTHETIC)) != 0) return;
        long id = CRC64.classId(bytes);
        String name = reader.getClassName();
        ExecutionData found = data.get(id);
        boolean[] probes = found == null ? null : found.getProbes();
        boolean noMatch = found == null && data.contains(name);
        ClassCoverageImpl coverage = new ClassCoverageImpl(name, id, noMatch);
        ClassAnalyzer analyzer = new ClassAnalyzer(coverage, probes, strings) {
            @Override
            public void visitEnd() {
                super.visitEnd();
                builder.visitCoverage(coverage);
            }
        };
        Field filter = ClassAnalyzer.class.getDeclaredField("filter");
        filter.setAccessible(true);
        IFilter jacoco = Filters.all();
        filter.set(analyzer, (IFilter) (method, context, output) -> {
            jacoco.filter(method, context, output);
            for (AbstractInsnNode jump : composeJumps(context.getClassName(), method)) output.ignore(jump, jump);
        });
        reader.accept(new ClassProbesAdapter(analyzer, false), 0);
    }

    /** The conditional jumps in [method] that the Compose compiler added. */
    static List<AbstractInsnNode> composeJumps(String owner, MethodNode method) {
        List<AbstractInsnNode> jumps = new ArrayList<>();
        if (method.instructions.size() == 0) return jumps;
        Frame<SourceValue>[] frames;
        try {
            frames = new Analyzer<>(new SourceInterpreter()).analyze(owner, method);
        } catch (AnalyzerException e) {
            return jumps;
        }
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode insn = method.instructions.get(i);
            int operands = operands(insn);
            if (operands == 0 || frames[i] == null) continue;
            Leaves leaves = new Leaves();
            for (int k = 0; k < operands; k++) {
                trace(frames[i].getStack(frames[i].getStackSize() - 1 - k), method, frames, leaves, 0);
            }
            if (leaves.compose && !leaves.other) jumps.add(insn);
        }
        return jumps;
    }

    /** The values a conditional jump tests: 1 or 2. Other instructions test none. */
    private static int operands(AbstractInsnNode insn) {
        if (!(insn instanceof JumpInsnNode)) return 0;
        int op = insn.getOpcode();
        if (op >= Opcodes.IFEQ && op <= Opcodes.IFLE || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL) return 1;
        if (op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ACMPNE) return 2;
        return 0;
    }

    /** What a value is made of: something from Compose, something else, or only constants. */
    private static final class Leaves {
        boolean compose;
        boolean other;
    }

    private static void trace(SourceValue value, MethodNode method, Frame<SourceValue>[] frames, Leaves leaves, int depth) {
        if (leaves.other) return;
        if (depth > 40 || value.insns.isEmpty()) {
            leaves.other = true;
            return;
        }
        for (AbstractInsnNode insn : value.insns) traceInsn(insn, method, frames, leaves, depth + 1);
    }

    private static void traceInsn(AbstractInsnNode insn, MethodNode method, Frame<SourceValue>[] frames, Leaves leaves, int depth) {
        int op = insn.getOpcode();
        int index = method.instructions.indexOf(insn);
        Frame<SourceValue> frame = frames[index];
        if (insn instanceof MethodInsnNode call) {
            if (call.owner.startsWith(COMPOSE_RUNTIME)) leaves.compose = true;
            else leaves.other = true;
        } else if (insn instanceof FieldInsnNode field) {
            if (op == Opcodes.GETSTATIC && field.owner.startsWith(COMPOSE_RUNTIME)) leaves.compose = true;
            else leaves.other = true;
        } else if (insn instanceof VarInsnNode variable) {
            if (compilerLocal(method, variable.var, index)) {
                leaves.compose = true;
            } else if (op >= Opcodes.ILOAD && op <= Opcodes.ALOAD && frame != null) {
                // Whatever was stored in it: a parameter has nothing stored, and counts as other.
                trace(frame.getLocal(variable.var), method, frames, leaves, depth);
            } else if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE && frame != null) {
                trace(frame.getStack(frame.getStackSize() - 1), method, frames, leaves, depth);
            } else {
                leaves.other = true;
            }
        } else if (insn instanceof IincInsnNode) {
            leaves.other = true;
        } else if (insn instanceof LdcInsnNode ldc) {
            if (!(ldc.cst instanceof Integer)) leaves.other = true;
        } else if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5 || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH) {
            // A constant: neither from Compose nor from the code's own data.
        } else if (frame != null && (arithmetic(op) || op == Opcodes.DUP || op == Opcodes.CHECKCAST)) {
            int inputs = arithmetic(op) ? 2 : 1;
            for (int k = 0; k < inputs; k++) trace(frame.getStack(frame.getStackSize() - 1 - k), method, frames, leaves, depth);
        } else {
            leaves.other = true;
        }
    }

    private static boolean arithmetic(int op) {
        return op == Opcodes.IAND || op == Opcodes.IOR || op == Opcodes.IXOR || op == Opcodes.ISHL || op == Opcodes.ISHR
            || op == Opcodes.IUSHR || op == Opcodes.IADD || op == Opcodes.ISUB || op == Opcodes.IMUL;
    }

    /** The Compose compiler's own masks of what changed and which arguments were left out. */
    private static boolean compilerLocal(MethodNode method, int slot, int at) {
        if (method.localVariables == null) return false;
        List<LocalVariableNode> named = new ArrayList<>();
        for (LocalVariableNode local : method.localVariables) {
            if (local.index != slot) continue;
            int start = method.instructions.indexOf(local.start);
            int end = method.instructions.indexOf(local.end);
            // A store comes just before its variable's range starts.
            if (at >= start - 2 && at <= end) named.add(local);
        }
        return !named.isEmpty() && named.stream().allMatch(l -> l.name.matches("\\$(changed|default|dirty)\\d*"));
    }
}
