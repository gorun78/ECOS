package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §2.2 矩阵第 4 行第 1 格 —— 无权用户 / 页面查询
 * （可执行标识 {@code RlsDenyNoLeakTest}，#142/B4-3a）。
 *
 * <p><b>矩阵期望</b>：403 / 空集，<b>不泄露存在性</b>。</p>
 *
 * <p><b>本用例真正钉住的两件事</b>：</p>
 * <ol>
 *   <li><b>RLS 面的默认 DENY</b>：无任何命中策略时，合并谓词落到
 *       {@code predicateTemplate="1=1"} 且 {@code denyIfEmpty=true} ——
 *       执行侧（data-engine {@code QueryController}）据此拒绝而非放行；
 *       同时命中策略清单与参数集必须为空，返回体里不得出现"存在哪些策略/哪些表被管控"的元信息。</li>
 *   <li><b>OPA 面的 DENY 形态</b>：策略名已合规（单源 {@code data_access}，订正 B4-3c），
 *       本 harness 的 OPA 对端不可达 ⇒ fail-close {@code allow=false} +
 *       {@code policyState=OPA_UNAVAILABLE}，与 §2.4-8 默认 DENY 一致（如实呈现，非伪造）。</li>
 * </ol>
 *
 * <p><b>在册缺陷（不作为通过项）</b>：CLS 当前是<b>黑名单式</b>——
 * {@code getColumns} 以"请求的全部列"起步再减去 blocked，因此<b>无策略即全列可见</b>（<b>D-B4-6b</b>），
 * 与本行"空集不泄露"直接冲突；姊妹面 <b>D-B4-6a</b> 是 seed 的 {@code visible_cols='[]'} 占位
 * 在 allow 语义下把"屏蔽 2 列"放大成"整表 0 列可读"（见 {@code RlsProjOwnerTest}/{@code RlsDeptTest}）。
 * 该状态由 {@link #clsBlackListFailOpenIsRegisteredDefect()} 以特征化断言钉住，
 * 收口项为 B4-4（敏感列改 allow-list 默认拒绝 + seed 的 visible_cols 语义订正）。
 * 收口落地时这些特征化断言<b>必须同步改成 DENY 断言</b>，不是把它们删掉。</p>
 */
class RlsDenyNoLeakTest {

    private static PageChannelHarness harness;

    @BeforeAll
    static void setUp() throws IOException {
        harness = PageChannelHarness.create();
    }

    @Test
    @DisplayName("page — 无角色主体在三张表上 0 命中策略，谓词 1=1 且 denyIfEmpty=true（执行侧须拒）")
    void page() {
        List<String> tables = List.of(
                V246SeedFixtures.T_PERIOD, V246SeedFixtures.T_COST, V246SeedFixtures.T_RESOURCE);

        for (String table : tables) {
            Map<String, Object> rls = harness.applyRls(table, V246SeedFixtures.NO_ROLE_USER);

            assertEquals(List.of(), PageChannelHarness.policies(rls),
                    table + "：无权用户不得命中任何策略，且返回体不得带出策略元信息");
            assertEquals("1=1", PageChannelHarness.predicateTemplate(rls),
                    table + "：无策略时的兜底模板必须是 1=1（配合 denyIfEmpty 才成立）");
            assertTrue(PageChannelHarness.denyIfEmpty(rls),
                    table + "：denyIfEmpty 必须为 true，否则 1=1 等于全量放行");
            assertEquals(List.of(), PageChannelHarness.bindingsOf(rls),
                    table + "：无策略即无绑定，不得凭空调出上下文参数");

            Map<?, ?> params = (Map<?, ?>) rls.get("params");
            assertTrue(params == null || params.isEmpty(),
                    table + "：params 必须为空集（空清单，不得填占位默认值）");
        }
    }

    @Test
    @DisplayName("page — 裁决入口视角：无权用户的 bundle 不泄露策略/表元信息，OPA 面 DENY")
    void bundleCarriesNoMetadata() {
        DecisionBundle b = harness.decidePage(V246SeedFixtures.T_COST,
                V246SeedFixtures.COST_COLUMNS, V246SeedFixtures.NO_ROLE_USER);

        assertEquals("1=1", b.rls().get("predicateTemplate"));
        assertEquals(Boolean.TRUE, b.rls().get("denyIfEmpty"),
                "bundle 必须把 denyIfEmpty 透传给执行侧");
        assertEquals(List.of(), b.rls().get("bindings"), "bindings 必须为空列表");
        assertEquals("ecos." + OpaPolicyService.POLICY_DATA_ACCESS, b.opa().get("policyRef"));
        assertEquals(Boolean.FALSE, b.opa().get("allow"),
                "harness 的 OPA 对端不可达 ⇒ 如实 DENY 形态（AI/页面通道均不得据此放行）");
        assertEquals(OpaPolicyService.STATE_UNAVAILABLE, b.opaPolicyState(),
                "不可达必须落在不可用态（订正 B4-3c：策略名已合规，不再冒充异常兜底态）");

        // bundle 结构本身不含策略名/表清单字段：只允许 4 源 + 2 个 id（防"把命中过程回显给前端"）
        Map<String, Object> view = b.toMap();
        assertEquals(List.of("rls", "columns", "masks", "opa", "decisionId", "traceId"),
                List.copyOf(view.keySet()), "bundle 字段集不得扩张出策略元信息");
        assertFalse(String.valueOf(view).contains("rls-"),
                "bundle 文本表示里不得出现 seed 策略 id（存在性泄露）");
    }

    @Test
    @DisplayName("defect/D-B4-6b — CLS 黑名单式：无权用户当前得「全部列可见」= 本行「空集」期望未成立（特征化钉缺陷）")
    void clsBlackListFailOpenIsRegisteredDefect() {
        Map<String, Object> cls = harness.applyCls(V246SeedFixtures.T_COST,
                V246SeedFixtures.NO_ROLE_USER, V246SeedFixtures.COST_COLUMNS);

        // ↓ 断的是「现状」，不是「应然」。矩阵第4行应然是 visibleColumns 为空集。
        assertEquals(V246SeedFixtures.COST_COLUMNS, PageChannelHarness.visible(cls),
                "D-B4-6b 现状钉桩：CLS 无策略即全列可见；B4-4 收口后本断言须改为 DENY 空集断言");
        assertEquals(List.of(), PageChannelHarness.blocked(cls));

        DecisionBundle b = harness.decidePage(V246SeedFixtures.T_COST,
                V246SeedFixtures.COST_COLUMNS, V246SeedFixtures.NO_ROLE_USER);
        assertFalse(b.columnsDenied(),
                "同上：columnsDenied() 现状为 false（全列放行），收口后应为 true");
    }

    @Test
    @DisplayName("交叉 — 演示主体 u-none 的角色不在 seed 任何策略的 role_id 里（保证「无权」前提为真）")
    void noRoleUserReallyHasNoPolicy() {
        for (String roleId : harness.seed().seedRoleIds()) {
            assertFalse(V246SeedFixtures.rolesFor(V246SeedFixtures.NO_ROLE_USER).contains(roleId),
                    "u-none 不得持有 seed 角色 " + roleId + "，否则矩阵第4行前提失效");
        }
    }
}
