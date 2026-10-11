package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.workspace.blueprint.BlueprintStatus;
import com.chinacreator.gzcm.workspace.blueprint.CapabilityReadService;
import com.chinacreator.gzcm.workspace.blueprint.IllegalBlueprintTransitionException;
import com.chinacreator.gzcm.workspace.blueprint.PublishBlockedException;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprint;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintDao;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintService;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintStateMachine;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 补充设计 v1.0 §16 <b>SUP-BP-02/03</b> 冻结测试：{@link ScenarioBlueprintService}
 * 发布/验收门槛（§6.6）+ 版本不可变 + 运行回链 digest（mock 级；端到端 live 记未执行）。
 *
 * <p>真实生产路径：release/validate/transition/revise 走真实
 * {@link ScenarioBlueprintStateMachine} 与真实 {@link ScenarioBlueprintValidator}
 * （capability 端口 mock，fail-closed 桩），仅 DAO 与事件总线 provider mock。</p>
 *
 * <p>注意（断言对齐实际生产行为）：{@link ScenarioBlueprint} 的 *_text 列 setter 为
 * 保留 no-op，校验器实际消费<b>类型化列表</b>（gaps/dependencies/capabilities/tests）
 * 序列化投影；{@code content_json} 当前为保留列（getContentJson() 恒 null），
 * 故 release 落库的 artifact_digest 实际为 SHA-256(空串)。</p>
 */
@ExtendWith(MockitoExtension.class)
class ScenarioBlueprintServiceTest {

    private static final String BP_ID = "bp-001";

    /** SHA-256("") — 生产态 content_json 为保留列（getContentJson() 恒 null）时 release 摘要实为空的 SHA-256。 */
    private static final String SHA256_EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Mock
    private ScenarioBlueprintDao dao;
    @Mock
    private CapabilityReadService capabilityReadService;
    @Mock
    private ObjectProvider<EventBusService> eventBusProvider;

    private ScenarioBlueprintService service;
    private ScenarioBlueprint row;

    @BeforeEach
    void setUp() {
        // 显式装配：状态机/校验器/ObjectMapper 用真实生产类，DAO 与事件总线 provider mock
        ScenarioBlueprintStateMachine machine = new ScenarioBlueprintStateMachine();
        ScenarioBlueprintValidator validator =
                new ScenarioBlueprintValidator(capabilityReadService, new ObjectMapper());
        service = new ScenarioBlueprintService(dao, machine, validator,
                new ObjectMapper(), eventBusProvider);
        row = validBlueprintRow("APPROVED");
        lenient().when(dao.findById(BP_ID)).thenReturn(List.of(row));
    }

    /** 截面：§6.6 四道静态门槛全过 + 审批链齐（列表 = 校验器真实消费口径；改单项 = 单门缺口）。 */
    private static ScenarioBlueprint validBlueprintRow(String status) {
        ScenarioBlueprint bp = new ScenarioBlueprint();
        bp.setId(BP_ID);
        bp.setScenarioId("sc-001");
        bp.setVersion(1);
        bp.setStatus(status);
        bp.setGoalStatement("goal statement");
        Map<String, Object> gap = new LinkedHashMap<>();
        gap.put("gapId", "g-1");
        gap.put("severity", "BLOCKER");
        gap.put("status", "RESOLVED");
        bp.setGaps(List.of(gap));
        ScenarioBlueprint.Dependency dep = new ScenarioBlueprint.Dependency();
        dep.setType("DATA_ASSET");
        dep.setId("t-1");
        dep.setRequired(true);
        dep.setStatus("PUBLISHED");
        bp.setDependencies(List.of(dep));
        ScenarioBlueprint.Capability cap = new ScenarioBlueprint.Capability();
        cap.setCapabilityId("DATA_PROFILE");
        cap.setContractVersion("v1");
        cap.setRequired(true);
        bp.setCapabilities(List.of(cap));
        ScenarioBlueprint.Test test = new ScenarioBlueprint.Test();
        test.setTestId("t-1");
        test.setPriority("P0");
        test.setStatus("PASSED");
        bp.setTests(List.of(test));
        bp.setApprovalRequired(1);
        bp.setApprovalRef("approval-9001");
        bp.setContextDigest("ctx-digest-abc");
        return bp;
    }

    private static ScenarioBlueprint.Test test(String id, String priority, String status) {
        ScenarioBlueprint.Test t = new ScenarioBlueprint.Test();
        t.setTestId(id);
        t.setPriority(priority);
        t.setStatus(status);
        return t;
    }

