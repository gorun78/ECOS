package com.chinacreator.gzcm.engine.kb.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;

/**
 * W84 / C68（详细设计-03 §七.1，M0 P0，O-6，分册 02 同名护栏扩展至 ontology 面）——
 * kb 经 REST 消费本体映射契约（{@code GET /api/v1/ontology/entity-mappings}）时，
 * <b>本体端点不可用必须 fail-loud</b>：抽取报告须落 {@code MAPPING_UNAVAILABLE} 问题码，
 * 禁"看似成功、零告警"的静默吞（否则实例抽取在未取到任何映射契约的情况下谎报"完成、0 节点"，
 * 前端无法区分"真无数据"与"契约探针挂了"）。
 *
 * <p>本护栏锁三种<b>可辨识</b>的失败/降级语义彼此不混淆（只读态，meta 端点）：</p>
 * <ul>
 *   <li>{@code MAPPING_UNAVAILABLE}：本体映射端点抛异常（自环指向未运行端口 / 5xx / 超时）
 *       —— 真降级，须显式落告警（本护栏主断言）；</li>
 *   <li>映射端点可达但返回空 &rarr; <b>不</b>落 {@code MAPPING_UNAVAILABLE}（真实完成态，白线对照）；</li>
 *   <li>本体快照缺失（无实例可抽）&rarr; 落 {@code NO_SNAPSHOT}，也<b>不</b>触发 {@code MAPPING_UNAVAILABLE}
 *       （判定范围与映射可用性解耦，避免误报）。</li>
 * </ul>
 *
 * <p><b>寻址注记（O-6 / W84 两态）</b>：本服务 {@code ontologyApiBase} 由
 * {@code ecos.ontology-api-base} 驱动（既有 {@code @Value} 默认 {@code http://localhost:8080/api/v1}
 * = monolith 自环态）。切两态寻址（monolith :8080 / service :18083，经 {@code ServiceEndpointResolver}
 * / {@code route-manifest.json}）的<b>改码</b>与 ADR-15 切 S3 同批，属授权闸项——本护栏不重排寻址字面量
 * （per 分册 02 W55 同族护栏刻意匹配 :180xx 的口径，:8080 monolith 态默认值不视为违规），只锁
 * <b>失败不得静默</b>这一可离线验证的语义底线。</p>
 *
 * <p>离线：不触库（{@code JdbcTemplate} mock，仅 {@code readSnapshots} 读）、不 Spring 容器、
 * 不联网（{@code RestTemplate} mock，仅按 URL 前缀打桩）。</p>
 */
@DisplayName("W84/C68 跨引擎取数（kb→ontology 映射契约）失败 fail-loud，禁静默吞")
class CrossEngineFailLoudTest {

    private JdbcTemplate jdbc;
    private RestTemplate restTemplate;

    private KbEntityInstanceExtractionService service() {
        return new KbEntityInstanceExtractionService(jdbc, restTemplate,
                "http://localhost:8080", "http://localhost:8080/api/v1", true);
    }

    @BeforeEach
    void setUp() {
        this.jdbc = mock(JdbcTemplate.class);
        this.restTemplate = mock(RestTemplate.class);
    }

    /** DW 层 CURATED 资源端点（datanetBaseUrl 前缀）固定返回空资源（抽取始终会调用，用 when）。 */
    private void stubEmptyDwResources() {
        when(restTemplate.getForObject(ArgumentMatchers.startsWith("http://localhost:8080/api/v1/engine/data"), any(Class.class)))
                .thenReturn(Map.of("data", Map.of("resources", List.of())));
    }

    /** 快照表返回一条本体快照（使抽取进入映射契约拉取分支）。 */
    private void stubSnapshot() {
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(List.of(Map.<String, Object>of("ontology_id", "ONT-1", "version", "1", "entity_codes", "[]")));
    }

    private boolean hasIssue(EntityInstanceExtractionReportVO report, String code) {
        return report.getIssues().stream().anyMatch(i -> code.equals(i.getCode()));
    }

    @Test
    @DisplayName("本体映射端点不可用（抛异常）→ 报告落 MAPPING_UNAVAILABLE，禁静默成功")
    void mappingEndpointUnavailable_isLoud() {
        stubSnapshot();
        // 注册顺序：mappings 先、data 后——Mockito 末注册命中优先，故 data URL 命中 data 桩、mappings URL 回退 mappings 桩
        when(restTemplate.getForObject(ArgumentMatchers.startsWith("http://localhost:8080/api/v1/ontology/entity-mappings"), any(Class.class)))
                .thenThrow(new RuntimeException("connection refused (self-loop to unstarted port)"));
        stubEmptyDwResources();

        EntityInstanceExtractionReportVO report = service().extract("ONT-1", "job-1", false, false);

        assertTrue(hasIssue(report, "MAPPING_UNAVAILABLE"),
                "本体映射端点不可用时报告必须落 MAPPING_UNAVAILABLE（fail-loud），实际 issues="
                        + describe(report));
        assertTrue(report.getNodeCreated() == 0 && report.getNodeUpdated() == 0,
                "映射不可用不应产出任何实例节点（既禁静默成功，也禁假成功数据）");
    }

    @Test
    @DisplayName("白线 — 映射端点可达但返回空 → NOT MAPPING_UNAVAILABLE（真完成态与降级态不混淆）")
    void mappingThreadedButEmpty_isNotUnavailable() {
        stubSnapshot();
        when(restTemplate.getForObject(ArgumentMatchers.startsWith("http://localhost:8080/api/v1/ontology/entity-mappings"), any(Class.class)))
                .thenReturn(Map.of("data", List.of()));
        stubEmptyDwResources();

        EntityInstanceExtractionReportVO report = service().extract("ONT-1", "job-2", false, false);

        assertFalse(hasIssue(report, "MAPPING_UNAVAILABLE"),
                "映射端点可达且返回空 ≠ 端点不可用（不得误报降级），实际 issues=" + describe(report));
    }

    @Test
    @DisplayName("白线 — 无本体快照 → 不误报 MAPPING_UNAVAILABLE（判定范围与映射可用性解耦）")
    void noSnapshots_doesNotFalseReportMappingUnavailable() {
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(List.of());

        EntityInstanceExtractionReportVO report = service().extract("ONT-1", "job-3", false, false);

        assertFalse(hasIssue(report, "MAPPING_UNAVAILABLE"),
                "无快照时不应误报 MAPPING_UNAVAILABLE，实际 issues=" + describe(report));
    }

    private static String describe(EntityInstanceExtractionReportVO report) {
        return report.getIssues().stream().map(i -> i.getCode() + ":" + i.getMessage())
                .collect(java.util.stream.Collectors.joining("; "));
    }
}
