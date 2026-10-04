package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.AgentToolSqlWhitelist;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-01 咽喉裁决（W140/W141）离线验收 — 沿唯一对外入口
 * {@link ToolExecutorService#execute(String, Map)} 的显式阶段链
 * {@code policyEvaluate → sandboxReview → dispatch}，锁定三条不变式：
 * <ul>
 *   <li><b>I-1</b>：每次 execute() 恰好 1 次 ABAC 裁决
 *       （{@code policyGate.adjudicate} → {@code AiSecurityEngineClient.evaluate}）；</li>
 *   <li><b>X-11</b>：裁决对<b>所有工具类型</b>无条件前置（不再以 {@code "SQL"} 为条件）；
 *       非 SQL 工具（BUILTIN/REST/未登记）也必被裁决，DENY 桩下 0 执行；</li>
 *   <li><b>X-14</b>：高危沙盒审查器失败 → DENY（不再降级放行）。</li>
 * </ul>
 *
 * <p>全部边界（ABAC / whitelist / JdbcTemplate）以 Mockito 桩锁死，<b>不触 DB/网络</b>。
 * 离线无 token 上下文，故 {@code @BeforeEach} 手工写 {@link SecurityContextHolder} 供
 * {@code getCurrentUserId()} 取主体（否则 {@code adjudicate} 会在 ABAC 前短路，无法锁 I-1）；
 * {@code @AfterEach} 清空。验收名对应 doc §X.9.0 行 275-279；
 * {@code timeoutTwoSecondsFailClosed} 归 {@code AgentToolPolicyGateTest}（同包）。</p>
 */
class ToolGuardrailChokepointTest {

    private AiSecurityEngineClient security;
    private AgentToolPolicyGate gate;
    private ToolExecutorService svc;

    /**
     * 覆盖的 15 个具名工具：2 个纯 BUILTIN（允许桩下真执行、无 IO）+ 13 个"未登记/非纯"
     * （裁决后被「工具未找到」或后端不可用拦下，0 出向）。BUILTIN 只挑纯计算者，
     * 避免 {@code delegate_to_agent} 在无 AgentDelegationService 的 OFFLINE 下抛后端错、污染语义。
     */
    private static final List<String> TOOL_NAMES = List.of(
            "getCurrentTime",            // BUILTIN 纯计算
            "calculateRisk",             // BUILTIN 纯计算
            "delegate_to_agent",         // BUILTIN（但走 catch → 后端不可用 fail）
            "invoke_rest", "read_file", "write_file", "patch",      // doc 行 276 地名非 SQL 场景工具
            "knowledge-search", "object-query", "graph-query", "ontology-explore",
            "aggregation-query", "sales-lookup", "contract-read", "risk-write"
    );

    @BeforeEach
    void setUp() throws Exception {
        security = mock(AiSecurityEngineClient.class);
        AgentToolSqlWhitelist wl = mock(AgentToolSqlWhitelist.class);
        gate = new AgentToolPolicyGate(wl, security, 2000);

        svc = new ToolExecutorService();
        svc.setSqlSecurityGate(gate);
        svc.setSqlWhitelist(wl);
        svc.setJdbcTemplate(null);            // 默认无 DB：sandbox 附加防御短路；SQL 定义不可达
        ToolRegistry reg = mock(ToolRegistry.class);
        when(reg.has(anyString())).thenReturn(false); // 走 fallback/DB 路径，不桩 registry.execute
        inject(svc, "toolRegistry", reg);

        // 离线补 token 主体（getCurrentUserId 走 SecurityContext principal）
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-42", "pw", Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = ToolExecutorService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ── 验收 1：everyToolTypeReceivesExactlyOneEvaluate（I-1） ─────────────

    @Test
    @DisplayName("W140 everyToolTypeReceivesExactlyOneEvaluate: 15 工具各恰好 1 次 ABAC，无未裁决执行")
    void everyToolTypeReceivesExactlyOneEvaluate() {
        for (String name : TOOL_NAMES) {
            reset(security);
            when(security.evaluate(anyString(), any(), anyString(), any())).thenReturn(true); // ALLOW

            ToolExecutorService.ToolResult r = svc.execute(name, argsFor(name));

            // I-1：恰好 1 次 ABAC 裁决（不多不少）
            verify(security, times(1)).evaluate(anyString(), any(), anyString(), any());

            // 0 未裁决执行：执行（若发生）必在 times(1) ABAC 之后；未登记/非纯者须未越障。
            if (name.equals("getCurrentTime") || name.equals("calculateRisk")) {
                assertTrue(r.isSuccess(), name + ": 合法纯 BUILTIN 在允许桩下应成功执行");
            } else {
                assertNotNull(r.getError(),
                        name + ": 未登记/非纯工具应被拦下（工具未找到 / 后端不可用），不得越障成功");
            }
        }
    }

    // ── 验收 2：nonSqlToolsAreAlsoAdjudicated（X-11 作用域） ───────────────

    @Test
    @DisplayName("W140 nonSqlToolsAreAlsoAdjudicated: 5 非 SQL 工具 DENY 桩下 0 执行")
    void nonSqlToolsAreAlsoAdjudicated() {
        for (String name : List.of("invoke_rest", "read_file", "write_file", "patch", "delegate_to_agent")) {
            reset(security);
            when(security.evaluate(anyString(), any(), anyString(), any())).thenReturn(false); // DENY

            ToolExecutorService.ToolResult r = svc.execute(name, argsFor(name));

            assertFalse(r.isSuccess(), name + ": DENY 桩下必须拒绝执行");
            assertNotNull(r.getError());
            assertTrue(r.getError().contains("ABAC"),
                    name + ": 失败须携 ABAC 指纹（证被前置裁决拦下，非后端错误）, got=" + r.getError());
            // ABAC 恰好 1 次即被拦下 —— 非 SQL 也不得绕过
            verify(security, times(1)).evaluate(anyString(), any(), anyString(), any());
        }
    }

    // ── 验收 3：sandboxReviewerFailureYieldsDenyNotPass（X-14） ────────────

    @Test
    @DisplayName("W141 sandboxReviewerFailureYieldsDenyNotPass: 沙盒审查器不可用 → 高危指令 DENY")
    void sandboxReviewerFailureYieldsDenyNotPass() {
        reset(security);
        when(security.evaluate(anyString(), any(), anyString(), any())).thenReturn(true); // ABAC 放行

        // 强制高沙盒 profile：sandbox_mandatory = true
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("sandbox_mandatory"), anyString()))
                .thenReturn(List.of(Map.of("sandbox_mandatory", true)));
        svc.setJdbcTemplate(jdbc);

        // 高危指令（DROP）→ 高危正则命中 → 沙盒审查 → 服务 Bean 不可用(applicationContext=null) → 默认 DENY
        ToolExecutorService.ToolResult r =
                svc.execute("patch", Map.of("command", "DROP TABLE ecos_dw.t"));

        assertFalse(r.isSuccess(), "审查器不可用 + 高危指令必须 DENY（fail-closed），不得降级放行");
        assertNotNull(r.getError());
        assertTrue(r.getError().contains("fail-closed"), "失败须携 fail-closed 指纹, got=" + r.getError());
    }

    /** 各工具的 LLM 入参（无副作用参数；REST/场景工具目标统一未登记 → 裁决后不出向） */
    private Map<String, Object> argsFor(String name) {
        return switch (name) {
            case "invoke_rest" -> Map.of("url", "http://internal/svc/api");
            case "read_file", "write_file", "patch" -> Map.of("path", "/data/report.csv");
            case "calculateRisk" -> Map.of("probability", 0.6, "impact", 0.5, "severity", "medium");
            case "sales-lookup", "contract-read", "risk-write" -> Map.of("id", "x-1");
            default -> Map.of();
        };
    }
}
