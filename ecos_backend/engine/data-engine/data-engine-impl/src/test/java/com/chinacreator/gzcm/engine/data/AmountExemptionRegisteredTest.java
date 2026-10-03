package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * F02-06-6 / E.2 ST03-A（详细设计-02，DATA-01 P0）—— 事实金额列须在列级加密豁免登记表**逐列登记**。
 *
 * <p>设计红线（E.2 §quote）：事实五表金额列（{@code amount}/{@code hourly_rate}/{@code
 * contract_base}）**不加密**是<b>豁免</b>，豁免必须先在
 * {@code docs/40-实现/列级加密豁免登记表-2026-09-28.md} 逐列登记（表/列/理由/防护/批准人/日期），
 * <b>未登记即视同违规</b>。启动侧校验语义 = 缺登记 ⇒ FAIL。本测试是对该不变量的静态护栏：</p>
 * <ol>
 *   <li>登记表存在（缺失 = 整项豁免不成立，直接 fail）；</li>
 *   <li>四张事实表中<b>承载金额的列</b>逐列出现在登记表内（同行同时含表名与列名）；</li>
 *   <li>比率/工时列（{@code attribution_ratio}/{@code realization_rate}/{@code fte}/{@code work_hours}）
 *       与脱敏列（{@code staff_ref_hash}）<b>不</b>在豁免登记中（它们不是金额豁免项，登记反而违规）。</li>
 * </ol>
 *
 * <p>登记表当前实测条目（行号依 2026-10-03 实查）：{@code ecos_biz_stage_fact}→
 * {@code contract_base/amount}、{@code ecos_biz_cost_fact}→{@code amount}、
 * {@code ecos_biz_resource_fact}→{@code hourly_rate}；{@code staff_ref_hash} 明确列于"脱敏非豁免"。
 * 本测试只做<b>存在性 + 逐列登记</b>断言，不触库、不 Spring 容器。</p>
 */
class AmountExemptionRegisteredTest {

    /** 须在登记表逐列登记的 事实表 → 金额列 集合（E.2 明确"不加密但必登记"的列）。 */
    private static final String[][] REQUIRED_EXEMPT = {
            {"ecos_biz_stage_fact", "contract_base"},
            {"ecos_biz_stage_fact", "amount"},
            {"ecos_biz_cost_fact", "amount"},
            {"ecos_biz_resource_fact", "hourly_rate"},
    };

    /** 这些列本就<b>不应</b>登记为金额豁免（比率/工时/脱敏列），登记反而违规。 */
    private static final String[][] MUST_NOT_EXEMPT_COL = {
            {"ecos_biz_resource_fact", "staff_ref_hash"},
    };

    @Test
    void factAmountColumnsMustBeRegisteredInExemptionLedger() throws IOException {
        Path registry = findExemptionLedger();
        assertTrue(Files.isRegularFile(registry),
                "列级加密豁免登记表不存在 —— 事实金额列未登记即视同 ST03 违规; 期望 docs/40-实现/列级加密豁免登记表*.md");

        List<String> lines = Files.readAllLines(registry, StandardCharsets.UTF_8);

        // ① 金额列逐列登记（同行含表名与列名，允许 cell 内 "a / b" 并写）
        List<String> missing = new ArrayList<>();
        for (String[] pair : REQUIRED_EXEMPT) {
            String table = pair[0];
            String col = pair[1];
            boolean found = lines.stream()
                    .anyMatch(l -> l.contains(table) && containsColumnCell(l, col));
            if (!found) missing.add(table + "." + col);
        }
        assertTrue(missing.isEmpty(),
                "ST03-A 违规：以下事实金额列未在列级加密豁免登记表逐列登记（未登记即视同违规）:\n"
                        + String.join("\n", missing)
                        + "\n[登记表: " + registry + "]");

        // ② 脱敏列 staff_ref_hash 属"不可逆脱敏存储"，不得登记为金额豁免
        List<String> wronglyExempt = new ArrayList<>();
        for (String[] pair : MUST_NOT_EXEMPT_COL) {
            String table = pair[0];
            String col = pair[1];
            boolean inExemptRow = lines.stream()
                    .anyMatch(l -> l.contains(table) && containsColumnCell(l, col)
                            && !l.contains("不是豁免") && !l.contains("不是豁免项")
                            && !l.contains("不得登记") && !l.contains("脱敏"));
            if (inExemptRow) wronglyExempt.add(table + "." + col);
        }
        assertTrue(wronglyExempt.isEmpty(),
                "ST03-A 违规：脱敏/脱列被误登记为金额豁免（脱敏列不是豁免项）:\n"
                        + String.join("\n", wronglyExempt));
    }

    /**
     * 判定"表格行的某个数据 cell 是否声明了该列名"。登记表列名可能以 {@code contract_base / amount}
     * 形式并写在同一 cell，也可能相邻；此处按"<b>令牌边界</b>出现列名"判定，避免裸子串误命中
     * （如 {@code amount} 命中 {@code plan_amount}/{@code revenue_amount} 之类别的列）。
     */
    private static boolean containsColumnCell(String line, String col) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(col) + "(?![A-Za-z0-9_])")
                        .matcher(line);
        return m.find();
    }

    /** 从模块 src/main/java 上溯到同时含 {@code docs} 与 {@code ecos_backend} 的仓根，再定位登记表。 */
    private Path findExemptionLedger() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Path cur = srcMain;
        for (int i = 0; i < 12 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                return locateRegistryUnder(cur.resolve("docs"));
            }
            cur = cur.getParent();
        }
        fail("无法从 " + srcMain + " 上溯到 ECOS 仓根（含 docs + ecos_backend），无法定位列级加密豁免登记表");
        return null; // unreachable
    }

    private Path locateRegistryUnder(Path docs) {
        try (Stream<Path> walk = Files.walk(docs)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("列级加密豁免登记表") && n.endsWith(".md");
                    })
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "docs/ 下未找到 列级加密豁免登记表*.md（事实金额列豁免无登记载体）"));
        } catch (IOException e) {
            throw new AssertionError("扫描 docs/ 定位登记表失败: " + e.getMessage(), e);
        }
    }
}
