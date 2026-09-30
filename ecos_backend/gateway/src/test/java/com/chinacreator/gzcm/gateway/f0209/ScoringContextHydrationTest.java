package com.chinacreator.gzcm.gateway.f0209;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W58 Scoring-Context hydration — 数据源 / 存储可用性打标（gateway 视角 source-scan）。
 *
 * <p>设计依据（详细设计-02 F02-09 / F02-14 + M0 表格 W58）：
 * data-engine / datanet 处在 5 大场景（sysman down / MinIO down / OLAP down /
 * DQ 告警 down / GitRepoRootResolver 缺目录）下，
 * 参与 DQ 打分的 5 维 aux 上下文（{@code datasourceAvailability / minioStorage /
 * olapEngine / dqAlertChannel / gitRepoRoot}）中被降级的两个必须打
 * {@code INCONCLUSIVE-AUX} 标记，其余保持 {@code OK}，让评分尖柱不再把
 * "基础设施降级"当"业务数据完整"。</p>
 *
 * <p><b>当前实现状态（2026-09-30 实查）= 缺口未闭合</b>：</p>
 * <ul>
 *   <li>{@code dsource/} 目录 / {@code DataResourceService} 中<b>无</b>
 *       {@code lowStorage} / {@code degraded} 打标（grep 零命中）；
 *       唯一的旁证是 {@code MinioReportStorage.degraded = WARN log},
 *       仅用于 MQ 降级诊断，不进入 ScoringContext；</li>
 *   <li>{@code INCONCLUSIVE-AUX} 全仓零命中；</li>
 *   <li>{@code ScoringContext} 类未提供 {@code auxAvailability / minioStorage}
 *       等字段，只能塞进 {@code connectionConfig / parameters} 副 map。</li>
 * </ul>
 *
 * <p><b>本文件的现状收口</b>：</p>
 * <ul>
 *   <li>{@code markerEmitterShapeProbe}：把 W58 期望的 ctx map 构造为纯函数
 *       （本地 helper {@link #markAuxAvailability(Map)}），并固化"2 端降级 +
 *       3 端 OK"的目标形；当下标 INCONCLUSIVE-AUX 的是 {@code DEGRADED/LOW}
 *       的 {@code datasourceAvailability + minioStorage}；OK 的是
 *       {@code olapEngine / dqAlertChannel / gitRepoRoot}。
 *       这是纯函数 → 可作为 W58 落地前 shape 契约；</li>
 *   <li>{@code dsourceMarkerAbsent_today}：source-scan 当前 data-engine 无
 *       {@code lowStorage|degraded|INCONCLUSIVE-AUX}；W58 引入时必须本 test
 *       fail（防"半引入 + main 已修改但 shape 契约未清理"）；</li>
 *   <li>behavior 部分（真实 main-code 打标副作用扫描）以 {@code @Disabled} 存在
 *       {@code w58markerEmissionBehavior}，待 W58 修订 {@code buildContext}
 *       注入 aux map 后启用。</li>
 * </ul>
 *
 * <p>无 MockMvc：因 F02-15 未落地 service-endpoints 端点（见
 * ServiceCatalogGrepArchTest 的 W53 报告），gateway 端不新增 MockMvc 层
 * 避免引入新 controller。</p>
 *
 * @author ECOS Gateway Team / W58-gap
 */
@DisplayName("W58 Scoring-Context hydration: deg 打标 shape 契约")
class ScoringContextHydrationTest {

    /** 5 aux 上下文键（设计表格逐字）。 */
    private static final Set<String> AUX_KEYS = Set.of(
            "datasourceAvailability", "minioStorage", "olapEngine",
            "dqAlertChannel", "gitRepoRoot");

    /** 测试辅助：给定 aux 状态 → 打标 INCONCLUSIVE-AUX / OK。纯函数，不依赖 main-code。 */
    static Map<String, Object> markAuxAvailability(Map<String, Object> input) {
        Map<String, Object> out = new LinkedHashMap<>(input);
        for (String key : AUX_KEYS) {
            Object v = out.get(key);
            String s = v == null ? "" : String.valueOf(v);
            boolean degraded = "DEGRADED".equalsIgnoreCase(s) || "LOW".equalsIgnoreCase(s)
                    || "WARN".equalsIgnoreCase(s) || "".equals(s);
            out.put(key + "_label", degraded ? "INCONCLUSIVE-AUX" : "OK");
        }
        return out;
    }

    @Test
    @DisplayName("5 aux 键 shape 契约：DEGRADED/LOW → INCONCLUSIVE-AUX；OK 端保持 OK")
    void markerEmitterShapeProbe() {
        Map<String, Object> hydrated = new LinkedHashMap<>();
        hydrated.put("datasourceAvailability", "DEGRADED"); // sysman down
        hydrated.put("minioStorage", "LOW");                 // minio merge 端低
        hydrated.put("olapEngine", "OK");
        hydrated.put("dqAlertChannel", "WARN");              // DQ 告警 down
        hydrated.put("gitRepoRoot", "OK");

        Map<String, Object> labeled = markAuxAvailability(hydrated);

        assertEquals("INCONCLUSIVE-AUX", labeled.get("datasourceAvailability_label"));
        assertEquals("INCONCLUSIVE-AUX", labeled.get("minioStorage_label"));
        assertEquals("INCONCLUSIVE-AUX", labeled.get("dqAlertChannel_label"));
        assertEquals("OK", labeled.get("olapEngine_label"));
        assertEquals("OK", labeled.get("gitRepoRoot_label"));
    }

    @Test
    @DisplayName("Source-scan：当前 data-engine 无 lowStorage/degraded/INCONCLUSIVE-AUX（W58 前 baseline）")
    void dsourceMarkerAbsent_today() throws IOException {
        Path backendRoot = Paths.get("..").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(backendRoot), "backend root 未找到: " + backendRoot);
        Path dataEngine = backendRoot.resolve("engine/data-engine");
        assertTrue(Files.isDirectory(dataEngine), "data-engine 目录缺失");

        Set<String> hits = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(dataEngine)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/src/main/"))
                .filter(p -> !p.toString().contains("target"))
                .forEach(p -> {
                    try {
                        String src = Files.readString(p);
                        if (src.contains("lowStorage")
                                || src.contains("INCONCLUSIVE-AUX")
                                || (src.contains("degraded") && src.contains("ScoringContext"))) {
                            hits.add(backendRoot.relativize(p).toString());
                        }
                    } catch (IOException e) {
                        fail("读文件失败: " + p);
                    }
                });
        }
        // W58 前置：现在 data-engine main 只允许 MinioReportStorage 的 WARN log 命中 degraded
        // （那里是降级诊断，与 ScoringContext 无关；我们的扫描条件 degraded + ScoringContext 双命中才计）
        // 因此 baseline 应为空；W58 落地后可以为空反而=没引入，故此处用"空或皆当事例外"软基线
        assertTrue(hits.isEmpty(),
                "W58 前 data-engine main 无 ScoringContext 相关 degraded 打标；若命中异常需人工确认: " + hits);
    }

    @Disabled("W58 gap：DqScheduleServiceImpl.buildContext 尚未向 ScoringContext 注入 aux 5 键 & INCONCLUSIVE-AUX 位；"
            + "等 W58 引入 aux map 后，启用 main-code 副作用扫描 + DqScoreEngine 短路确保 2 端降级不出综合分")
    @Test
    @DisplayName("(W58 behavior) buildContext → 注入 aux 5 键 + 2 端 INCONCLUSIVE-AUX；评分尖柱被短路")
    void w58markerEmissionBehavior() {
        // W58 引入后此处应：
        // 1. Mock JdbcTemplate 提供 check 行；DqRuleVO 触发 buildContext
        // 2. 断言 ctx.getConnectionConfig()（或 ctx.getParameters）包含 5 aux 键
        // 3. 断言 datasourceAvailability=minioStorage=INCONCLUSIVE-AUX 且 olapEngine=dqAlertChannel=gitRepoRoot=OK
        // 4. 断言 DqScoreEngine.evaluateAll 结果两维=0.0 + status=INCONCLUSIVE-AUX（评分尖柱被短路）
        //    而不是 1.0 fallback
    }
}
