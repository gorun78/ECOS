package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileDO;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileService;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileStore;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@code /api/v1/knowledge/profiles} 族的 <b>HTTP 状态行保真</b>行为门
 * （#142/B4-3e-32，收口 D-B4-59 在本类的类型见证腿；与 gateway
 * {@code AlertCloseEndpointContractTest}、kb {@code KbAssumptionDomainStatusFidelityTest} 同族）。
 *
 * <p><b>为什么必须有行为门</b>：common-api 的 {@code SecurityDenialHttpStatusRatchetTest}
 * 判据 A 只认「{@code return ApiResponse…} 这一源码形态」，对「形态已改、状态值却写错」
 * （如把 403 折成 {@code ResponseEntity.ok(...)}）是<b>结构性失明</b>的——那种写法在静态门上
 * 读数完全正常。能推翻它的只有本门这类「调方法、看 {@code getStatusCode()}」的行为断言。
 * 两门互补，缺一即半只眼。</p>
 *
 * <p><b>互补不是修辞，本批已实测</b>（变异取证，夹具用后已清、复扫 {@code MUTANT} = 0）：
 * 把 {@code publish()} 的 403 腿折成 {@code ResponseEntity.ok(...)}（MUTANT-M1）后，
 * 静态门 {@code SecurityDenialHttpStatusRatchetTest} 5/5 <b>全绿</b>（判据 A 抓不到、
 * 锚点 {@code contains("ResponseEntity")} 也抓不到），本门却 2 红
 * （{@code publishWithoutAdminRoleCarries403} / {@code publishWithoutRoleHeaderIsDeny}，
 * 均为 {@code expected: <403> but was: <200>}）。gateway 侧同构取证（MUTANT-M2，折
 * {@code close()} 的 503 腿）读数一致：静态门 5/5 全绿，
 * {@code AlertCloseEndpointContractTest#repositoryFailureIs503} 单红。
 * ⇒ 若只补静态门就宣布 D-B4-59 收口，「折叠」这一复现形态将长期无人报警。</p>
 *
 * <p>钉六件事：① 403 权限拒绝上状态行且机读码取 {@code ECOS-KB-062} 单源；② 409（061/060）
 * 与 403、404 三者互不折叠（§4.7.2 的分态在状态行上必须可达）；③ 503 底座不可用上状态行且取
 * {@code ECOS-KB-021}；④ 成功腿仍 200（补状态行不得把成功一并抬走）；⑤ 入参校验 400 上状态行；
 * ⑥ 方法返回类型为 {@code ResponseEntity}（签名回退即红，路径与入参签名一律未动）。</p>
 */
@ExtendWith(MockitoExtension.class)
class KbProfileStatusFidelityTest {

    @Mock private KbProfileService service;
    @Mock private KbProfileStore store;
    @Mock private ITaskManagementService taskManagement;

    private KbProfileController controller() {
        return new KbProfileController(service, store, taskManagement);
    }

    private static void assertStatusAndCodeSameNumber(ResponseEntity<? extends ApiResponse<?>> resp,
                                                      int expectedStatus,
                                                      String expectedMachineCode) {
        assertEquals(expectedStatus, resp.getStatusCode().value(),
                "状态行必须等于契约期望值（裸回时代恒 HTTP 200，§4.7.2 的分态在传输层不可见）");
        ApiResponse<?> body = resp.getBody();
        assertNotNull(body, "ResponseEntity 必须携带信封");
        assertEquals(expectedStatus, body.getCode(), "信封 code 必须与状态行同号");
        assertEquals(expectedMachineCode, body.getErrorCode(), "机读码必须取本域码表单源");
    }

    // ── ① 403 权限拒绝 ────────────────────────────────────────────────────

    @Test
    @DisplayName("publish 非 knowledge_admin ⇒ 403 上状态行 + ECOS-KB-062，禁折成成功或 409")
    void publishWithoutAdminRoleCarries403() {
        ResponseEntity<ApiResponse<KbProfileDO>> resp =
                controller().publish("p-1", "knowledge_viewer", "u-1");
        assertStatusAndCodeSameNumber(resp, KbErrorCodes.PERMISSION_DENIED.httpStatus(),
                KbErrorCodes.PERMISSION_DENIED.code());
        assertTrue(resp.getBody().getMessage().contains("knowledge_admin"),
                "拒绝文案必须给出所需角色，实际=" + resp.getBody().getMessage());
    }

    @Test
    @DisplayName("publish 缺角色头 ⇒ 同样 403（缺省不得当成放行）")
    void publishWithoutRoleHeaderIsDeny() {
        ResponseEntity<ApiResponse<KbProfileDO>> resp = controller().publish("p-1", null, "u-1");
        assertStatusAndCodeSameNumber(resp, 403, KbErrorCodes.PERMISSION_DENIED.code());
    }

    // ── ② 409 / 404 分态互不折叠 ──────────────────────────────────────────

    @Test
    @DisplayName("publish 撞 I-6（ECOS-KB-061）⇒ 409 上状态行，不与 403 互折")
    void publishedImmutableIs409() {
        when(service.publish(anyString(), anyString()))
                .thenThrow(new IllegalStateException("ECOS-KB-061 已发布资产不可变"));
        ResponseEntity<ApiResponse<KbProfileDO>> resp =
                controller().publish("p-2", "knowledge_admin", "u-2");
        assertStatusAndCodeSameNumber(resp, 409, "ECOS-KB-061");
    }

    @Test
    @DisplayName("publish 非法跃迁（ECOS-KB-060）⇒ 409 上状态行")
    void illegalTransitionIs409() {
        when(service.publish(anyString(), anyString()))
                .thenThrow(new IllegalStateException("ECOS-KB-060 状态机非法跃迁"));
        ResponseEntity<ApiResponse<KbProfileDO>> resp =
                controller().publish("p-3", "knowledge_admin", "u-3");
        assertStatusAndCodeSameNumber(resp, 409, "ECOS-KB-060");
    }

    @Test
    @DisplayName("publish 领域异常无码 ⇒ 404 上状态行（不得把「查无」折成「无权限」）")
    void unknownProfileIs404Not403() {
        when(service.publish(anyString(), anyString()))
                .thenThrow(new IllegalStateException("profile not found"));
        ResponseEntity<ApiResponse<KbProfileDO>> resp =
                controller().publish("p-4", "knowledge_admin", "u-4");
        assertEquals(404, resp.getStatusCode().value(),
                "「记录不存在」与「非管理员」必须分态（403 触发权限申诉、404 触发取数排查）");
        assertEquals(404, resp.getBody().getCode(), "信封 code 与状态行同号");
    }

    // ── ③ 503 底座不可用 ──────────────────────────────────────────────────

    @Test
    @DisplayName("generate 提交失败 ⇒ 503 上状态行 + ECOS-KB-021，禁降级为本地同步计算")
    void generateTaskBaseUnavailableIs503() throws TaskManagementException {
        when(taskManagement.submitTask(any(TaskDescription.class)))
                .thenThrow(new TaskManagementException("runtime-task 不可达"));
        KbProfileController.ProfileGenerateRequest req = new KbProfileController.ProfileGenerateRequest();
        req.setMetricCodes(List.of("sales"));
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller().generate(req);
        assertStatusAndCodeSameNumber(resp, KbErrorCodes.TASK_BASE_UNAVAILABLE.httpStatus(),
                KbErrorCodes.TASK_BASE_UNAVAILABLE.code());
    }

    // ── ④⑤ 成功腿与入参校验腿 ────────────────────────────────────────────

    @Test
    @DisplayName("generate 成功 ⇒ 状态行仍是 200（补状态行不得把成功腿一并抬走）")
    void generateSuccessStays200() throws TaskManagementException {
        when(taskManagement.submitTask(any(TaskDescription.class))).thenReturn("kbpg-1");
        KbProfileController.ProfileGenerateRequest req = new KbProfileController.ProfileGenerateRequest();
        req.setMetricCodes(List.of("sales"));
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller().generate(req);
        assertEquals(200, resp.getStatusCode().value(), "成功腿状态行必须 200");
        assertEquals(0, resp.getBody().getCode(), "成功信封 code=0");
        assertEquals("kbpg-1", resp.getBody().getData().get("taskId"));
    }

    @Test
    @DisplayName("generate 缺 metricCodes ⇒ 400 上状态行（入参校验不得报成成功）")
    void generateWithoutMetricsIs400() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller().generate(new KbProfileController.ProfileGenerateRequest());
        assertEquals(400, resp.getStatusCode().value(), "参数校验失败必须落 400");
        assertEquals(400, resp.getBody().getCode(), "信封 code 与状态行同号");
    }

    @Test
    @DisplayName("publish 成功 ⇒ 200 + data 透传")
    void publishSuccessStays200() {
        KbProfileDO row = new KbProfileDO();
        when(service.publish(anyString(), anyString())).thenReturn(row);
        ResponseEntity<ApiResponse<KbProfileDO>> resp =
                controller().publish("p-5", "knowledge_admin", "u-5");
        assertEquals(200, resp.getStatusCode().value());
        assertSame(row, resp.getBody().getData(), "成功腿必须原样透传领域对象");
    }

    // ── ⑥ 签名回退锚 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("反射钉：generate/publish 返回体为 ResponseEntity，路径与入参签名一律未动")
    void signaturesAreResponseEntityWithUnchangedPaths() throws Exception {
        Method generate = KbProfileController.class.getMethod("generate",
                KbProfileController.ProfileGenerateRequest.class);
        Method publish = KbProfileController.class.getMethod("publish",
                String.class, String.class, String.class);
        assertEquals(ResponseEntity.class, generate.getReturnType(),
                "generate 返回体退回 ApiResponse＝状态行将恒 200（D-B4-59 复现）");
        assertEquals(ResponseEntity.class, publish.getReturnType(),
                "publish 返回体退回 ApiResponse＝403/409/404 在传输层同形");
        assertTrue(generate.isAnnotationPresent(
                        org.springframework.web.bind.annotation.PostMapping.class),
                "generate 仍是 POST 腿（本批只补状态行，不动方法类型）");
    }
}
