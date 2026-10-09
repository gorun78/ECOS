package com.chinacreator.gzcm.gateway;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-17 — 业务包自建并发（含 llm-gateway 底座）验收命令命名投影。
 *
 * <p>F06-17 验收命令显式要求 {@code -Dtest='SelfScheduleArchTest#noExecutorFactoryNoRunAsyncNoRawThreadInBusiness'}。
 * 本类作为该命名入口的<b>独立投影</b>，与母规则
 * {@link ModuleDependencyArchTest#businessMustNotSelfSchedule()} 的 three 半区
 * （字节码 Executors 工厂 + 源码 new Thread( + 源码 CompletableFuture.runAsync/supplyAsync）
 * 保持同口径：同 businessPackages、同 {@code archunit/self-schedule-baseline.txt}（只减不增）、同
 * X-27 扩展检测面（含 {@code runtime/llm-gateway}）。改动任一侧都会同步红。</p>
 *
 * <p>X-26 特殊处理：本投影的字节码半区通过 importPaths 显式加 {@code runtime/llm-gateway/target/classes}，
 * 目录缺失即 FAIL（禁静默空宇宙）。真正的宇宙归属断言见
 * {@link ModuleDependencyArchTest#llmGatewayIsInsideTheUniverse()}。</p>
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='SelfScheduleArchTest'}</p>
 */
class SelfScheduleArchTest {

    /** businessPackages 与 ModuleDependencyArchTest.businessPackagesForScheduling() 对齐，另显式纳入 llm-gateway 底座。 */
    private static final String[] BUSINESS_PACKAGES = {
            "com.chinacreator.gzcm.engine.data..",
            "com.chinacreator.gzcm.engine.ontology..",
            "com.chinacreator.gzcm.engine.kb..",
            "com.chinacreator.gzcm.engine.cognitive2..",
            "com.chinacreator.gzcm.engine.ai..",
            "com.chinacreator.gzcm.services.sysman..",
            "com.chinacreator.gzcm.services.datanet..",
            "com.chinacreator.gzcm.services.buszhi..",
            "com.chinacreator.gzcm.services.dccheng..",
            "com.chinacreator.gzcm.services.aiming..",
            "com.chinacreator.gzcm.workspace..",
            // X-26：底座 llm-gateway 显式纳入（承接 X-25 底座内部并发收口）
            "com.chinacreator.gzcm.runtime.llm.."
    };

    /** 字节码半区候选 target/classes（含 runtime/llm-gateway）。 */
    private static final String[] TARGET_CLASSES = {
            "engine/data-engine/data-engine-impl/target/classes",
            "engine/ontology-engine/ontology-engine-impl/target/classes",
            "engine/kb-engine/kb-engine-impl/target/classes",
            "engine/cognitive-engine/cognitive-engine-impl/target/classes",
            "engine/ai-engine/ai-engine-impl/target/classes",
            "services/sysman/impl/sysman-impl/target/classes",
            "services/datanet/target/classes",
            "services/buszhi/impl/buszhi-impl/target/classes",
            "services/dccheng/target/classes",
            "services/aiming/target/classes",
            "workspace/workspace-impl/target/classes",
            "workspace/workspace-service/target/classes",
            "runtime/llm-gateway/target/classes",
            "gateway/target/classes"
    };

    /** 源码半区扫描根（含 runtime/llm-gateway，承接 X-26 使底座并发进入源码面）。 */
    private static final String[] SOURCE_ROOTS = {
            "engine", "services", "workspace", "gateway", "runtime/llm-gateway"
    };

    private static final Set<String> SELF_SCHEDULE_BASELINE =
            loadBaseline("archunit/self-schedule-baseline.txt");

    private static Path backendRoot;

    @BeforeAll
    static void setUp() {
        Path cur = Paths.get("").toAbsolutePath();
        backendRoot = null;
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml")) && Files.exists(cur.resolve("gateway"))) {
                backendRoot = cur;
                break;
            }
            cur = cur.getParent();
        }
        assertTrue(backendRoot != null, "backend root 未定位 — 需从 gateway 模块 test 上下文执行");
        System.out.println("[SelfScheduleArch] baseline entries: " + SELF_SCHEDULE_BASELINE.size());
    }

    @Test
    @DisplayName("F06-17/X-24~X-28 命名入口：Executors 工厂 + runAsync/supplyAsync + 裸 Thread 面联合断言（含 llm-gateway）")
    void noExecutorFactoryNoRunAsyncNoRawThreadInBusiness() {
        // 半区 1：字节码 Executors 工厂（llm-gateway 显式入宇宙 —— X-26 缺失则直接 FAIL）。
        JavaClasses classes = importUniverse();
        DescribedPredicate<JavaClass> notInBaseline =
                new DescribedPredicate<JavaClass>("不在 self-schedule-baseline.txt") {
                    @Override
                    public boolean test(JavaClass input) {
                        return !inBaseline(SELF_SCHEDULE_BASELINE, input.getName());
                    }
                };
        noClasses().that().resideInAnyPackage(BUSINESS_PACKAGES)
                .and(notInBaseline)
                .should().dependOnClassesThat(
                        new DescribedPredicate<JavaClass>("Executors 工厂类") {
                            @Override
                            public boolean test(JavaClass input) {
                                return "java.util.concurrent.Executors".equals(input.getName());
                            }
                        })
                .allowEmptyShould(true)
                .check(classes);

        // 半区 2：源码 literal 扫描（剥注释）—— new Thread( / CompletableFuture.runAsync / .supplyAsync。
        List<String> offenders = new ArrayList<>();
        List<Path> roots = new ArrayList<>();
        for (String rel : SOURCE_ROOTS) {
            Path p = backendRoot.resolve(rel).normalize();
            if (Files.isDirectory(p)) {
                roots.add(p);
            }
        }
        for (String literal : new String[]{"new Thread(", "CompletableFuture.runAsync(",
                "CompletableFuture.supplyAsync("}) {
            for (String hit : scanForLiteral(roots, literal, true)) {
                String filePart = hit.substring(0, hit.indexOf(':')).replace('\\', '/');
                if (!filePart.contains("/src/main/java/")
                        || filePart.contains("/archive/") || filePart.contains("/legacy/")
                        || filePart.contains("/config/")) {
                    continue;
                }
                String fqcn = sourcePathToFqcn(filePart);
                if (fqcn != null && inBaseline(SELF_SCHEDULE_BASELINE, fqcn)) {
                    continue;
                }
                offenders.add("[E/" + literal + "] " + hit);
            }
        }
        assertTrue(offenders.isEmpty(),
                "F06-17 X-25/X-27 业务源码存在自建并发（基线外，含 runtime.llm）：\n"
                        + String.join("\n", offenders));
    }

    private JavaClasses importUniverse() {
        Path llmGw = backendRoot.resolve("runtime/llm-gateway/target/classes").normalize();
        assertTrue(Files.isDirectory(llmGw),
                "X-26 未达：runtime/llm-gateway/target/classes 缺失。请 mvn -pl runtime/llm-gateway install 后重跑。");
        List<Path> paths = new ArrayList<>();
        for (String rel : TARGET_CLASSES) {
            Path full = backendRoot.resolve(rel).normalize();
            if (Files.isDirectory(full)) {
                paths.add(full);
            }
        }
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPaths(paths.toArray(new Path[0]));
        assertTrue(imported.size() > 0, "SelfScheduleArchTest: import 宇宙为空");
        return imported;
    }

    // ── 与 ModuleDependencyArchTest 同实现的工具（投影自持，母规则改动不同步即可独立红） ──
    private static boolean inBaseline(Set<String> baseline, String fqcn) {
        if (baseline.contains(fqcn)) {
            return true;
        }
        for (String entry : baseline) {
            if (fqcn.startsWith(entry + "$")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> loadBaseline(String resource) {
        Set<String> out = new HashSet<>();
        try (InputStream in = SelfScheduleArchTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(in != null, "缺少基线 gateway/src/test/resources/" + resource);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) {
                        out.add(t);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("基线读取失败 " + resource, e);
        }
        return out;
    }

    /** {@code .../src/main/java/a/b/C.java} → FQCN；非 src/main/java 返回 null（与母规则同实现）。 */
    private static String sourcePathToFqcn(String path) {
        String s = path.replace('\\', '/');
        int idx = s.indexOf("/src/main/java/");
        if (idx < 0 || !s.endsWith(".java")) {
            return null;
        }
        return s.substring(idx + "/src/main/java/".length(), s.length() - ".java".length())
                .replace('/', '.');
    }

    /** 与 ModuleDependencyArchTest.scanForLiteral 同实现（剥注释 literal 扫描，返回值 file:line | line）。 */
    private static List<String> scanForLiteral(List<Path> roots, String literal, boolean skipCommentLines) {
        List<String> found = new ArrayList<>();
        for (Path root : roots) {
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"))
                     .forEach(p -> {
                         try {
                             List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                             boolean inBlockComment = false;
                             for (int i = 1; i <= lines.size(); i++) {
                                 String line = lines.get(i - 1);
                                 String trimmed = line.strip();
                                 if (skipCommentLines) {
                                     if (!inBlockComment && trimmed.startsWith("/*")) {
                                         if (!(trimmed.contains("*/"))) {
                                             inBlockComment = true;
                                         }
                                         continue;
                                     }
                                     if (inBlockComment) {
                                         if (trimmed.contains("*/")) {
                                             inBlockComment = false;
                                         }
                                         continue;
                                     }
                                     if (trimmed.startsWith("*") || trimmed.startsWith("//")) {
                                         continue;
                                     }
                                 }
                                 if (line.contains(literal)) {
                                     found.add(root.relativize(p) + ":" + i + " | " + line.trim());
                                 }
                             }
                         } catch (IOException ex) {
                             // 单文件读失败不上抛
                         }
                     });
            } catch (IOException e) {
                // 根目录不可读 — 视为无内容
            }
        }
        return found;
    }
}
