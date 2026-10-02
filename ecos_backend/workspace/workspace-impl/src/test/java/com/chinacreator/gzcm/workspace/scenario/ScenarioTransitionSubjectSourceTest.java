package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.scenario.ScenarioStatusTransitionService.StatusVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F07-07 / C153 状态迁移「操作主体取自 token，禁 request-body」红线 — REQ-WS-01 矩阵点名项。
 *
 * <p>设计卷 §八 验收矩阵 REQ-WS-01 行点名
 * {@code ScenarioStatusMachineTest#transitionSubjectComesFromTokenNotRequestBody}：
 * {@code PATCH /scenarios/{id}/status} 的 {@code update_by} 必须来自 {@link SecurityContextHolder}
 * (服务侧 {@code currentOperator()})，<b>不得</b>由请求体携带操作人（否则前端可伪造任意主体
 * 迁移场景状态 = 提权 + 审计污染）。本方法签名 {@code transition(scenarioId, toStatus)}
 * 根本没有 actor 参数 ⇒ 结构上 operator 只有 SecurityContext 一个来源。</p>
 *
 * <p>本测试驱动<b>真实</b> {@code transition()}（非 mock service），仅 stub 依赖：
 * 捕获落库 {@code update_by} 值 = {@code currentOperator()} 的输出，直接证伪
 * "operator 来自 token principal"。若有人误加一个 {@code transition(id, to, operatorFromBody)}
 * 重载让 body 传 actor，此类的契约即告破。</p>
 */
class ScenarioTransitionSubjectSourceTest {

    private JdbcTemplate jdbc;
    private ScenarioStatusTransitionService svc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        ScenarioStatusMachine machine = new ScenarioStatusMachine();
        ScenarioCompletenessService completeness = mock(ScenarioCompletenessService.class);
        svc = new ScenarioStatusTransitionService(jdbc, machine, completeness);
        // DRAFT 态；无 FORMAL 运行（→ACTIVE 不需要）；无孤岛（激活闸放行）
        stubCurrentStatus("sc1", "DRAFT");
        when(jdbc.queryForObject(anyString(), same(Integer.class), eq("sc1"))).thenReturn(0);
        when(completeness.islandsOf("sc1")).thenReturn(List.of());
    }

    /** stub `SELECT status FROM ecos_business_scenario ... WHERE id=?`。 */
    private void stubCurrentStatus(String id, String status) {
        when(jdbc.queryForList(anyString(), same(String.class), eq(id)))
                .thenReturn(List.of(status));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("合法迁移时 update_by 取 token principal（DRAFT→ACTIVE，SecurityContext 已鉴权）")
    void transitionSubjectComesFromTokenNotRequestBody() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("token-user-777", "ignored", List.of()));

        AtomicReference<Object[]> updArgs = new AtomicReference<>();
        doAnswer(inv -> {
            // jdbc.update(String sql, Object... args) → arg0=sql, arg1=target, arg2=update_by, arg3=id
            Object[] a = { inv.getArgument(1), inv.getArgument(2), inv.getArgument(3) };
            updArgs.set(a);
            return 1;
        }).when(jdbc).update(anyString(), any(), any(), any());

        StatusVO vo = svc.transition("sc1", "ACTIVE");

        assertEquals("ACTIVE", vo.getStatus(), "DRAFT→ACTIVE 应迁移成功");
        Object[] args = updArgs.get();
        assertTrue(args != null, "迁移成功必须落 UPDATE（状态持久化）");
        assertEquals("ACTIVE", args[0], "update 第 1 个 bind 参 = 目标态");
        assertEquals("token-user-777", args[1],
                "update_by 必须 = SecurityContext principal（token 主体），非 request-body");
        assertEquals("sc1", args[2]);
    }

    @Test
    @DisplayName("无鉴权上下文时 update_by 兜底 system，而非任何客户端可传值")
    void noAuthContextFallsBackToSystemNotClientValue() {
        SecurityContextHolder.clearContext();

        AtomicReference<Object[]> updArgs = new AtomicReference<>();
        doAnswer(inv -> {
            Object[] a = { inv.getArgument(1), inv.getArgument(2), inv.getArgument(3) };
            updArgs.set(a);
            return 1;
        }).when(jdbc).update(anyString(), any(), any(), any());

        svc.transition("sc1", "ACTIVE");

        assertEquals("system", updArgs.get()[1],
                "无鉴权上下文 → 兜底 system（currentOperator 契约）；绝无客户端注入路径");
    }

    @Test
    @DisplayName("结构断言：transition 方法签名不含 actor/operator 参数（防 body 传操作人回潮）")
    void transitionSignatureHasNoActorParameter() throws Exception {
        List<String> params = new ArrayList<>();
        for (var m : ScenarioStatusTransitionService.class.getDeclaredMethods()) {
            if (m.getName().equals("transition")) {
                for (var p : m.getParameters()) {
                    params.add(p.getType().getSimpleName());
                }
            }
        }
        String joined = String.join(",", params).toLowerCase();
        assertTrue(!joined.contains("operator") && !joined.contains("username")
                        && !joined.contains("userid") && !joined.contains("actor"),
                "transition() 若出现 operator/username/userId/actor 类型的额外参数即允许 body 传主体 ⇒ 红线破，实参: " + params);
    }
}
