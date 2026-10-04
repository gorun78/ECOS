package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * F06-22 / W164 → C146（前提门禁的计数护栏）：防「ai-engine + llm-gateway
 * 的 {@code @SpringBootTest} 目录建成了但里面空，红线验收仍无处安放」。
 *
 * <p>设计-06 §F06-22 明确：全仓 {@code @SpringBootTest} 曾 0 命中（X-87），此底座
 * <b>必须本批落</b>，否则 F06-01/03/04/07/… 的红线验收「一律标未执行」。本类做
 * <b>可判化计数</b>：源码扫描 ai-engine-impl 与 llm-gateway 两个模块的
 * {@code src/test/java}，断言各自 {@code @SpringBootTest} 命中数 ≥ 1 —— 即
 * 「两模块各有至少一个真实集成切片」这一前置门禁不再靠人工口头确认（Q13：
 * 验收只允许可执行标识）。
 *
 * <p>棘轮语义：只减不增无意义（本项是「必须存在」的下限断言，不是存量冻结），
 * 故用 {@code >= 1} 下限门槛而非基线上限。扫描规则与 {@link SelfScheduleArchTest}
 * 同族（自携 walk + stripComments 去伪命中）。</p>
 */
class AiIntegrationCoverageTest {

    private static final Pattern SPRING_BOOT_TEST = Pattern.compile("@SpringBootTest\\b");

    private static final Path AI_IMPL_TEST =
            resolveBackendRoot().resolve(
                    "engine/ai-engine/ai-engine-impl/src/test/java");
    private static final Path LLM_GW_TEST =
            resolveBackendRoot().resolve("runtime/llm-gateway/src/test/java");

    /** 向上探测 ecos_backend 根（与同族 SelfScheduleArchTest / ModuleDependencyArchTest 一致约定）。 */
    private static Path resolveBackendRoot() {
        Path cur = Path.of("").toAbsolutePath();
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve("pom.xml"))
                    && Files.isDirectory(cur.resolve("gateway"))) {
                return cur.normalize();
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位 ecos_backend 根（pom.xml + gateway/ 目录）");
    }

    @Test
    @DisplayName("F06-22 aiEngineAndGatewayHaveAtLeastOneRealIT — 两模块各有 ≥1 个 @SpringBootTest 切片")
    void aiEngineAndGatewayHaveAtLeastOneRealIT() throws IOException {
        int aiIts = countSpringBootTest(AI_IMPL_TEST);
        int gwIts = countSpringBootTest(LLM_GW_TEST);

        Assertions.assertTrue(aiIts >= 1,
                "ai-engine-impl 缺少 @SpringBootTest 集成切片（F06-22 底线未落：红线验收无处安放）。"
                        + "实测 @SpringBootTest 命中 = " + aiIts);
        Assertions.assertTrue(gwIts >= 1,
                "llm-gateway 缺少 @SpringBootTest 集成切片（F06-22 底线未落：红线验收无处安放）。"
                        + "实测 @SpringBootTest 命中 = " + gwIts);
        System.out.println("[AiIntegrationCoverage] @SpringBootTest slices — "
                + "ai-engine-impl: " + aiIts + "/1, llm-gateway: " + gwIts + "/1 (下限 ≥1)");
    }

    private static int countSpringBootTest(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("测试根目录不存在（工作树路径变更？）：" + root);
        }
        int count = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .toArray(Path[]::new)) {
                String source = Files.readString(p);
                for (String line : source.split("\r?\n")) {
                    if (SPRING_BOOT_TEST.matcher(stripComments(line)).find()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static String stripComments(String line) {
        int lineComment = line.indexOf("//");
        if (lineComment >= 0) {
            line = line.substring(0, lineComment);
        }
        int blockStart = line.indexOf("/*");
        if (blockStart >= 0) {
            int blockEnd = line.indexOf("*/");
            if (blockEnd > blockStart) {
                line = line.substring(0, blockStart) + line.substring(blockEnd + 2);
            } else {
                line = line.substring(0, blockStart);
            }
        }
        return line;
    }
}
