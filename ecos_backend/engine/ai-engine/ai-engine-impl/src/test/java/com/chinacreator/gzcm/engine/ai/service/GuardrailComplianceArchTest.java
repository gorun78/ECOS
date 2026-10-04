package com.chinacreator.gzcm.engine.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F06-04 架构护栏（设计-06 §312 {@code GuardrailComplianceArchTest#noLocalRegexUsedAsPassDecision}）。
 *
 * <p>文本层权威护栏（对齐 {@code ObligationsApplyTest#engineDoesNotSelfImplementMasking} 先例）：
 * 扫描 {@code GuardrailsServiceImpl.java} 源码（剥注释），断言<b>本地正则不再充当护栏放行依据</b>
 * （X-16 收口）——即不得再有 {@code Pattern.compile} + {@code .matcher(...).find()} 判定的形态。
 * 一旦有人往引擎里塞回"本地正则判通过"逻辑，本测试立即转红。
 *
 * <p>守护口径：{@code GuardrailsService#validate} 的放行（passed=true）<b>只能</b>来自
 * security 客户端 {@code screenOrThrow} 的显式确认，引擎内不得自建内容审核正则。</p>
 */
class GuardrailComplianceArchTest {

    private static final Pattern PATTERN_COMPILE = Pattern.compile("Pattern\\.compile\\s*\\(");
    private static final Pattern MATCHER_FIND = Pattern.compile("\\.matcher\\s*\\([^)]*\\)\\s*\\.\\s*(?:find|matches|lookingAt)\\s*\\(");

    @Test
    @DisplayName("noLocalRegexUsedAsPassDecision: GuardrailsServiceImpl 内不得出现本地正则判定（X-16）")
    void noLocalRegexUsedAsPassDecision() throws IOException {
        String src = readGuardrailsImpl();
        String code = stripComments(src);
        StringBuilder hits = new StringBuilder();
        if (PATTERN_COMPILE.matcher(code).find()) hits.append("Pattern.compile(...) 本地正则\n");
        if (MATCHER_FIND.matcher(code).find()) hits.append(".matcher().find()/matches() 判定\n");
        assertTrue(hits.isEmpty(),
                "F06-04 违规 —— GuardrailsServiceImpl 出现本地正则充当护栏放行依据（应全改经 security 客户端）:\n"
                        + hits);
    }

    /** 剥掉块注释与行注释，避免 Javadoc/说明里的正则字样被误判 */
    private static String stripComments(String s) {
        String noBlock = s.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlock.replaceAll("//[^\n]*", "");
    }

    private static String readGuardrailsImpl() throws IOException {
        try (Stream<Path> walk = Files.walk(resolveModuleSrcMainJava())) {
            var matched = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("GuardrailsServiceImpl.java"))
                    .toList();
            if (matched.isEmpty()) {
                fail("未找到 GuardrailsServiceImpl.java（护栏合规护栏无法判定）");
                return "";
            }
            return Files.readString(matched.get(0), StandardCharsets.UTF_8);
        }
    }

    /** 定位模块 src/main/java（同 ObligationsApplyTest 先例） */
    private static Path resolveModuleSrcMainJava() {
        String userDir = System.getProperty("user.dir");
        Path cur = Paths.get(userDir).toAbsolutePath().normalize();
        for (String rel : new String[]{"src/main/java",
                "ai-engine-impl/src/main/java",
                "engine/ai-engine/ai-engine-impl/src/main/java"}) {
            Path cand = cur.resolve(rel);
            if (Files.isDirectory(cand)) return cand;
        }
        throw new IllegalStateException("无法定位 ai-engine-impl/src/main/java (user.dir=" + userDir + ")");
    }
}
