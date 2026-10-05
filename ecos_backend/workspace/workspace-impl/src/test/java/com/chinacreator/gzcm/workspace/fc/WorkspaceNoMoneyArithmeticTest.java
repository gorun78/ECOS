package com.chinacreator.gzcm.workspace.fc;

import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分册09 §七 W233/C215 · 场景层无金额算术（铁律 §0.6 + R-59①）。
 *
 * <p>工作区 {@code *.fc.*} 只编排、不生产：金额/区间/回测数值一律由
 * cognitive-engine 计算后经 data-engine 写通道落 {@code ecos_dw}。
 * 本测试离线扫 {@code workspace-impl/src/main/java/.../workspace/fc} 下的 .java 源文件，
 * 一旦出现 {@code BigDecimal.subtract/multiply/divide/remainder/abs} 等金额语义字段上的算术调用，
 * 即判红（fail-loud ratchet）。</p>
 *
 * <p>白名单（<b>不计入</b>）：
 * <ul>
 *   <li>{@code BigDecimal.valueOf(id)}、{@code new BigDecimal("...")} 但常量仅用于<b>展示</b>
 *       （如 5% 固定展开比）——本批次 FC 包尚未引入任何 BigDecimal 字段，计数基线=0</li>
 *   <li>字符串拼接/比较/排序等与金额无关的字符算符</li>
 * </ul>
 *
 * <p>实现：只匹配 Arithmetic method 调用（{@code .subtract( / .multiply( / .divide( / .remainder( /
 * .add( / .abs() AND preceded by amount/bd prefix ident}），不做自然语言句法分析——守住"引入新算术"
 * 的"caller 面"即可。来源：与本批次 FcRunState 定位一致（scene 层禁金额代码）。</p>
 */
@DisplayName("C215 Workspace 场景层无金额算术 ratchet（fc 包基线=0）")
class WorkspaceNoMoneyArithmeticTest {

    /** 基线：fc 包内确实用到"金额算术方法调用"的行数（2026-10-05 实查 = 0）。 */
    static final int FC_ARM_ARITHMETIC_BASELINE = 0;

    /**
     * 金额语义字段做算术：字段名必须完整含 "amount"/"amountP*"、"money"、"price"（词边界匹配）；
     * 方法名必须是 BigDecimal 算术方法。避免 bd 前缀过宽。
     */
    private static final Pattern MONEY_ARITH =
            Pattern.compile(
                    "(?i)\\b[A-Za-z0-9_]*(?:amount[A-Za-z0-9_]*|money|price)\\s*\\.\\s*(?:subtract|multiply|divide|add|remainder|abs)\\s*\\(");

    /** 兼容式：显式 BigDecimal 常量做算术（例 new BigDecimal("0.05").multiply(...)）。 */
    private static final Pattern BD_CONSTANT_ARITH =
            Pattern.compile("\\bnew\\s+BigDecimal\\s*\\([^)]*\\)\\s*\\.\\s*(?:subtract|multiply|divide|add|remainder|abs)\\s*\\(");

    @Test
    @DisplayName("C215 · fc 包内不引入金额算术（基线=0，只减不增）")
    void noBigDecimalArithmeticInWorkspaceImpl() throws IOException {
        List<String> detail = new ArrayList<>();
        int hits = scan(MONEY_ARITH, detail) + scan(BD_CONSTANT_ARITH, detail);
        assertEquals(FC_ARM_ARITHMETIC_BASELINE, hits,
                String.format("C215 R-59① 铁律 §0.6 违反：fc 包出现金额算术 %d 处（基线 0）。明细前 20：\n%s",
                        hits, String.join("\n", detail.size() > 20 ? detail.subList(0, 20) : detail)));
    }

    private static int scan(Pattern rule, List<String> detail) throws IOException {
        Path fcRoot = fcRoot();
        if (fcRoot == null || !Files.isDirectory(fcRoot)) return 0;
        int hits = 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(fcRoot)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        for (Path p : files) {
            String rel = p.getFileName().toString();
            for (String line : read(p).split("\\R", -1)) {
                if (isCommentOnly(line)) continue;
                Matcher m = rule.matcher(line);
                if (m.find()) {
                    hits++;
                    detail.add(rel + " : " + line.trim());
                }
            }
        }
        return hits;
    }

    private static boolean isCommentOnly(String line) {
        String t = line.trim();
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*");
    }

    /** 只保留金额/价格语义（去掉过宽的 bd 匹配）。 */
    private static Path fcRoot() {
        Path me;
        try {
            me = Path.of(WorkspaceNoMoneyArithmeticTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IllegalStateException("test-classes 位置不可解析: " + e.getMessage());
        }
        // me = .../workspace-impl/target/test-classes → 上溯到名为 workspace-impl 的目录
        Path implDir = me;
        for (int i = 0; i < 8 && implDir != null; i++) {
            if (implDir.getFileName() != null
                    && implDir.getFileName().toString().equals("workspace-impl")
                    && Files.exists(implDir.resolve("pom.xml"))) {
                break;
            }
            implDir = implDir.getParent();
        }
        if (implDir == null) {
            throw new IllegalStateException("上溯找不到 workspace-impl 目录（起点 " + me + "）");
        }
        Path fc = implDir.resolve("src/main/java/com/chinacreator/gzcm/workspace/fc");
        return Files.isDirectory(fc) ? fc : null;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