    private static Map<String, Object> gap(String gapId, String severity, String status) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("gapId", gapId);
        g.put("severity", severity);
        g.put("status", status);
        return g;
    }

    /** 让 required 能力契约可用（真实校验器 fail-closed：CapabilityReadService 桩默认不可用）。 */
    private void makeCapabilitiesAvailable() {
        lenient().when(capabilityReadService.availability(anyString(), any()))
                .thenReturn(CapabilityReadService.CapabilityAvailability.available());
    }

    // ═══════════════ SUP-BP-02: 发布门槛缺一即拒（§6.6 四道静态门 + 审批链门 + 状态机门） ═══════════════

    @ParameterizedTest(name = "缺单门[{0}]即拒, updateRelease 永不触发")
    @CsvSource(value = {"GAP", "DEP", "CAP", "TEST", "APPROVAL"})
    @DisplayName("SUP-BP-02: 验收门槛单门缺口即拒, DAO 发布写永不触发")
    void releaseRejectedWhenAnySingleGateMissing(String gate) {
        makeCapabilitiesAvailable();
        switch (gate) {
            case "GAP" ->
                    row.setGaps(List.of(gap("g-2", "BLOCKER", "OPEN")));
            case "DEP" -> {
                ScenarioBlueprint.Dependency dep = new ScenarioBlueprint.Dependency();
                dep.setType("DATA_ASSET");
                dep.setId("t-2");
                dep.setRequired(true);
                dep.setStatus("DRAFT");
                row.setDependencies(List.of(dep));
            }
            case "TEST" ->
                    row.setTests(List.of(test("t-9", "P0", "FAILED")));
            case "APPROVAL" ->
                    row.setApprovalRef("  ");
            default -> lenient().when(capabilityReadService.availability(eq("DATA_PROFILE"), any()))
                    .thenReturn(CapabilityReadService.CapabilityAvailability
                            .unavailable("capability center not wired"));
        }

        PublishBlockedException ex = assertThrows(PublishBlockedException.class,
                () -> service.release(BP_ID, "actor-1"));
        assertEquals(409, ex.getHttpStatus());
        // 静态四门未过 → BLUEPRINT_VALIDATION_FAILED；审批链门未过 → BLUEPRINT_APPROVAL_MISSING
        if ("APPROVAL".equals(gate)) {
            assertEquals("BLUEPRINT_APPROVAL_MISSING", ex.getCode());
        } else {
            assertEquals("BLUEPRINT_VALIDATION_FAILED", ex.getCode());
        }
        verify(dao, never()).updateRelease(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    @DisplayName("SUP-BP-02: 状态机门 - 非 APPROVED 态禁发布; 通用迁移禁直跳 RELEASED 绕道")
    void releaseOnlyFromApprovedAndGenericTransitionCannotBypassRelease() {
        makeCapabilitiesAvailable();
        row.setStatus("VALIDATING");

        IllegalBlueprintTransitionException ex = assertThrows(IllegalBlueprintTransitionException.class,
                () -> service.release(BP_ID, "actor-1"));
        assertEquals(409, ex.getHttpStatus());
        assertEquals("ILLEGAL_TRANSITION", ex.getCode());
        verify(dao, never()).updateRelease(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());

        // 通用迁移端点禁经 transition 直接进 RELEASED（发布须走专用门槛端点, §6.6）
        row.setStatus("APPROVED");
        IllegalBlueprintTransitionException bypass = assertThrows(
                IllegalBlueprintTransitionException.class,
                () -> service.transition(BP_ID, "RELEASED", "bypass", "actor-1"));
        assertEquals("RELEASE_VIA_RELEASE_ENDPOINT_ONLY", bypass.getCode());
        verify(dao, never()).updateTransition(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("SUP-BP-02: 全门通过 -> 发布成功, artifact_digest 落库 (= SHA-256(空串), content_json 为保留列), context_digest 原样落库")
    void releaseSucceedsWhenAllGatesPass() {
        makeCapabilitiesAvailable();
        when(dao.updateRelease(eq(BP_ID), eq("RELEASED"), eq(SHA256_EMPTY),
                eq("ctx-digest-abc"), eq("actor-1"), eq("APPROVED"))).thenReturn(1);
        when(eventBusProvider.getIfAvailable()).thenReturn(null); // 事件总线 provider 空 = 降级仅记日志

        service.release(BP_ID, "actor-1");

        ArgumentCaptor<String> digestCaptor = ArgumentCaptor.forClass(String.class);
        verify(dao, times(1)).updateRelease(eq(BP_ID), eq("RELEASED"), digestCaptor.capture(),
                eq("ctx-digest-abc"), eq("actor-1"), eq("APPROVED"));
        assertEquals(SHA256_EMPTY, digestCaptor.getValue(),
                "artifact_digest 取 content_json 的 SHA-256; 生产态该列为保留(null) -> 摘要为空的 SHA-256");
    }

    @Test
    @DisplayName("SUP-BP-02: validate 只读 - passed=false 带明细且不抛异常, 不触任何 DAO 写")
    void validateIsReadOnlyAndReturnsDetail() {
        row.setTests(List.of(test("t-3", "P0", "FAILED")));
        when(capabilityReadService.availability(anyString(), any()))
                .thenReturn(CapabilityReadService.CapabilityAvailability.unavailable("not wired"));

        Map<String, Object> data = service.validate(BP_ID);

        assertEquals(Boolean.FALSE, data.get("passed"));
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) data.get("reasons");
        assertFalse(reasons.isEmpty(), "缺口明细须非空 (P0 未过 + 能力不可用)");
        // never() 面全用 any()：anyString() 不匹配 null 实参，会让"0 次写"断言因实参不匹配而假绿
        verify(dao, never()).updateRelease(any(), any(), any(), any(), any(), any());
        verify(dao, never()).updateTransition(any(), any(), any(), any(), any(), any());
        verify(dao, never()).insert(any(ScenarioBlueprint.class));
        verify(dao, never()).updateApprovalRef(any(), any(), any());
    }

    // ═══════════════ SUP-BP-02: 版本不可变（发布行永不回改, 修订 = version+1 新行） ═══════════════

    @Test
    @DisplayName("SUP-BP-02: 版本不可变 - 发布行后续修订只读旧行 (insert 新行), 对已发布 id 任何 DAO 更新写不发生")
    void publishedVersionRowIsNeverMutatedByLaterOperations() {
        row.setStatus("RELEASED");
        when(dao.maxVersionOfScenario("sc-001")).thenReturn(1);

        service.revise(BP_ID, "actor-2");

        // 修订 = version+1 新 DRAFT 行, 旧发布行不被任何更新写触达
        ArgumentCaptor<ScenarioBlueprint> newRow = ArgumentCaptor.forClass(ScenarioBlueprint.class);
        verify(dao, times(1)).insert(newRow.capture());
        assertEquals("DRAFT", newRow.getValue().getStatus());
        assertEquals(2, newRow.getValue().getVersion(), "修订生成 version+1 新行");
        // never() 面全用 any()：anyString() 不匹配 null 实参，会让"0 次写"断言因实参不匹配而假绿
        verify(dao, never()).updateTransition(any(), any(), any(), any(), any(), any());
        verify(dao, never()).updateApprovalRef(any(), any(), any());
        verify(dao, never()).updateRelease(any(), any(), any(), any(), any(), any());
        // 状态机口径: RELEASED 起进入不可变区
        assertTrue(new ScenarioBlueprintStateMachine().immutableAfterRelease(
                BlueprintStatus.RELEASED));
        assertTrue(new ScenarioBlueprintStateMachine().immutableAfterRelease(
                BlueprintStatus.COMPLETED));
    }

    // ═══════════════ SUP-BP-03: 回链 digest（mock 级; 端到端 live 记「未执行(待实跑授权)」） ═══════════════

    @Test
    @DisplayName("SUP-BP-03 (mock 级): 发布落库同时携带 context_digest 与 artifact_digest 两回链列")
    void releasePersistsBothBacklinkDigestColumns() {
        makeCapabilitiesAvailable();
        when(dao.updateRelease(eq(BP_ID), eq("RELEASED"), eq(SHA256_EMPTY),
                eq("ctx-digest-abc"), eq("actor-1"), eq("APPROVED"))).thenReturn(1);
        when(eventBusProvider.getIfAvailable()).thenReturn(null);

        service.release(BP_ID, "actor-1");

        // context_digest = 请求行带入的 Context Pack 摘要 (§5.2 回链); artifact_digest = content SHA-256
        verify(dao).updateRelease(eq(BP_ID), eq("RELEASED"),
                eq(SHA256_EMPTY), eq("ctx-digest-abc"),
                eq("actor-1"), eq("APPROVED"));
        verify(eventBusProvider).getIfAvailable(); // 事件发布降级路径被执行（未装配 -> 仅日志, 不阻塞）
    }
}
