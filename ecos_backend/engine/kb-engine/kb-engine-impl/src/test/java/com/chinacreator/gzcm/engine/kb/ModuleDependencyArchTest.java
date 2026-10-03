package com.chinacreator.gzcm.engine.kb;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-13 / F04-14 / F04-15 / F04-18 契约与门禁补强（守卫型测试，来源与字节码双轨）。
 *
 * <ul>
 *   <li>{@link #kbMustNotDependOnOntologyInternals()} — 字节码级：kb 主源码不得依赖本体引擎的
 *       内部实现包（允许的 {@code engine.ontology.model.*} 共享契约豁免）；F04-18 K-51 精准拦截。
 *   <li>{@link #kbMustNotCallLlmEndpointDirectly()} — 源码级：kb 主源码禁止出现
 *       {@code /api/v1/llm/} 字面量与裸 {@code new RestTemplate()}；F04-13 K-34/K-35。
 *   <li>{@link #kbMustNotCreateOwnExecutors()} — 源码级：kb 主源码禁止新建
 *       {@code Executors.newXxx()} 或本地 {@code @Scheduled}（K-38/K-39 收口 runtime-task）。
 *   <li>{@link #kbMustNotReflectivelyInvokeSend()} — 源码级：kb 主源码禁止反射调用
 *       {@code KafkaTemplate#send(String, Object)}（K-44/K-45 收口 runtime-event）。
 * </ul>
 *
 * <p>kb-api 模块的对外模型隔离由 {@code KbApiContractIsolationTest#apiModuleHasNoEngineInternalImports}
 * 承担（同 F 章条目，独立测试类）。
 *
 * @author ECOS KB Team
 */
public class ModuleDependencyArchTest {

    private static JavaClasses classes;

    private static final Path KB_MAIN = resolveKbMainSourceRoot();

    private static final Path KB_IMPL = Path.of(
            "kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb");

    /** 容器配置层允许声明 {@code new RestTemplate()} bean 的白名单（F04-13 声明点唯一）。 */
    private static final String REST_TEMPLATE_ALLOWED_FILE = "config/KbEngineRestConfig.java";

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .importPackages("com.chinacreator.gzcm.engine.kb");
    }

    // ── F04-18 字节码：本体引擎内部实现包拦截 ─────────────────────────────

    @Test
    public void kbMustNotDependOnOntologyInternals() {
        ArchRule rule = noClasses().that()
                .resideInAPackage("com.chinacreator.gzcm.engine.kb..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.chinacreator.gzcm.engine.ontology.service..",
                        "com.chinacreator.gzcm.engine.ontology.impl..",
                        "com.chinacreator.gzcm.engine.ontology.controller..",
                        "com.chinacreator.gzcm.engine.ontology.dao..",
                        "com.chinacreator.gzcm.engine.ontology.repository..",
                        "com.chinacreator.gzcm.engine.ontology.mapper..",
                        "com.chinacreator.gzcm.engine.ontology.task..",
                        "com.chinacreator.gzcm.engine.ontology.config..",
                        "com.chinacreator.gzcm.engine.ontology.shared..");
        rule.check(classes);
    }

    // ── F04-13 源码：LLM 端点 / RestTemplate 禁止 ──────────────────────────

    @Test
    public void kbMustNotCallLlmEndpointDirectly() throws IOException {
        List<String> llmEndpointHits = scanSource(
                Pattern.compile("/api/v1/llm/"));
        List<String> bareRestTemplateHits = new ArrayList<>();
        for (String line : scanSource(Pattern.compile("new\\s+RestTemplate\\s*\\("))) {
            // 唯一合法位置：容器配置层 RestTemplate bean 声明（KbEngineRestConfig，斜杠归一免 Windows 反斜杠）
            if (!line.replace('\\', '/').contains(REST_TEMPLATE_ALLOWED_FILE)) {
                bareRestTemplateHits.add(line);
            }
        }
        assertTrue(llmEndpointHits.isEmpty(),
                "kb 主源码禁出现 /api/v1/llm/ 字面量（F04-13 K-34，一律经 LLMGateway 接口）：\n  "
                        + String.join("\n  ", llmEndpointHits));
        assertTrue(bareRestTemplateHits.isEmpty(),
                "kb 主源码禁裸 new RestTemplate()（F04-13 K-35，仅容器配置层可声明 bean）：\n  "
                        + String.join("\n  ", bareRestTemplateHits));
    }

    // ── F04-14 源码：本地 Executors / @Scheduled 禁止 ───────────────────────

    @Test
    public void kbMustNotCreateOwnExecutors() throws IOException {
        List<String> executorHits = scanSource(
                Pattern.compile("Executors\\.new\\w+\\("));
        List<String> scheduledHits = scanSource(
                Pattern.compile("^\\s*@Scheduled\\b"));
        assertTrue(executorHits.isEmpty(),
                "kb 主源码禁自建 Executors.newXxx()（F04-14 K-38，经 ITaskManagementService）：\n  "
                        + String.join("\n  ", executorHits));
        assertTrue(scheduledHits.isEmpty(),
                "kb 主源码禁本地 @Scheduled 定时器（F04-14 K-38，经 runtime-task 定时）：\n  "
                        + String.join("\n  ", scheduledHits));
    }

    // ── F04-15 源码：反射调用 KafkaTemplate.send 禁止 ───────────────────────

    @Test
    public void kbMustNotReflectivelyInvokeSend() throws IOException {
        // getMethod("send"/getDeclaredMethod("send" — 反射调用 KafkaTemplate.send 的两种形态
        List<String> reflectionHits = scanSource(
                Pattern.compile("(getDeclaredMethod|getMethod)\\s*\\(\\s*\"send\""));
        assertTrue(reflectionHits.isEmpty(),
                "kb 主源码禁反射调用 KafkaTemplate.send（F04-15 K-45，经 EventBusService）：\n  "
                        + String.join("\n  ", reflectionHits));
    }

    // ── 源码扫描工具 ───────────────────────────────────────────────────────

    /** 递归扫描 kb-engine 主源码 .java 文件；命中返回 "绝对路径" 标记行。 */
    private static List<String> scanSource(Pattern pattern) throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(KB_MAIN_SOURCE_ROOT)) {
            for (Path p : stream.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .sorted().toList()) {
                String content = Files.readString(p, StandardCharsets.UTF_8);
                // 只扫代码行：跳过注释行（javadoc `* ...` / `/*` / 行注释 `//`）。
                // 真实违规代码（new RestTemplate(...)、Executors.newXxx()、@Scheduled、
                // getMethod("send")）不会以注释符开头；但说明性 Javadoc 可能引用受禁字面量
                // （如 "{@code new RestTemplate()}"、"禁裸 new RestTemplate()"），须排除以免假阳。
                for (String line : content.split("\n")) {
                    String code = line.trim();
                    if (code.startsWith("*") || code.startsWith("/*")
                            || code.startsWith("//") || code.startsWith("/**")) {
                        continue;
                    }
                    if (pattern.matcher(line).find()) {
                        hits.add(p.toString().replace('\\', '/') + "  ||  " + code);
                    }
                }
            }
        }
        return hits;
    }

    /** 兼容 surefire CWD=模块目录与 IDE CWD=工程根两种形态，定位 kb-engine-impl/src/main/java。 */
    private static Path resolveKbMainSourceRoot() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            Path candidate = cur.resolve(
                    "kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb").normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cur = cur.getParent();
        }
        throw new AssertionError(
                "未找到 kb-engine-impl/src/main/java/.../engine/kb 主源码目录；从 "
                        + Path.of("").toAbsolutePath().normalize() + " 向上递归 10 级均失败");
    }

    /** 扫一次以懒解析（避免类加载期多实例），并缓存结果根。 */
    private static final Path KB_MAIN_SOURCE_ROOT = resolveKbMainSourceRoot();
}
