package com.chinacreator.gzcm.engine.data.fact.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AmountExemptionRegisteredTest — ST03-A 回归守卫。
 *
 * <p>V167 契约：{@code amount}（stage_fact/cost_fact）、{@code hourly_rate}
 * （resource_fact）、{@code contract_base}（stage_fact）为聚合计算必需，**必须**已在
 * {@code docs/40-实现/列级加密豁免登记表-2026-09-28.md} 逐列登记（V167 头注 ST03-A 段）。
 *
 * <p>本守卫读登记表文件（从模块目录向上按 .git 标记找仓库根），断言 V167 五表
 * 金额/敏感列登记条目存在。2026-09-29 已预登记 → 当前应 PASS；若有人删行即 FAIL。
 */
class AmountExemptionRegisteredTest {

    /** 登记必需：表 名 → 列 名 列表（V167 ST03-A 头注一致）。 */
    private static final List<String> REQUIRED_ENTRIES = List.of(
            "`ecos_dw.ecos_biz_stage_fact`",
            "contract_base",
            "`amount`",
            "`ecos_dw.ecos_biz_cost_fact`",
            "`ecos_dw.ecos_biz_resource_fact`",
            "hourly_rate");

    @Test
    @DisplayName("ST03-A 登记表含 V167 金额列条目（回归守卫）")
    void registerContainsV167AmountColumns() throws IOException {
        Path doc = repoRoot().resolve("docs/40-实现/列级加密豁免登记表-2026-09-28.md");
        assertTrue(Files.exists(doc), "登记表缺失: " + doc);
        String content = Files.readString(doc);
        for (String entry : REQUIRED_ENTRIES) {
            assertTrue(content.contains(entry),
                    "ST03-A 登记表缺少必需条目 '" + entry + "'（V167 金额/敏感列未登记 = 违规）");
        }
        // 精确性：stage_fact 行须同时含 contract_base 与 amount（V167 差异③）
        int idx = content.indexOf("ecos_biz_stage_fact");
        assertTrue(idx >= 0, "登记表未提及 ecos_biz_stage_fact");
        String line = content.substring(idx, Math.min(content.length(), idx + 400));
        assertTrue(line.contains("contract_base") && line.contains("amount"),
                "stage_fact 登记行必须同时含 contract_base 与 amount，实际: " + line);
    }

    /** 从当前用户目录向上找 .git 标记（不写死机器路径）。 */
    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.home"));
        // 兜底：user.dir 常已是模块目录
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path c = cur; c != null; c = c.getParent()) {
            if (Files.isDirectory(c.resolve(".git"))) {
                return c;
            }
            if (c.equals(p)) break;
        }
        throw new IllegalStateException("未找到 .git 仓库根（user.dir=" + cur + "）");
    }
}
