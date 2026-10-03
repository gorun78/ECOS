package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-07 (P0 红线) —— 禁"补默认值 / 补 0 以通过校验"的代码路径。
 *
 * <p>详细设计-02 §F02-07 红线：DQ 导入链路 <b>不允许</b>出现
 * {@code putIfAbsent(x, 0)} / {@code getOrDefault(x, 0)} / 显式
 * {@code setField(..., 0)} 之类的<b>静默补默认值</b>形态——这会
 * 把缺字段静默转成 0 后通过校验，违背"缺列即拒、显式报错"。
 *
 * <p>本测试对 <b>业务事实写入路径</b>（{@code service/BusinessFactService.java}
 * 与 {@code service/BusinessDomainWriteService.java}）做源码文本护栏：
 * 命中任一静默零值填充形态即 FAIL，防止未来 Agent 回补。
 * 数值上限/阈值常量（如 {@code = 0}）不计——仅盯"把缺失当作 0 塞回去"的调用形态。
 */
@DisplayName("F02-07 禁静默零值填充：fact 写入路径")
class NoSilentDefaultFillArchTest {

    /** 静态定位到本模块 src/main/java（护栏测试间复用同类路径解析）。 */
    private static Path moduleSrcMainJava() {
        return RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
    }

    /** 事实写入路径文件名（受限口径，避免误伤其它引擎的合法 WARN/DEG 分支）。 */
    private static final Set<String> FACT_WRITE_PATHS = Set.of(
            "BusinessFactService.java",
            "BusinessDomainWriteService.java");

    /** 静默零值填充形态（调用点，非常量声明）：
     *  {@code putIfAbsent(col, 0)} / {@code getOrDefault(col, 0)}
     *  {@code requireNonNullElse(col, 0)} / {@code orElse(0)} / {@code isBlank)...= 0}
     *  注意：要求整数 0 紧跟且后跟 {@code )}，避开 {@code LIMIT 0} 或 {@code 0.0} 等合法值。
     */
    private static final java.util.regex.Pattern SILENT_ZERO_FILL =
            java.util.regex.Pattern.compile(
                    "(?i)\\b(putIfAbsent|getOrDefault|requireNonNullElse)\\s*\\([^)]*,\\s*0\\s*\\)|\\.orElse\\s*\\(\\s*0\\b|\\bwasBlank[^;]*=\\s*0\\b|\\bishBlank\\(\\)[^;]*=\\s*0\\b");

    @Test
    void factWritePathHasNoSilentZeroFill() throws IOException {
        List<String> hits = new ArrayList<>();
        Path root = moduleSrcMainJava();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> FACT_WRITE_PATHS.contains(f.getFileName().toString()))
                    .sorted()
                    ::iterator) {
                String text = RlsInjectionGuardArchTest.stripComments(Files.readString(p, StandardCharsets.UTF_8));
                var m = SILENT_ZERO_FILL.matcher(text);
                while (m.find()) {
                    hits.add(p + ": 静默零值填充 " + m.group().trim());
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "F02-07 红线违规（fact 写入路径出现补 0 静默通过校验）：\n" + String.join("\n", hits));
    }
}
