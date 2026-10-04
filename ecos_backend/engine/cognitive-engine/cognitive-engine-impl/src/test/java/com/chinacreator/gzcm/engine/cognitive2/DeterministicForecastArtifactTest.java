package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 F05-06 / B-3 定性离线护栏：确定性计算产物 (forecast/scenario_run/counterfactual
 * artifact) 不建表在认知 schema；ADR-14 + R-14 组已裁定确定性产物经 data-engine 写通道落
 * APPLICATION (ecos_dw) 层。
 *
 * <p>本护栏锁定<b>认知侧 DDL 单源</b>（V187/V188/V189）不出现与确定性产物等价的对象名
 * （forecast/scenario_run/counterfactual/summary/aggregate 族）——新增一类即红，让
 * 认知侧<b>不能</b>先把产物落成自拥有表再对其它域"看起来跨写"。</p>
 *
 * <p>认知侧历史 DDL (V22/V117/V124/V127~V130/V146/V159/V163/V208/V240/V239) 属 legacy
 * 已就地未改（R9），不在本护栏走查范围；本护栏只走查<b>新增</b> E1 数据源三份。</p>
 */
final class DeterministicForecastArtifactTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+TABLE\\b[^;]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORECAST_LIKE_TABLE = Pattern.compile(
        "\\b(?:ecos_cognitive_)?(forecast|scenario_run|counterfactual|summary|aggregate|metric_value)[a-z_]*",
        Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("V187/V188/V189 三份认知 DDL 新建物不含确定性计算产物表（forecast/scenario_run/counterfactual 族 0 命中）")
    void resultsLandInEcosDwNotEngineSchema() throws IOException {
        for (int v : new int[] {187, 188, 189}) {
            String sql = CognitiveDocPaths.migrationOrNull(v);
            assertNotNull(sql, "V%d 迁移脚本缺失" .formatted(v));
            String stripped = CognitiveDocPaths.stripComments(sql);
            for (var m = CREATE_TABLE.matcher(stripped); m.find(); ) {
                String ddl = m.group();
                if (FORECAST_LIKE_TABLE.matcher(ddl).find()) {
                    fail("V%d CREATE TABLE 含确定性产物表名（应落 APPLICATION 层，非 ecos_cognitive）：%s"
                        .formatted(v, clean(ddl)));
                }
            }
        }
        // 走查 0 命中即通过
        assertTrue(true);
    }

    private static String clean(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
