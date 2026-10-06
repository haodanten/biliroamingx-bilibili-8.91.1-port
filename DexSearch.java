import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 宿主 dex 检索工具 (适配 BiliRoamingX 指纹用)。
 *
 * 用法:
 *   java DexSearch <apk> <mode> [args...]
 *
 * mode = str    <substring> [classFilter]     找出含该字符串常量的方法
 * mode = cls    <classFilter>                 列出匹配的类及其方法签名
 * mode = ref    <substring> [classFilter]     找出引用了该类型/方法的方法
 */
public class DexSearch {

    static final List<ClassDef> allClasses = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        File apk = new File(args[0]);
        String mode = args[1];
        String needle = args.length > 2 ? args[2] : null;
        String classFilter = args.length > 3 ? args[3] : null;
        // cls 模式只有一个参数, 让它当类名过滤用 (sub/fld 的 needle 含义不同, 不能借用)
        if (classFilter == null && "cls".equals(mode)) {
            classFilter = needle;
        }

        MultiDexContainer<? extends DexFile> container =
                DexFileFactory.loadDexContainer(apk, Opcodes.forApi(35));
        for (String entry : container.getDexEntryNames()) {
            DexFile dex = container.getEntry(entry).getDexFile();
            allClasses.addAll(dex.getClasses());
        }
        System.err.println("[DexSearch] loaded classes: " + allClasses.size());

