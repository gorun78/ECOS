package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.decision.DecisionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /decision} 的 {@code safeOpa} 载荷数据域腿（#142/B4-3d-3，差距 D-B4-20 收口面）。
 *
 * <p>本类只断<b>生产者实际发出的请求体</b>形态，策略结论由
 * {@code OpaProjOwnerTest}/{@code OpaDeptTest} 的 oracle 回放断 —— 分工的理由：
 * 回放用例的可比性钉要求"发出形态＝录制形态"，而本批新增的四种上下文组合
 * （声明两键/只声明域/只声明项目集/完全不声明）里只有两键态在 oracle 里有记录，
 * 其余三态若强行录进 oracle 就得改策略单源文件并重录全部 14 例，代价与本断言收益不称。</p>
 *
 * <p><b>能变红的点</b>：删掉 {@code safeOpa} 里写 {@code resource} 的三行 ⇒ 例 1/2/3 红；
 * 把"未声明也写空段"改回去 ⇒ 例 4 红；把 {@code subject.department} 的归一去掉 ⇒ 例 5 红。</p>
 */
class DecisionDataScopePayloadTest {

    private static final String TABLE = "ecos_dw.fc_cost_allocation_fact";

    /** 走一次唯一入口，返回生产实际发给护栏源的 input 段。 */
    private static Map<String, Object> sentInput(DecisionContext ctx,
                                                DecisionBundle.Purpose purpose) throws IOException {
        try (OpaOracleReplaySupport server = OpaOracleReplaySupport.start()) {
            server.setCase("fin_page");
            PageChannelHarness harness = PageChannelHarness.create(server.baseUrl());
            harness.decide(new AssetRef(PageChannelHarness.SCHEMA, TABLE, V246SeedFixtures.COST_COLUMNS),
                    ctx, purpose);
            Map<String, Object> sent = server.lastSentInput();
            assertNotNull(sent, "护栏源必须被咨询一次（未咨询＝本用例无从判断发出形态）");
            return sent;
        }
    }

    private static DecisionContext ctx(String orgId, DataScopeContract.Declared scope) {
        return DecisionContext.of("u-pj", "t-demo", orgId, null, List.of("project_owner"), "trace-ds")
                .withDataScope(scope);
    }

    @Test
    @DisplayName("声明两键 — resource.dataScope 到达护栏输入，键序 departmentId→projectIds")
    void declaredScopeReachesGuardrailInput() throws IOException {
        Map<String, Object> sent = sentInput(
                ctx("d01", new DataScopeContract.Declared("d01", List.of("p1"))),
                DecisionBundle.Purpose.AI);

        assertEquals("{dataScope={departmentId=d01, projectIds=[p1]}}",
                String.valueOf(sent.get("resource")),
                "发出形态必须与录制器 SCOPE 工厂同形（深比按文本，见 assertRequestMatches）");
    }

    @Test
    @DisplayName("只声明项目集 — 仍写 resource 段且不含 departmentId 空值（禁冒充声明了空域）")
    void projectOnlyDeclarationWritesOnlyThatKey() throws IOException {
        Map<String, Object> sent = sentInput(
                ctx("d01", new DataScopeContract.Declared(null, List.of("p1"))),
                DecisionBundle.Purpose.AI);

        Map<?, ?> resource = (Map<?, ?>) sent.get("resource");
        Map<?, ?> scope = (Map<?, ?>) resource.get("dataScope");
        assertEquals(List.of("p1"), scope.get("projectIds"));
        assertFalse(scope.containsKey("departmentId"),
                "写 \"departmentId\": null 会让策略侧把「未声明」读成「声明了 null 域」");
    }

    @Test
    @DisplayName("只声明所属域 — 项目集未声明即不写（策略侧据此落 AI_SCOPE_NOT_DECLARED）")
    void departmentOnlyDeclarationWritesOnlyThatKey() throws IOException {
        Map<String, Object> sent = sentInput(
                ctx("d01", new DataScopeContract.Declared("d01", List.of())),
                DecisionBundle.Purpose.AI);

        Map<?, ?> scope = (Map<?, ?>) ((Map<?, ?>) sent.get("resource")).get("dataScope");
        assertEquals("d01", scope.get("departmentId"));
        assertFalse(scope.containsKey("projectIds"), "空列表冒充声明会让「未声明」与「声明空集」同形");
    }

    @Test
    @DisplayName("未声明 — 顶层不出现 resource 键（页面/导出通道载荷逐字节不变的前提）")
    void undeclaredContextWritesNoResourceSegment() throws IOException {
        for (DecisionBundle.Purpose purpose : List.of(DecisionBundle.Purpose.PAGE,
                DecisionBundle.Purpose.EXPORT, DecisionBundle.Purpose.AI)) {
            Map<String, Object> sent = sentInput(ctx("d01", DataScopeContract.none()), purpose);
            assertFalse(sent.containsKey("resource"),
                    purpose + " 通道未声明数据域时不得出现 resource 段: " + sent);
        }
    }

    @Test
    @DisplayName("组织域 null — subject.department 发成 \"\" 而不是 null（拒绝理由不被伪造）")
    void unresolvedDepartmentNormalizesToEmptyString() throws IOException {
        Map<String, Object> sent = sentInput(
                ctx(null, new DataScopeContract.Declared("d01", List.of("p1"))),
                DecisionBundle.Purpose.AI);

        Map<?, ?> subject = (Map<?, ?>) sent.get("subject");
        assertTrue(subject.containsKey("department"), "键必须存在（缺键与空值在 rego 里同义，但显式写更稳）");
        assertEquals("", subject.get("department"),
                "发 null 会绕过 AI_SUBJECT_DEPARTMENT_UNRESOLVED、误落越权规则（dev 容器 /opa eval 实测：null→OUT_OF_SUBJECT_DEPARTMENT，\"\"→UNRESOLVED）");
    }

    @Test
    @DisplayName("oracle 往返 — 三份带 dataScope 的 /decision 形态记录经 read→write 原样再现")
    void oracleRecordedScopesRoundTrip() throws IOException {
        int shaped = 0;
        for (OpaOracleReplaySupport.OracleCase c : OpaOracleReplaySupport.cases()) {
            if (!c.decisionChainShaped() || !c.hasResourceScope()) continue;
            shaped++;
            Object recorded = c.input().get(DataScopeContract.KEY_RESOURCE);
            DataScopeContract.Declared parsed = DataScopeContract.read(recorded);
            assertEquals(OpaOracleReplaySupport.writeJson(recorded),
                    OpaOracleReplaySupport.writeJson(DataScopeContract.toResourceSegment(parsed)),
                    c.name() + " 的录制段经单源往返必须逐字再现（否则回放比的是两个形态）");
        }
        assertEquals(3, shaped, "带声明域的 /decision 形态例应为 proj_ai_p1/proj_ai_p2/dept_ai 三例");
    }
}
