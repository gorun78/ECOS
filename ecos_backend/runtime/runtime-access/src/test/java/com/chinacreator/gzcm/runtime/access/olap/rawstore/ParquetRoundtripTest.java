package com.chinacreator.gzcm.runtime.access.olap.rawstore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-14 RAW parquet 写入（M2 建议实施，非 M0 必完成） — Roundtrip source-scan 与占位。
 *
 * <p>设计（详细设计-02 F02-14 ①）：{@code SINK_MINIO} 节点 {@code format: parquet}
 * → 经 runtime-access DuckDB {@code COPY (…) TO 'x.parquet' (FORMAT PARQUET)}
 * 落临时文件再送 MinIO；key = {@code raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{ts}.parquet}；
 * <b>铁律：不引入 parquet-mr 依赖</b>（DuckDB 自身完成 parquet I/O）。</p>
 *
 * <p><b>当前实现状态（2026-09-30 实查）= 缺口未闭合</b>：</p>
 * <ul>
 *   <li>{@code writeParquet / readParquet / COPY ... FORMAT PARQUET} 全仓 grep 零命中
 *       在 runtime-access 与 data-engine main 源码；</li>
 *   <li>{@code DuckDBQueryService.registerParquetView} 现是 no-op log
 *       （{@code OLAPEngineResolver} 管实际的引擎，parquet 只走 MinIO object name
 *       后缀识别）；</li>
 *   <li>{@code PipelineExecutionService.java:1220} 显式 business-exception
 *       拒绝 parquet 写入器（"无写入器"），{@code PipelineLakeNodeTest}
 *       已断言此行为。</li>
 * </ul>
 *
 * <p><b>本文件的现状收口</b>：</p>
 * <ul>
 *   <li>{@code parquetAbsent_today} (可启用)：source-scan 确认 M2 缺口仍在
 *       （{@code writeParquet} 未落地，避免伪装写成 csv 得了 parquet 声明——
 *       {@code PipelineExecutionService:1220} 已 fail-loud，本 test 保 baseline）。</li>
 *   <li>{@code @Disabled parquetRoundtrip} 占位：M2 落地后启用 100 行 write
 *       to temp dir(Mock MinIO bucket) + read-back 行数/列型/3 采样值一致断言。</li>
 * </ul>
 *
 * <p>约束：本文件不含 Windows 绝对路径；时使用 relative path（Maven surefire 工作目录
 * = runtime-access/）。</p>
 *
 * @author ECOS Runtime-Access Team / M2 backlog
 */
@DisplayName("F02-14 RAW parquet I/O 未落地 baseline（M2 建议实施）")
class ParquetRoundtripTest {

    private static final Path BACKEND_ROOT = Paths.get("..", "..")
            .toAbsolutePath().normalize();  // runtime-access/ → runtime/ → ecos_backend/

    @Test
    @DisplayName("Source-scan baseline：runtime-access + data-engine main 里没有 writeParquet/readParquet (M2 未落地)")
    void parquetAbsent_today() throws IOException {
        assertTrue(Files.isDirectory(BACKEND_ROOT), "backend root 未找到: " + BACKEND_ROOT);

        Set<String> writeHits = new LinkedHashSet<>();
        Set<String> readHits = new LinkedHashSet<>();
        // runtime-access 与 data-engine-impl 两个 main 目录并集扫描
        for (String rel : new String[] {
                "runtime/runtime-access/src/main/java",
                "engine/data-engine/data-engine-impl/src/main/java" }) {
            Path root = BACKEND_ROOT.resolve(rel);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target"))
                    .forEach(p -> {
                        try {
                            String src = Files.readString(p);
                            if (src.contains("writeParquet") || src.contains("COPY ")
                                    && src.contains("FORMAT PARQUET")) {
                                writeHits.add(BACKEND_ROOT.relativize(p).toString());
                            }
                            if (src.contains("readParquet")) {
                                readHits.add(BACKEND_ROOT.relativize(p).toString());
                            }
                        } catch (IOException e) {
                            fail("读文件失败: " + p);
                        }
                    });
            }
        }
        assertTrue(writeHits.isEmpty(),
                "M2 未落地时 main 不应出现 writeParquet / COPY ... FORMAT PARQUET: " + writeHits);
        assertTrue(readHits.isEmpty(),
                "M2 未落地时 main 不应出现 readParquet: " + readHits);
    }

    @Disabled("M2 backlog（F02-14 建议实施，非 M0）：待 DuckDBQueryService + MinioStorageService "
            + "协作落地 parquet I/O（Key = raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{ts}.parquet）"
            + "后启用；写入者应落在 SINK_MINIO 节点执行器/PIPELINE 侧（PipelineExecutionService.java:1220 "
            + "现在 fail-loud 拒绝），且遵守设计铁律：不新引 parquet-mr 依赖")
    @Test
    @DisplayName("(M2 behavior) 100 行 → 临时 parquet → read-back：行数/列型/3 采样值一致")
    void parquetRoundtrip() throws IOException {
        // M2 编码方向（Comp）：
        // 1. JdbcConnector 建临时 DuckDB 库；
        // 2. 100 行 → COPY <t> TO '<tmp>.parquet' (FORMAT PARQUET)；
        // 3. MinioStorageService.putObject("raw/structured/ds1/orders/dt=YYYY-MM-DD/orders_<ts>.parquet", bytes, "application/octet-stream")；
        // 4. 从 MinIO 取 bytes → 写临时文件 → read-back 100 行 + 3 采样值 = 写前；
        // 5. key 合规断言（raw/structured 前缀 + dt 分区分键 + {table}_{ts}.parquet 后缀）
        // 注意：pipeline 侧执行器不应直接调 DuckDB，应走 runtime-access Bean（铁律 2.5 #1）
    }
}
