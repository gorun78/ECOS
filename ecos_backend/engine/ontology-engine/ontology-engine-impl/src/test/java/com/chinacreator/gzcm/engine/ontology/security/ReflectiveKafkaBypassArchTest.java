package com.chinacreator.gzcm.engine.ontology.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W89 / O-25 / C71 / 铁律 §2.5-4 · ST06 架构守卫《ReflectiveKafkaBypassArchTest》—
 * 禁止引擎内以「Object kafkaTemplate Bean + 反射 getMethod("send")」直连 KafkaTemplate 的绕过。
 *
 * <p>病理：{@code SecurityEngineClient} 曾用
 * {@code @Autowired @Qualifier("kafkaTemplate") Object} + {@code Class.getMethod("send")}
 * 反射调用 {@code KafkaTemplate.send(topic, payload)}。字段为 {@code Object} 类型，
 * 编译期无 spring-kafka import，故 gateway 侧
 * {@code businessMustNotDependOnSpringKafkaDirectly}（import 图检查）结构上<b>看不见</b>该违规；
 * 且 bean 缺失时静默 warn 返回 = 审计事件丢失。</p>
 *
 * <p>修复口径（O-25/W89）：审计一律收敛 runtime-event {@link
 * com.chinacreator.gzcm.runtime.eventbus.EventBusService#publish}（Kafka 在 → 入 topic；
 * 不在 → 内存 fallback）。本测试走<b>源码扫描</b>（纯 Files/PathWalking，无 Spring 上下文），
 * 扫描 ontology-engine 全源码树，断言无以下两类绕过：</p>
 * <ol>
 *   <li>任一文件以反射方式调用名为 {@code "send"} 的 Method（{@code getMethod(…send…)} /
 *       {@code getDeclaredMethod(…send…)}）= FAIL。</li>
 *   <li>任一文件同时出现 {@code @Qualifier("kafkaTemplate")} + {@code Object} 类型 Bean 字段
 *       （即「以 Object 吞掉 KafkaTemplate 再反射」的注入形态）= FAIL。</li>
 * </ol>
 *
 * <p>实现口径与 data-engine {@code QualifiedTableNameArchTest} 一致（JUnit5 + Files.walk 源码扫描）。</p>
 */
@DisplayName("W89/O-25/§2.5/ST06：禁引擎内反射 getMethod(\"send\") 绕道直连 KafkaTemplate")
class ReflectiveKafkaBypassArchTest {

    /** 反射取名为 "send" 的方法（覆盖 getMethod/getDeclaredMethod，参数可含任意 text）。 */
    private static final Pattern REFLECTIVE_SEND = Pattern.compile(
            "\\bget(?:Declared)?Method\\s*\\([^)]*\"send\"",
            Pattern.CASE_INSENSITIVE);

    /** 以 @Qualifier("kafkaTemplate") 注入的 Object 类型 Bean（反射绕道的注入形态）。 */
    private static final Pattern KAFKA_TEMPLATE_OBJECT = Pattern.compile(
            "@Qualifier\\s*\\(\\s*[\"']kafkaTemplate[\"']\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static final String ONTOLOGY_ENGINE_REL = "ecos_backend/engine/ontology-engine";

    @Test
    @DisplayName("ontology-engine 全源码树不得出现反射 send() / Object kafkaTemplate 注入")
    void noReflectiveKafkaBypassInOntologyEngine() throws IOException {
        Path base = findRepoRoot().resolve(ONTOLOGY_ENGINE_REL);
        assertTrue(Files.isDirectory(base), "缺 ontology-engine 源码目录: " + base);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path f : (List<Path>) files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (f.toString().contains("src\\test") || f.toString().contains("/src/test/")) {
                    // 本守卫测试自身及 test 源码不做自检（避免误伤本测试的字面量）
                    continue;
                }
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                boolean hasKafkaQualifier = false;
                int lin = 0;
                for (String raw : lines) {
                    lin++;
                    String codeOnly = collapseComments(raw);
                    String codeTrim = codeOnly.trim();
                    if (codeTrim.isEmpty()) {
                        continue;
                    }
                    if (KAFKA_TEMPLATE_OBJECT.matcher(codeOnly).find()) {
                        hasKafkaQualifier = true;
                        violations.add(rel(f, base) + ":" + lin
                                + ": 以 @Qualifier(\"kafkaTemplate\") 注入 Object Bean（反射绕道前置形态）: " + codeTrim);
                    }
                    if (REFLECTIVE_SEND.matcher(codeOnly).find()) {
                        violations.add(rel(f, base) + ":" + lin
                                + ": 反射元数据查找发送方法（KafkaTemplate 直连绕道）: " + codeTrim);
                    }
                }
                // 组合违规：kafkaTemplate qualifier + 同文件另有 "Object" Bean 字段（反射缝）
                if (hasKafkaQualifier
                        && lines.stream().anyMatch(l -> collapseComments(l).trim().startsWith("Object kafkaTemplate"))) {
                    violations.add(rel(f, base) + ": Object kafkaTemplate + @Qualifier 组合 = 反射绕道");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "W89/O-25/§2.5/ST06：ontology-engine 禁止反射 getMethod(\"send\") 直连 KafkaTemplate"
                        + "（审计一律收敛 runtime-event EventBusService）：\n"
                        + String.join("\n", violations));
    }

    /** Comment-line fold: skip Javadoc body lines and inline comments before matching code-shape patterns. */
    private static String collapseComments(String line) {
        String t = line.trim();
        if (t.startsWith("*") || t.startsWith("/*") || t.endsWith("*/") || t.startsWith("//")) {
            return "";
        }
        int slash = line.indexOf("//");
        if (slash >= 0) {
            return line.substring(0, slash);
        }
        return line;
    }

    private static String rel(Path f, Path base) {
        try {
            return base.relativize(f).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return f.getFileName().toString();
        }
    }

    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))
                    || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
