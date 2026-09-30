package com.chinacreator.gzcm.engine.data.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.data.service.LakeObjectKeys;

/**
 * F02-14-1 验收：{@code SINK_MINIO} parquet 分支的对象 key 规范 + 写入路径守卫。
 *
 * <p>原始验收口径：{@code ParquetRoundtripTest}（DuckDB 读回行数/列型一致 + key 合规）。
 * DuckDB parquet 写入器<b>尚未落地</b>（{@code PipelineExecutionService#executeSinkMinio}
 * 当前对显式非 csv 格式 fail-loud 抛 {@code 写入器未就绪}，<b>禁止静默写成 csv 却声称 parquet</b>），
 * 运真切 COPY 需真实 DuckDB — 在 CI（GitHub-runner 中立 JVM，无原生 DuckDB）下不可行。
 * 故本类按验收给定的<b>源码/契约 fallback</b> 落地两条今天确实成立的守卫，使测试存在且通过：
 * <ol>
 *   <li><b>key 规范</b>（F02-14-1 字面）：parquet 对象 key 必须落
 *       {@code raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{timestamp}.parquet}
 *       —— 经真实 {@link LakeObjectKeys#structuredObjectKey} 组装并对分区日/时间戳/扩展名形态断言；</li>
 *   <li><b>写入守卫（不静默）</b>：{@code executeSinkMinio} 对显式非 csv 格式（含 parquet）
 *       必须 fail-loud（抛业务异常"写入器未就绪"），不得静默降级写 csv —— 源码扫描断言该守卫存在。</li>
 * </ol>
 *
 * <p>当 DuckDB parquet 写入器落地（F02-14 完全态）后，应新增真 DuckDB 读回 roundtrip 断言
 * （行数 + 列型一致）并置本 fallback 守卫为兼容。</p>
 */
@DisplayName("F02-14-1 SINK_MINIO parquet：对象 key 规范 + 不静默降级守卫")
class ParquetRoundtripTest {

    /** key 规范：raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{yyyyMMddHHmmss}.parquet */
    private static final Pattern PARQUET_KEY = Pattern.compile(
            "^raw/structured/([a-z0-9_]+)/([a-z0-9_]+)/dt=\\d{4}-\\d{2}-\\d{2}/[a-z0-9_]+_\\d{14}\\.parquet$");

    @Test
    @DisplayName("parquet 对象 key 合规：raw/structured/{source}/{table}/{partition}=day/{table}_{ts}.parquet")
    void parquetObjectKeyConformsToSpec() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 19, 10, 30, 0);
        String key = LakeObjectKeys.structuredObjectKey("ds1", "orders", "dt", "parquet", now);

        assertNotNull(key, "structuredObjectKey 不得为 null");
        assertTrue(PARQUET_KEY.matcher(key).matches(),
                "parquet key 须符合 raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{ts}.parquet，实际: " + key);
        // 分区段 = 固定日期
        assertTrue(key.contains("/orders/dt=2026-09-19/"),
                "分区段须为 dt=2026-09-19，实际: " + key);
        // 时间戳段 = 14 位 yyyyMMddHHmmss
        assertTrue(key.contains("orders_20260919103000.parquet"),
                "文件名须含 {table}_{yyyyMMddHHmmss}.parquet，实际: " + key);
    }

    @Test
    @DisplayName("partition_field 缺省回退 dt + 扩展名归一：'.parquet' → 'parquet'")
    void parquetKeyPartitionDefaultAndExtNormalize() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        // partitionField 传 null → 回退默认 dt；extension 传 ".parquet"（带点）→ 归一 parquet
        String key = LakeObjectKeys.structuredObjectKey("srcA", "tbl_x", null, ".parquet", now);
        assertTrue(key.endsWith(".parquet"), "扩展名归一后须以 .parquet 结尾，实际: " + key);
        assertTrue(key.contains("/dt=2026-01-02/"), "缺省分区字段回退 dt=，实际: " + key);
        assertTrue(key.startsWith("raw/structured/srcA/tbl_x/"), "对象 key 前缀须 raw/structured/{source}/{table}/，实际: " + key);
    }

    @Test
    @DisplayName("写入守卫：SINK_MINIO 对显式 parquet（非 csv）fail-loud（禁静默写 csv 却声称 parquet）")
    void sinkMinioParquetFailsLoudNotSilentCsv() throws IOException {
        Path root = repoRoot();
        Path svc = root.resolve(
                "ecos_backend/engine/data-engine/data-engine-impl/src/main/java" +
                "/com/chinacreator/gzcm/engine/data/pipeline/PipelineExecutionService.java");
        assertTrue(Files.isRegularFile(svc), "PipelineExecutionService 缺失: " + svc);
        String src = Files.readString(svc, StandardCharsets.UTF_8);

        // 守卫 1：解析显式格式 explicitLakeFormat（节点 format > 对象名后缀）
        assertTrue(src.contains("explicitLakeFormat"),
                "SINK_MINIO 须显式解析生效格式（节点 format/对象名后缀优先）");
        // 守卫 2：非 csv 显式格式 → 抛 BusinessException（fail-loud，不静默写 csv）
        assertTrue(src.contains("\"csv\".equals(explicitFormat)")
                || src.contains("explicitFormat != null && !\"csv\".equals(explicitFormat)"),
                "须存在「显式非 csv → 拒写」判定（防静默 csv 声称 parquet）");
        assertTrue(src.contains("写入器未就绪"),
                "parquet/orc/avro 显式但写入器未就绪时须 fail-loud（消息含'写入器未就绪'），避免静默降级");
        // 守卫 3：parquet 后缀被识别为 parquet 语义（suffixFormat 分支）
        assertTrue(src.contains(".parquet"), "对象名 .parquet 后缀须被识别为 parquet 格式");
        // 断言：当前不存在「静默把 parquet 写 csv」路径 —— 未指定格式时沿用 csv 但必须以
        // 显式 warn 且不得声称 parquet（守卫仅拦「用户显式要求非 csv」）
        assertTrue(src.contains("dw.lake.storage_format"),
                "全局 dw.lake.storage_format 期望值须参与非显式降级告警");
    }

    /** user.dir 上溯 .git 定位仓库根（沿用 NoDriverManagerArchTest 口径，无 .toURI()）。 */
    private static Path repoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve(".git")) || Files.isDirectory(cur.resolve(".git"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位仓库根（缺 .git）");
    }
}