        int hits = 0;
        for (ClassDef cls : allClasses) {
            if (classFilter != null && !cls.getType().contains(classFilter)) continue;

            if ("cls".equals(mode)) {
                System.out.println("CLASS " + cls.getType() + " super=" + cls.getSuperclass()
                        + " impl=" + cls.getInterfaces() + " access=0x" + Integer.toHexString(cls.getAccessFlags()));
                for (Method m : cls.getMethods()) {
                    System.out.println("   M " + m.getName() + "(" + join(m.getParameterTypes()) + ")"
                            + m.getReturnType() + " access=0x" + Integer.toHexString(m.getAccessFlags()));
                }
                continue;
            }

            if ("sub".equals(mode)) {
                if (!needle.equals(cls.getSuperclass())) continue;
                System.out.println("CLASS " + cls.getType() + " super=" + cls.getSuperclass()
                        + " access=0x" + Integer.toHexString(cls.getAccessFlags()));
                for (com.android.tools.smali.dexlib2.iface.Field f : cls.getFields()) {
                    System.out.println("   F " + f.getName() + ":" + f.getType()
                            + "  ann=" + annStr(f.getAnnotations()));
                }
                for (Method m : cls.getMethods()) {
                    System.out.println("   M " + m.getName() + "(" + join(m.getParameterTypes()) + ")"
                            + m.getReturnType() + " access=0x" + Integer.toHexString(m.getAccessFlags()));
                }
                continue;
            }

            if ("fld".equals(mode)) {
                for (com.android.tools.smali.dexlib2.iface.Field f : cls.getFields()) {
                    String as = annStr(f.getAnnotations());
                    if (as.contains(needle)) {
                        System.out.println("FIELD " + cls.getType() + "->" + f.getName() + ":" + f.getType()
                                + "  ann=" + as);
                    }
                }
                continue;
            }

            if ("insn".equals(mode)) {
                // insn <方法名正则> [类名过滤] —— 打印全部指令(含索引), 用于核对指令模式
                for (Method m : cls.getMethods()) {
                    if (needle != null && !m.getName().matches(needle)) continue;
                    MethodImplementation impl = m.getImplementation();
                    System.out.println("METHOD " + cls.getType() + "->" + m.getName()
                            + "(" + join(m.getParameterTypes()) + ")" + m.getReturnType()
                            + " access=0x" + Integer.toHexString(m.getAccessFlags())
                            + " regs=" + (impl == null ? "-" : impl.getRegisterCount()));
                    if (impl == null) continue;
                    int i = 0;
                    int[] unitStart = new int[1024];
                    int unit = 0;
                    java.util.List<Instruction> insnList = new java.util.ArrayList<>();
                    for (Instruction ins : impl.getInstructions()) { insnList.add(ins); }
                    for (Instruction ins : insnList) {
                        if (i >= unitStart.length) break;
                        unitStart[i++] = unit;
                        unit += ins.getCodeUnits();
                    }
                    java.util.Map<Integer,Integer> unitToIndex = new java.util.HashMap<>();
                    for (int k = 0; k < insnList.size(); k++) unitToIndex.put(unitStart[k], k);
                    i = 0;
                    for (Instruction ins : insnList) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("    %4d  %s", i++, ins.toString()));
                        if (ins instanceof com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction wli) {
                            long wl = wli.getWideLiteral();
                            sb.append(String.format("  #lit=0x%x (f=%s)", wl, Float.intBitsToFloat((int) wl)));
                        }
                        if (ins instanceof ReferenceInstruction ri) sb.append("  ").append(ri.getReference());
                        if (ins instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n x) {
                            sb.append("  v").append(x.getRegisterA()).append(", #").append(x.getNarrowLiteral());
                        } else if (ins instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c x) {
                            sb.append("  v").append(x.getRegisterA()).append(", v").append(x.getRegisterB());
                        } else if (ins instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c x) {
                            sb.append("  {v").append(x.getRegisterC()).append(",v").append(x.getRegisterD())
                              .append(",v").append(x.getRegisterE()).append(",v").append(x.getRegisterF())
                              .append(",v").append(x.getRegisterG()).append("}");
                        } else if (ins instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c x) {
                            sb.append("  v").append(x.getRegisterA());
                        } else if (ins instanceof com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction x) {
                            sb.append("  v").append(x.getRegisterA());
                        }
                        if (ins instanceof com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction oi) {
                            int tgt = unitToIndex.getOrDefault(unitStart[i - 1] + oi.getCodeOffset(), -1);
                            sb.append("  -> ").append(tgt);
                        }
                        System.out.println(sb);
                    }
                }
                continue;
            }

            for (Method m : cls.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) continue;
                List<String> body = new ArrayList<>();
                boolean matched = false;
                for (Instruction ins : impl.getInstructions()) {
                    if (!(ins instanceof ReferenceInstruction ri)) continue;
                    Reference r = ri.getReference();
                    String line = null;
                    if (r instanceof StringReference sr) {
                        line = "    STR \"" + sr.getString() + "\"";
                        if ("str".equals(mode) && sr.getString().contains(needle)) matched = true;
                    } else if (r instanceof MethodReference mr) {
                        line = "    INV " + mr.getDefiningClass() + "->" + mr.getName()
                                + "(" + join(mr.getParameterTypes()) + ")" + mr.getReturnType();
                        if ("ref".equals(mode) && line.contains(needle)) matched = true;
                    } else if (r instanceof FieldReference fr) {
                        line = "    FLD " + fr.getDefiningClass() + "->" + fr.getName() + ":" + fr.getType();
                        if ("ref".equals(mode) && line.contains(needle)) matched = true;
                    }
                    if (line != null) body.add(line);
                }
                if ("cls".equals(mode)) matched = true;
                if (!matched) continue;
                hits++;
                System.out.println("METHOD " + cls.getType() + "->" + m.getName()
                        + "(" + join(m.getParameterTypes()) + ")" + m.getReturnType()
                        + " access=0x" + Integer.toHexString(m.getAccessFlags()));
                for (String l : body) System.out.println(l);
            }
        }
        System.err.println("[DexSearch] method hits: " + hits);
    }

    static String join(Iterable<? extends CharSequence> it) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence c : it) sb.append(c);
        return sb.toString();
    }

    /** 把注解渲染成可读文本, 例如 @Lcom/...JSONField;(name="banner_item") */
    static String annStr(java.util.Set<? extends com.android.tools.smali.dexlib2.iface.Annotation> anns) {
        if (anns == null || anns.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (com.android.tools.smali.dexlib2.iface.Annotation a : anns) {
            sb.append('@').append(a.getType()).append('(');
            for (com.android.tools.smali.dexlib2.iface.AnnotationElement e : a.getElements()) {
                sb.append(e.getName()).append('=');
                com.android.tools.smali.dexlib2.iface.value.EncodedValue v = e.getValue();
                if (v instanceof com.android.tools.smali.dexlib2.iface.value.StringEncodedValue s) {
                    sb.append('"').append(s.getValue()).append('"');
                } else {
                    sb.append(v);
                }
                sb.append(',');
            }
            sb.append(") ");
        }
        return sb.append(']').toString();
    }
}
