package com.chinacreator.gzcm.engine.ai.wagent.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审批闸门接缝门（计划 §三 批 D：三处 L3 校验点不得「仅判非空」）。
 *
 * <p><b>病灶</b>：{@code WAgentOrchestrator.approveGate} 内部只判 token 非空——任意非空字符串
 * 都能清闸并把 Run 推进到 {@code executing}。真正的校验依赖调用方先跑
 * {@code ApprovalTokenService.verifyAndConsume}。一旦有人新增一个直接调 {@code approveGate}
 * 的入口（新 Controller / 运维脚本 / 内部任务），审批闸就被整体绕过，且<b>编译与既有用例全绿</b>。</p>
 *
 * <p>本门禁只读源码文本：扫全仓 {@code .approveGate(} 调用点，要求每个调用方同文件内
 * 出现 {@code verifyAndConsume}。判据带自检（对合成文本喂一次「无校验的调用」必须判违规），
 * 防止判据写成恒真。</p>
 */
class ApprovalGateChokepointTest {

    private static final String GATE_CALL = ".approveGate(";
    /** 必须匹配「方法调用」形态，否则 `verifyAndConsumeX(` 这类改名会被前缀命中而假绿。 */
    private static final String VERIFY = "verifyAndConsume(";

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("ecos_backend"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "未定位到仓根（含 ecos_backend 的目录）");
        return dir;
    }

    /** 判据本体：返回违规文件说明；空列表即合格。抽出来便于自检。 */
    static List<String> findViolations(Path root) throws Exception {
        List<String> violations = new ArrayList<>();
        Path src = root.resolve("ecos_backend");
        try (Stream<Path> files = Files.walk(src)) {
            for (Path file : files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/src/test/"))
                    .toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (!text.contains(GATE_CALL)) {
                    continue;
                }
                if (!text.contains(VERIFY)) {
                    violations.add(file + " 调用了 approveGate 但同文件无 verifyAndConsume");
                }
            }
        }
        return violations;
    }

    @Test
    @DisplayName("全仓 approveGate 调用点必须同文件带 verifyAndConsume（清闸不可回退）")
    void everyApproveGateCallIsPreVerified() throws Exception {
        List<String> violations = findViolations(repoRoot());
        assertTrue(violations.isEmpty(), "审批闸可被绕过：\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("判据自检：真实 Controller 源码删掉校验行后必须被判违规（防恒真）")
    void detectorIsNotVacuousOnRealSource() throws Exception {
        Path real = repoRoot().resolve(
                "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/"
                        + "engine/ai/wagent/controller/WAgentRunController.java");
        assertTrue(Files.isRegularFile(real), "真实 Controller 源码缺失: " + real);
        String original = Files.readString(real, StandardCharsets.UTF_8);
        assertTrue(original.contains(VERIFY), "真实调用方已不含校验调用，门禁失去前提");

        Path tmp = Files.createTempDirectory("approval-gate-probe-");
        try {
            Path copy = tmp.resolve("ecos_backend/fake/Controller.java");
            Files.createDirectories(copy.getParent());
            // 保留 approveGate 调用，抽掉校验调用（模拟有人新增绕过通道）
            Files.writeString(copy, original.replace(VERIFY, "verifyAndConsumeX("),
                    StandardCharsets.UTF_8);
            assertFalse(findViolations(tmp).isEmpty(),
                    "真实源码去掉校验后判据未报违规 ⇒ 判据失效");

            // 原样拷回必须判合格（证明不是「见 Controller 就报」）
            Files.writeString(copy, original, StandardCharsets.UTF_8);
            assertTrue(findViolations(tmp).isEmpty(), "合法形态被误判");
        } finally {
            try (Stream<Path> walk = Files.walk(tmp)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
