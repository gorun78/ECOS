package com.chinacreator.gzcm.engine.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F06-02（W144，详设-06 §F06-02 验收）— 四段裁决载荷（subject / action / resource /
 * environment）的离线护栏。
 *
 * <p>两条验收落点：</p>
 * <ul>
 *   <li>I-P1/I-P2/I-P3 — {@code payloadCarriesFourSectionsWithServerSideSubject}：</li>
 *       subject 段来自调用方（token 上下文取），匿名/空主体必须先拒；environment.channel
 *       恒 {@code AGENT}（不接受调用方覆盖）；timestamp 由服务端 {@link java.time.Instant} 填。</li>
 *   <li>I-P4 — {@code llmSuppliedUnauthorizedArgsNeverEnterPayload}：</li>
 *       LLM 入参里出现的<b>非白名单</b>键（如 {@code userId}/{@code role}/{@code department}/
 *       {@code tenantId}/{@code channel}）不得进入 {@code toWireBody()} 任何字段（防参数
 *       注入式越权，X-20）；五白名单字段（{@code scenario_id}/{@code forecast_run_id}/
 *       {@code data_scope}/{@code project_ids}/{@code periods}）则必须在。</li>
 * </ul>
 *
 * <p>本类以真实 payload 类实例化，桩调用方传入合法 token 上下文；不涉及 Spring / Kafka /
 * security REST —— 纯 POJO 合约锁。</p>
 */
class ToolAdjudicationPayloadTest {

    private static final String USER_ID = "user-42";

    // ── 验收 1：四段齐全 + token-context 主体 ─────────────────────────────

    @Test
    @DisplayName("F06-02 payloadCarriesFourSectionsWithServerSideSubject: 四段齐全；subject 来自服务端 token 上下文；"
            + "environment.channel 恒 AGENT；timestamp 服务端填")
    void payloadCarriesFourSectionsWithServerSideSubject() {
        Map<String, Object> llmArgs = new LinkedHashMap<>();
        llmArgs.put("scenario_id", "S-001");
        llmArgs.put("forecast_run_id", "FR-999");
        llmArgs.put("project_ids", List.of("P1", "P2"));
        llmArgs.put("periods", List.of("2026Q1", "2026Q2"));

        ToolAdjudicationRequest req = ToolAdjudicationRequest.fromServerSide(
                USER_ID, List.of("admin"), "corp-sec",
                "query_db", ToolAdjudicationRequest.ActionKind.READ,
                "trace-001", llmArgs);

        // subject：服务端 token 上下文（本例由调用方传入模拟 token 链路）
        ToolAdjudicationRequest.Subject subj = req.getSubject();
        assertEquals(USER_ID, subj.userId(), "subject.userId 应来自调用方传入的 token 上下文");
        assertEquals(List.of("admin"), subj.roles());
        assertEquals("corp-sec", subj.department());

        // action：type 固定 TOOL_CALL，tool 与 operation 由调用方给定
        ToolAdjudicationRequest.Action act = req.getAction();
        assertEquals(ToolAdjudicationRequest.ACTION_TYPE_TOOL_CALL, act.type());
        assertEquals("query_db", act.tool());
        assertEquals("read", act.operation(), "ActionKind.READ 应归一为小写 read");

        // environment：channel 恒 AGENT、traceId 原值、timestamp 服务端 Instant（非空 ISO-8601）
        ToolAdjudicationRequest.Environment env = req.getEnvironment();
        assertEquals(ToolAdjudicationRequest.CHANNEL_AGENT, env.channel(),
                "channel 必须恒 AGENT（不接受调用方覆盖）");
        assertEquals("trace-001", env.traceId());
        assertNotNull(env.timestamp());
        assertDoesNotThrow(() -> java.time.Instant.parse(env.timestamp()),
                "environment.timestamp 必须是服务端 Instant.now() 生成的 ISO-8601，禁调用方注入");
    }

