package com.buwan.shizuku.agent;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java Agent 入口：用 Instrumentation 在类加载期改写「读取血量」的方法。
 *
 * 两种挂载方式：
 * - premain：启动时通过 JVM 参数 -javaagent 挂载；
 * - agentmain：运行期用 VirtualMachine.attach 动态挂载（ tryAttachAgent ）。
 *
 * 用途：部分 boss / 附属 mod 把血量藏在 final 字段或自定义实现里，单靠反射拿不到、
 * 也改不动，只能在字节码层把 getHealth()/getMaxHealth() 的返回值换掉。
 *
 * 已知问题（上游行为，未在此版本修复）：attach 依赖同 JVM 的 attach API，某些启动器
 * （自带安全管理器 / 精简 JRE）会失败，此时 initialize() 只会记日志、功能降级为纯反射。
 */
public class AgentBootstrap {
    private static final AtomicBoolean agentLoaded = new AtomicBoolean(false);
    private static Instrumentation instrumentation = null;

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Shizuku] Agent \u6b63\u5728\u52a0\u8f7d...");
        instrumentation = inst;

        try {
            AgentHealthTransformer transformer = new AgentHealthTransformer();
            inst.addTransformer(transformer);
            System.out.println("[Shizuku] AgentHealthTransformer \u5df2\u6ce8\u518c");
            agentLoaded.set(true);
        } catch (Exception var3) {
            System.err.println("[Shizuku] Agent \u52a0\u8f7d\u5931\u8d25: " + var3.getMessage());
            var3.printStackTrace();
        }
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    public static boolean isAgentLoaded() {
        return agentLoaded.get();
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public static boolean isRetransformSupported() {
        Instrumentation inst = instrumentation;
        return inst != null && inst.isRetransformClassesSupported();
    }

    /**
     * JIT 层：对已加载的类强制重新转换。
     *
     * 这一步不是"再改一次字节码"这么简单 —— retransformClasses 会让 HotSpot
     * 把该类所有已编译的 nmethod 标记为 not-entrant（去优化），下一次调用从解释器
     * 重新跑并重新 JIT。也就是说，哪怕 LivingEntity.hurt 已经被 C2  aggressively
     * 内联过，这里也能把它拉回我们注入过的版本。
     *
     * 返回成功重新转换的类数量。
     */
    public static int retransform(Class<?>... classes) {
        Instrumentation inst = instrumentation;
        if (inst == null || classes == null || classes.length == 0) return 0;
        int done = 0;
        for (Class<?> type : classes) {
            if (type == null) continue;
            try {
                if (!inst.isModifiableClass(type)) continue;
                inst.retransformClasses(type);
                done++;
            } catch (Throwable failure) {
                System.err.println("[Shizuku] retransform 失败 " + type.getName() + ": " + failure);
            }
        }
        if (done > 0) System.out.println("[Shizuku] 已重新转换 " + done + " 个类（旧 JIT 代码已失效）");
        return done;
    }

    public static boolean tryAttachAgent() {
        try {
            if (isAgentLoaded()) {
                System.out.println("[Shizuku] Agent \u5df2\u52a0\u8f7d");
                return true;
            } else {
                Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
                if (vmClass == null) {
                    System.out.println("[Shizuku] VirtualMachine \u4e0d\u53ef\u7528\uff0c\u53ef\u80fd\u9700\u8981 -javaagent \u53c2\u6570");
                    return false;
                } else {
                    String pid = getProcessId();
                    if (pid == null) {
                        System.err.println("[Shizuku] \u65e0\u6cd5\u83b7\u53d6\u8fdb\u7a0bPID");
                        return false;
                    } else {
                        System.out.println("[Shizuku] \u5f53\u524d\u8fdb\u7a0bPID: " + pid);
                        Method attachMethod = vmClass.getMethod("attach", String.class);
                        Object vm = attachMethod.invoke(null, pid);
                        Method loadAgentMethod = vmClass.getMethod("loadAgent", String.class, String.class);
                        String agentPath = getAgentJarPath();
                        if (agentPath == null) {
                            System.err.println("[Shizuku] \u65e0\u6cd5\u83b7\u53d6Agent JAR\u8def\u5f84");
                            return false;
                        } else {
                            loadAgentMethod.invoke(vm, agentPath, "");
                            System.out.println("[Shizuku] Agent \u52a8\u6001\u9644\u52a0\u6210\u529f");
                            Method detachMethod = vmClass.getMethod("detach");
                            detachMethod.invoke(vm);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception var7) {
            System.err.println("[Shizuku] Agent \u52a8\u6001\u9644\u52a0\u5931\u8d25: " + var7.getMessage());
            var7.printStackTrace();
            return false;
        }
    }

    private static String getProcessId() {
        try {
            return Long.toString(ProcessHandle.current().pid());
        } catch (Exception var3) {
            try {
                String jvmName = ManagementFactory.getRuntimeMXBean().getName();
                return jvmName.split("@")[0];
            } catch (Exception var2) {
                return null;
            }
        }
    }

    private static String getAgentJarPath() {
        try {
            String path = AgentBootstrap.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            if (path != null && path.endsWith(".jar")) {
                return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            }
        } catch (Exception var1) {
            System.err.println("[Shizuku] \u83b7\u53d6Agent JAR\u8def\u5f84\u5931\u8d25: " + var1.getMessage());
        }

        return null;
    }

    public static void initialize() {
        System.out.println("[Shizuku] \u521d\u59cb\u5316Agent\u7cfb\u7edf...");
        if (isAgentLoaded()) {
            System.out.println("[Shizuku] Agent \u5df2\u52a0\u8f7d");
        } else {
            if (tryAttachAgent()) {
                System.out.println("[Shizuku] Agent \u52a8\u6001\u9644\u52a0\u6210\u529f");
            } else {
                System.out.println("[Shizuku] Agent \u52a8\u6001\u9644\u52a0\u5931\u8d25\uff0c\u8bf7\u4f7f\u7528 -javaagent \u53c2\u6570\u542f\u52a8");
                System.out.println("[Shizuku] \u793a\u4f8b: java -javaagent:shizuku-blade.jar -jar minecraft.jar");
            }
        }
    }
}
