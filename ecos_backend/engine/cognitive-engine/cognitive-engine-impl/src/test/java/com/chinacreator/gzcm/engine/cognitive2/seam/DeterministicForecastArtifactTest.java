package com.chinacreator.gzcm.engine.cognitive2.seam;

import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator.Artifact;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator.ArtifactRow;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator.SixFactors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-COG-09 验收（PRD-05 §6.4）：确定性计算产物落 {@code ecos_dw}（ADR-14 承接为 cognitive 侧可测义务）。
 *
 * <p>冻结标识（三条）：
 * {@code #resultsLandInEcosDwNotEngineSchema}（DDL 只读形态断言，库侧仅 SELECT/catalog ⇒ 本类<b>不实跑库</b>，
 * 行数/实际落库面按 §14.4 未授权记"未执行"）、
 * {@code #rerunWithSameSixFactorsIsByteIdentical}、
 * {@code #snapshotJsonColumnsAreTextAndUnindexed}（MC02）。</p>
 *
 * <p>第四条冻结项 {@code ModuleDependencyArchTest#cognitiveDoesNotWriteEcosDwDirectly} 属 ArchUnit 面，
 * 与 {@code #resultsLandInEcosDwNotEngineSchema} 的源码扫描互补（一个按字节码依赖、一个按 SQL 字面量）。</p>
 */
@DisplayName("确定性计算产物落 ecos_dw（REQ-COG-09）")
class DeterministicForecastArtifactTest {

    private static final Path V197 = CogScope.REPO.resolve(
            "docs/50-db_script/02-flyway-migration/gateway/V197__ecos_dw_application_forecast_run.sql");

    /** 引擎 schema 禁建金额结果表（ST08 认知侧禁建表）。 */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z0-9_]+)\\.([a-z0-9_]+)");

    private static final Pattern JSON_COL = Pattern.compile(
            "(?im)^\\s*([a-z0-9_]+_json)\\s+([A-Za-z0-9]+(?:\\s*\\([0-9,]+\\))?)");

    private static final Pattern INDEX_STMT = Pattern.compile(
            "(?is)CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[a-z0-9_]+\\s+ON\\s+[a-z0-9_.]+\\s*\\(([^)]*)\\)");

    @Test
    @DisplayName("产物表只落 ecos_dw（业务域 APPLICATION 层），cognitive 侧零直写 ecos_dw")
    void resultsLandInEcosDwNotEngineSchema() throws IOException {
        assertTrue(Files.isRegularFile(V197), "V197 迁移脚本缺失（单源目录漂移）: " + V197);
        String ddl = stripSql(readUtf8(V197));

        List<String> schemas = new ArrayList<>();
        Matcher ct = CREATE_TABLE.matcher(ddl);
        while (ct.find()) {
            schemas.add(ct.group(1).toLowerCase(Locale.ROOT));
        }
        assertEquals(List.of("ecos_dw"), schemas.stream().distinct().toList(),
                "确定性产物表落点必须且只能是 ecos_dw（规格 2 / R-17 ① / ADR-14）");

        // 全仓单源目录内不得出现"同族表落引擎 schema"的并行形态（禁 ecos_cognitive / ecos_knowledge 金额结果表）
        Path migration = CogScope.REPO.resolve("docs/50-db_script/02-flyway-migration/gateway");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(migration, 1)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".sql")).toList()) {
                Matcher m = CREATE_TABLE.matcher(stripSql(readUtf8(p)));
                while (m.find()) {
                    String schema = m.group(1).toLowerCase(Locale.ROOT);
                    String table = m.group(2).toLowerCase(Locale.ROOT);
                    if (table.contains("forecast_run") && !"ecos_dw".equals(schema)) {
                        offenders.add(p.getFileName() + ": " + schema + "." + table);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "forecast_run 载体寄生引擎 schema（违 ST08/规格 2）: " + offenders);

        // 认知侧只准经 data-engine 写通道 REST 提交，禁直连 ecos_dw 的 SQL 字面量
        List<String> directWrites = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(CogScope.MAIN)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String code = CogScope.stripComments(readUtf8(p));
                Matcher m = Pattern.compile("(?i)\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+ecos_dw\\b")
                        .matcher(code);
                while (m.find()) {
                    directWrites.add(p.getFileName() + ": " + m.group(0));
                }
            }
        }
        assertTrue(directWrites.isEmpty(),
                "cognitive 生产源出现 ecos_dw 直写 SQL（绕 data-engine 写通道，违规格 2 / X-31 类）: " + directWrites);

        // 写通道组件必须经两态寻址 + data-engine REST 路径（禁硬编码 localhost — X-35）
        Path channel = CogScope.MAIN.resolve(
                "com/chinacreator/gzcm/engine/cognitive2/service/forecast/ForecastArtifactWriteChannel.java");
        assertTrue(Files.isRegularFile(channel), "写通道组件缺失: " + channel);
        String ch = CogScope.stripComments(readUtf8(channel));
        assertTrue(ch.contains("ServiceEndpointResolver"), "写通道未经两态寻址单源");
        assertTrue(ch.contains("/api/v1/datanet/write-channel"), "写通道未指向 data-engine 唯一写通道端点");
        assertTrue(!ch.contains("localhost"), "写通道硬编码 localhost（X-35）");
    }

    @Test
    @DisplayName("同六要素重跑字节一致；六要素任一变更则幂等键变更；缺要素 fail-loud")
    void rerunWithSameSixFactorsIsByteIdentical() {
        SixFactors factors = six("run-1", "cal-A", "v1", "2026-09-01T00:00:00", "snapshot-1", "f-1");
        List<ArtifactRow> cells = List.of(
                new ArtifactRow("p1", "d1", "2026-01", "revenue", new BigDecimal("12000.50"), "dw:forecast_run"),
                new ArtifactRow("p2", "d1", "2026-02", "cost", new BigDecimal("8000.25"), "dw:forecast_run"));

        Artifact first = DeterministicForecastCalculator.compute(factors, cells);
        Artifact second = DeterministicForecastCalculator.compute(factors, cells);
        // 明细乱序必须重排到同一规范序 ⇒ 同六要素重跑字节一致
        Artifact shuffled = DeterministicForecastCalculator.compute(factors, List.of(cells.get(1), cells.get(0)));

        assertArrayEquals(first.canonicalBytes(), second.canonicalBytes(), "同六要素重跑规范化字节必须一致");
        assertArrayEquals(first.canonicalBytes(), shuffled.canonicalBytes(), "明细顺序不得影响产物字节");
        assertEquals(first.idempotencyKey(), second.idempotencyKey());
        assertEquals(first.idempotencyKey(), shuffled.idempotencyKey());
        assertEquals(first.contentHash(), shuffled.contentHash());

        // 六要素逐项参与键：任一变更 ⇒ 幂等键变更（防止"键只含 runId"的假幂等）
        assertNotEquals(first.idempotencyKey(),
                DeterministicForecastCalculator.compute(six("run-2", "cal-A", "v1",
                        "2026-09-01T00:00:00", "snapshot-1", "f-1"), cells).idempotencyKey(), "forecastRunId 必进键");
        assertNotEquals(first.idempotencyKey(),
                DeterministicForecastCalculator.compute(six("run-1", "cal-B", "v1",
                        "2026-09-01T00:00:00", "snapshot-1", "f-1"), cells).idempotencyKey(), "caliberId 必进键");
        assertNotEquals(first.idempotencyKey(),
                DeterministicForecastCalculator.compute(six("run-1", "cal-A", "v2",
                        "2026-09-01T00:00:00", "snapshot-1", "f-1"), cells).idempotencyKey(), "caliberVersion 必进键");
        assertNotEquals(first.idempotencyKey(),
                DeterministicForecastCalculator.compute(six("run-1", "cal-A", "v1",
                        "2026-09-02T00:00:00", "snapshot-1", "f-1"), cells).idempotencyKey(), "asOfTime 必进键");
        assertNotEquals(first.idempotencyKey(),
                DeterministicForecastCalculator.compute(six("run-1", "cal-A", "v1",
                        "2026-09-01T00:00:00", "snapshot-2", "f-1"), cells).idempotencyKey(), "输入快照必进键");
        assertNotEquals(first.idempotencyKey(),
                DeterministicForecastCalculator.compute(six("run-1", "cal-A", "v1",
                        "2026-09-01T00:00:00", "snapshot-1", "f-2"), cells).idempotencyKey(), "公式版本必进键");

        // 缺要素 / 无证据引用 / 缺数值来源 ⇒ fail-loud，禁默认值补齐后"重跑一致"（那会伪造幂等证据）
        assertThrows(IllegalArgumentException.class, () -> DeterministicForecastCalculator.compute(
                six("run-1", "cal-A", "v1", "", "snapshot-1", "f-1"), cells));
        assertThrows(IllegalArgumentException.class, () -> DeterministicForecastCalculator.compute(
                new SixFactors("run-1", "cal-A", "v1", "2026-09-01T00:00:00", "snapshot-1", "f-1", List.of()), cells));
        assertThrows(IllegalArgumentException.class, () -> DeterministicForecastCalculator.compute(factors,
                List.of(new ArtifactRow("p1", "d1", "2026-01", "revenue", BigDecimal.ONE, null))));
        assertThrows(IllegalArgumentException.class, () -> DeterministicForecastCalculator.compute(factors,
                List.of(new ArtifactRow("p1", "d1", "2026-01", "revenue", null, "dw:forecast_run"))));
    }

    @Test
    @DisplayName("输入快照 JSON 列一律 TEXT 且不建索引（MC02）")
    void snapshotJsonColumnsAreTextAndUnindexed() throws IOException {
        String ddl = stripSql(readUtf8(V197));

        List<String> badType = new ArrayList<>();
        List<String> jsonCols = new ArrayList<>();
        Matcher col = JSON_COL.matcher(ddl);
        while (col.find()) {
            String name = col.group(1);
            String type = col.group(2).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            jsonCols.add(name);
            if (!"TEXT".equals(type)) {
                badType.add(name + " -> " + type);
            }
        }
        assertTrue(jsonCols.size() >= 5,
                "V197 的 *_json 列数异常（实读 " + jsonCols + "），脚本可能漂移");
        assertTrue(badType.isEmpty(), "JSON 语义列必须 TEXT（DR04），禁二进制 JSON（MC02）: " + badType);

        List<String> indexed = new ArrayList<>();
        Matcher idx = INDEX_STMT.matcher(ddl);
        while (idx.find()) {
            String keyCols = idx.group(1).toLowerCase(Locale.ROOT);
            for (String json : jsonCols) {
                if (Pattern.compile("\\b" + Pattern.quote(json) + "\\b").matcher(keyCols).find()) {
                    indexed.add(json);
                }
            }
        }
        assertTrue(indexed.isEmpty(), "JSON 列参与索引（违 MC02 规格 3/4）: " + indexed);

        // 同一纪律扩到索引表达式中的 jsonb GIN 形态（本表禁）
        assertTrue(!ddl.matches("(?is).*USING\\s+(GIN|BTREE)\\s*\\([a-z0-9_]*_json.*"),
                "V197 出现 JSON 列 GIN/BTREE 索引形态（违 MC02）");
    }

    private static SixFactors six(String runId, String caliberId, String caliberVersion,
                                  String asOf, String snapshot, String formula) {
        return new SixFactors(runId, caliberId, caliberVersion, asOf, snapshot, formula,
                List.of("ev-1", "ev-2"));
    }

    /**
     * SQL 注释剥离（保行数）：迁移脚本的说明段大量援引旧形态（{@code public.ecos_scenario_run}、
     * 禁词示例），只判真正执行的 DDL 语句，否则注释里的历史描述会被当成载体的存在性证据。
     */
    private static String stripSql(String sql) {
        String noBlock = Pattern.compile("(?s)/\\*.*?\\*/").matcher(sql)
                .replaceAll(mr -> java.util.regex.Matcher.quoteReplacement(
                        mr.group().replaceAll("[^\\n]", "")));
        return Pattern.compile("--[^\\n]*").matcher(noBlock)
                .replaceAll(mr -> java.util.regex.Matcher.quoteReplacement(""));
    }

    /** 宽松 UTF-8 读（存量 buszhi/sysman impl 的 pom.xml 非 UTF-8，严格解码会抛 MalformedInputException 使门禁假崩）。 */
    private static String readUtf8(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

}