    @Test
    @DisplayName("F06-02 I-P1: 匿名 / 空白 subject 必须拒构造（禁止未归属主体的裁决载荷下发）")
    void anonymousSubjectRejected() {
        assertThrows(IllegalStateException.class, () -> ToolAdjudicationRequest.fromServerSide(
                null, null, null, "query_db", ToolAdjudicationRequest.ActionKind.READ, null, Map.of()));
        assertThrows(IllegalStateException.class, () -> ToolAdjudicationRequest.fromServerSide(
                "   ", null, null, "query_db", ToolAdjudicationRequest.ActionKind.READ, null, Map.of()));
        assertThrows(IllegalStateException.class, () -> ToolAdjudicationRequest.fromServerSide(
                "anonymousUser", null, null, "query_db", ToolAdjudicationRequest.ActionKind.READ,
                null, Map.of()));
    }

    @Test
    @DisplayName("F06-02 I-P2/P3: 任何 LLM 入参 channel / timestamp 修改尝试无效（环境段不受 LLM 影响）")
    void channelAndTimestampImmutableToLlm() {
        Map<String, Object> sneakyArgs = new LinkedHashMap<>();
        sneakyArgs.put("channel", "ADMIN_CONSOLE");      // 自报 channel → 不允许
        sneakyArgs.put("timestamp", "1999-01-01T00:00:00Z"); // 自报时间 → 不允许
        sneakyArgs.put("traceId", "LLM_INJECTED_TRACE");        // 自报 traceId → 由 caller 决定，payload 不接受此键

        // 注意：traceId 参数是 caller 侧传入的 —— 传空；channel/timestamp 完全闭锁
        ToolAdjudicationRequest req = ToolAdjudicationRequest.fromServerSide(
                USER_ID, null, null, "query_db", ToolAdjudicationRequest.ActionKind.READ,
                null, sneakyArgs);

        ToolAdjudicationRequest.Environment env = req.getEnvironment();
        assertEquals(ToolAdjudicationRequest.CHANNEL_AGENT, env.channel(),
                "channel 恒 AGENT，即使 LLM 明写 channel=ADMIN_CONSOLE 也不得覆盖（I-P2）");
        assertEquals("", env.traceId(), "traceId 仅来自 call-site，不来自 LLM 入参");
        // timestamp 服务端填（1999 若被采纳即违反 I-P3）
        assertNotEquals("1999-01-01T00:00:00Z", env.timestamp(),
                "timestamp 由服务端 Instant 填，不采纳 LLM 提供的时间");

        // toWireBody 里的 environment 段同样闭锁
        Map<String, Object> wire = req.toWireBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> envWire = (Map<String, Object>) wire.get("environment");
        assertEquals(ToolAdjudicationRequest.CHANNEL_AGENT, envWire.get("channel"));
        assertEquals("", envWire.get("traceId"));
        assertNotEquals("1999-01-01T00:00:00Z", envWire.get("timestamp"));
    }

    // ── 验收 2：LLM 自报键不得进入 wire body（I-P4，X-20） ─────────────────

    @Test
    @DisplayName("F06-02 llmSuppliedUnauthorizedArgsNeverEnterPayload: LLM 自报 subject/tenant/channel "
            + "键零命中；白名单 5 键则在")
    void llmSuppliedUnauthorizedArgsNeverEnterPayload() {
        // LLM 恶意/意外请求：混入一切自报主语键与伪系统段
        Map<String, Object> hostile = new LinkedHashMap<>();
        // 未授权（应零命中自动化层，防参数注入）
        hostile.put("userId", "admin");             // 自报主体
        hostile.put("user_id", "admin2");
        hostile.put("role", "admin_role");
        hostile.put("department", "finance");
        hostile.put("tenantId", "tenant-x");
        hostile.put("channel", "BACKDOOR");
        hostile.put("isAdmin", true);
        hostile.put("roleClaims", List.of("root"));
        // 白名单（必须在）
        hostile.put("scenario_id", "S-WHITELIST");
        hostile.put("forecast_run_id", "FR-WL");
        hostile.put("data_scope", Map.of(
                "project_ids", List.of("P-A"),
                "periods", List.of("2026Q4")));

        ToolAdjudicationRequest req = ToolAdjudicationRequest.fromServerSide(
                USER_ID, List.of("viewer"), null,
                "sales-lookup", ToolAdjudicationRequest.ActionKind.READ,
                "", hostile);

        Map<String, Object> wire = req.toWireBody();
        String serialized = String.valueOf(wire); // 全载荷文本化（含嵌套段）

        // —— 未授权键：必须零命中（除 subject 段合法使用 userId 这一固定键名之外）
        // 这是 X-20 直接防线：LLM 塞 "role":"admin_role" 这类放进 payload → security 侧可能被 push policies 绕过
        assertFalse(serialized.contains("admin2"), "LLM 自报 user_id 值 'admin2' 不得入载荷");
        assertFalse(serialized.contains("admin_role"), "LLM 自报 role 值 'admin_role' 不得入载荷");
        assertFalse(serialized.contains("finance"), "LLM 自报 department 值 'finance' 不得入载荷");
        assertFalse(serialized.contains("tenant-x"), "LLM 自报 tenantId 值 'tenant-x' 不得入载荷");
        assertFalse(serialized.contains("BACKDOOR"), "LLM 自报 channel 值 'BACKDOOR' 不得入载荷（I-P2 同）");
        assertFalse(serialized.contains("root"), "LLM 自报 roleClaims 值 'root' 不得入载荷");

        // subject 段的 roles 由 call-site 传入（本例 "viewer"），不取 LLM 的 roleClaims
        @SuppressWarnings("unchecked")
        Map<String, Object> subj = (Map<String, Object>) wire.get("subject");
        assertEquals(List.of("viewer"), subj.get("roles"),
                "subject.roles 只能来自 call-site（token 上下文），不采纳 LLM 的 roleClaims");

        // —— 白名单键：必须在
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) wire.get("resource");
        assertEquals("S-WHITELIST", resource.get("scenarioId"));
        assertEquals("FR-WL", resource.get("forecastRunId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ds = (Map<String, Object>) resource.get("dataScope");
        assertEquals(List.of("P-A"), ds.get("projectIds"));
        assertEquals(List.of("2026Q4"), ds.get("periods"));
    }

    @Test
    @DisplayName("F06-02 resource 段扁平 / data_scope 嵌套 两种形态都能投影，且同型的自报键仍不入")
    void resourceFlatAndNestedBothProjectAndUnauthorizedStillExcluded() {
        // 扁平形态（在项目/周期两个字段直展）
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("project_ids", List.of("P-FLAT"));
        flat.put("periods", List.of("2025Q4"));
        ToolAdjudicationRequest flatReq = ToolAdjudicationRequest.fromServerSide(
                USER_ID, null, null, "t", ToolAdjudicationRequest.ActionKind.READ, null, flat);
        assertEquals(List.of("P-FLAT"),
                flatReq.getResource().dataScope().projectIds());

        // 嵌套形态（PROJECT_REQUEST emulation）
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("data_scope", Map.of("project_ids", List.of("P-NS"), "periods", List.of("Y2026")));
        ToolAdjudicationRequest nestedReq = ToolAdjudicationRequest.fromServerSide(
                USER_ID, null, null, "t", ToolAdjudicationRequest.ActionKind.READ, null, nested);
        assertEquals(List.of("P-NS"), nestedReq.getResource().dataScope().projectIds());
        assertEquals(List.of("Y2026"), nestedReq.getResource().dataScope().periods());

        // 空入参：resource 段回落到全空
        ToolAdjudicationRequest emptyReq = ToolAdjudicationRequest.fromServerSide(
                USER_ID, null, null, "t", ToolAdjudicationRequest.ActionKind.READ, null, null);
        assertTrue(emptyReq.getResource().dataScope().projectIds().isEmpty());
        assertTrue(emptyReq.getResource().dataScope().periods().isEmpty());
    }

    @Test
    @DisplayName("F06-02 ActionKind 归一：write/R/W 归 write，其它 → read（保守）")
    void operationNormalized() {
        assertEquals("write", ToolAdjudicationRequest.fromServerSide(
                USER_ID, null, null, "t", ToolAdjudicationRequest.ActionKind.WRITE, null, null)
                .getAction().operation());
        // 未知 kind 若传入 map 形态无法满足 enum → 由 call-site 归一；本处仅验证 enum 语义
        assertEquals("read", ToolAdjudicationRequest.fromServerSide(
                USER_ID, null, null, "t", ToolAdjudicationRequest.ActionKind.READ, null, null)
                .getAction().operation());
    }
}
