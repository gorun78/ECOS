package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.nav.INavService;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategorySaveDTO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategoryVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavNodeStatsVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavRecommendVO;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * {@code /api/v1/knowledge/nav/*} 族的 <b>HTTP 状态行保真</b>行为门
 * （#142/B4-3e-33，收口 D-B4-59 在本类的 13 枚 403 裸回腿＋同处共面的 44 枚非拒绝腿）。
 *
 * <p><b>为什么必须有行为门</b>：common-api 的 {@code SecurityDenialHttpStatusRatchetTest}
 * 判据 A 只认「{@code return ApiResponse…} 这一源码形态」，对「形态已改成
 * {@code ResponseEntity} 而状态值却折成 200」是结构性失明的（#142/B4-3e-32 已用两枚
 * 真仓变异实测过这一点）。本类的方法名又与 {@code KbProfileController} 不同族，
 * 任何按名字/形态匹配的静态判据都可能漏掉这里，能推翻「写法对了、值错了」的只有
 * 「调方法、读 {@code getStatusCode()}」的行为断言。</p>
 *
 * <p><b>本门的单源不变式</b>：状态行取信封 {@code code} 经
 * {@code DataBridgeException.resolveHttpStatus(int)} 推出 ⇒ 「改码即改行」，本门断言
 * 二者同号，任何一处另写literal都会在这里翻红。{@code internalError} 腿按信封 v1
 * 负数码约定 {@code code=-1}，{@code resolveHttpStatus(-1)→500} ⇒ 本门把「行 500／信封 -1」
 * 作为<b>已登记的在册缺口</b>（D-B4-65「internalError code=-1 约定改判」）显式钉住，
 * 既不让它静默，也不在本批私改信封 code。</p>
 *
 * <p>钉八件事：① 403 上状态行；② 404 上状态行且与 403 不互折；③ 400 上状态行；
 * ④ 捕获兜底腿落 500；⑤ 成功腿仍 200（补状态行不得把成功一并抬走）；⑥ Void 出口
 * （delete）的四态全链；⑦ {@code recommend} 的 LLM 不可用兜底仍是 200 成功（既有合规
 * 兜底语义不因本批改动）；⑧ 方法返回类型为 {@code ResponseEntity}（签名回退即红），
 * 而 {@code modulesList} 作为无失败出口的方法必须<b>保持</b> {@code ApiResponse} 原签名。</p>
 */
@ExtendWith(MockitoExtension.class)
class NavTaxonStatusFidelityTest {

    @Mock private INavService navService;

    private NavTaxonController controller() {
        return new NavTaxonController(navService);
    }

    private static void assertStatusAndEnvelopeCode(ResponseEntity<? extends ApiResponse<?>> resp,
                                                    int expectedStatus,
                                                    int expectedEnvelopeCode) {
        ApiResponse<?> body = resp.getBody();
        assertNotNull(body, "ResponseEntity 必须携带信封");
        assertEquals(expectedStatus, resp.getStatusCode().value(),
                "状态行必须等于契约期望值（裸回时代恒 HTTP 200，§4.7.2 的分态在传输层不可见）");
        assertEquals(expectedEnvelopeCode, body.getCode(),
                "信封 code 必须落在登记的单源口径上（本类＝取信封 code 推状态行，两处同号）");
    }

    /** 状态行与信封 code 同号（400/403/404 三态走这条；500 腿因 code=-1 走显式两值断言）。 */
    private static void assertSameNumber(ResponseEntity<? extends ApiResponse<?>> resp, int expected) {
        assertStatusAndEnvelopeCode(resp, expected, expected);
    }

    /**
     * 成功腿：状态行 200，信封 {@code code} 取 {@code CODE_SUCCESS}（=0）而非 200。
     *
     * <p>与 {@link #assertSameNumber} 分开，是因为本类信封的成功码从来是 0（信封 v1 约定），
     * 把两者混成一条判据会在补状态行时误把成功信封 code 一起改掉＝改了 API 面。</p>
     */
    private static void assertSuccess(ResponseEntity<? extends ApiResponse<?>> resp) {
        ApiResponse<?> body = resp.getBody();
        assertNotNull(body, "ResponseEntity 必须携带信封");
        assertEquals(200, resp.getStatusCode().value(),
                "成功腿必须留在 200（补状态行不得把成功一并抬走）");
        assertEquals(ApiResponse.CODE_SUCCESS, body.getCode(),
                "成功信封 code 单源＝CODE_SUCCESS，本批只补行、不改信封");
    }

    // ── ① 403 权限拒绝 ────────────────────────────────────────────────────

    @Test
    @DisplayName("403：createCategory 权限拒绝落状态行 403 且与信封同号")
    void forbiddenLegCarriesStatusLine() {
        when(navService.createCategory(any())).thenThrow(new ForbiddenException("权限不足"));

        assertSameNumber(controller().createCategory(new NavCategorySaveDTO()), 403);
    }

    @Test
    @DisplayName("403：updateCategory 权限拒绝同样落 403（本类 13 枚 403 腿的第二个形态）")
    void forbiddenLegOnUpdateCarriesStatusLine() {
        when(navService.updateCategory(anyString(), any()))
                .thenThrow(new ForbiddenException("权限不足"));

        assertSameNumber(controller().updateCategory("c-1", new NavCategorySaveDTO()), 403);
    }

    // ── ② 404 与 403 互不折叠 ─────────────────────────────────────────────

    @Test
    @DisplayName("404：updateCategory 资源不存在的腿不得被折成 403 或 200")
    void notFoundLegStaysDistinctFromForbidden() {
        when(navService.updateCategory(anyString(), any()))
                .thenThrow(new NotFoundException("目录不存在"));

        assertSameNumber(controller().updateCategory("missing", new NavCategorySaveDTO()), 404);
    }

    // ── ③ 400 入参校验 ────────────────────────────────────────────────────

    @Test
    @DisplayName("400：createCategory 入参不合法落状态行 400")
    void validationLegCarriesStatusLine() {
        when(navService.createCategory(any())).thenThrow(new ValidationException("name 不能为空"));

        assertSameNumber(controller().createCategory(new NavCategorySaveDTO()), 400);
    }

    // ── ④ 捕获兜底腿：行 500／信封 -1（D-B4-65 在册缺口的显式钉）──────────

    @Test
    @DisplayName("500：listCategories 兜底腿落真 500，信封仍按 v1 的 -1（在册缺口照登）")
    void catchAllLegCarriesServerStatusLine() {
        when(navService.listCategories(any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        assertStatusAndEnvelopeCode(controller().listCategories(null, null, null), 500, -1);
    }

    // ── ⑤ 成功腿仍 200 ────────────────────────────────────────────────────

    @Test
    @DisplayName("200：成功腿不得被一并抬走（补状态行只补拒绝态）")
    void successLegStaysTwoHundred() {
        when(navService.listCategories(any(), any(), any())).thenReturn(List.of(new NavCategoryVO()));

        ResponseEntity<ApiResponse<List<NavCategoryVO>>> resp =
                controller().listCategories("default", null, null);
        assertSuccess(resp);
        assertEquals(1, resp.getBody().getData().size(), "成功载荷不得因补状态行而丢失");
    }

    // ── ⑥ Void 出口四态全链 ───────────────────────────────────────────────

    @Test
    @DisplayName("Void 出口：deleteCategory 的 200/400/404/403/500 五态互不折叠")
    void voidExitCarriesAllFiveStatesWithoutFolding() {
        NavTaxonController c = controller();

        assertSuccess(c.deleteCategory("ok"));

        doThrow(new ValidationException("有子目录")).when(navService).deleteCategory("bad");
        assertSameNumber(c.deleteCategory("bad"), 400);

        doThrow(new NotFoundException("没有该目录")).when(navService).deleteCategory("gone");
        assertSameNumber(c.deleteCategory("gone"), 404);

        doThrow(new ForbiddenException("权限不足")).when(navService).deleteCategory("deny");
        assertSameNumber(c.deleteCategory("deny"), 403);

        doThrow(new IllegalStateException("boom")).when(navService).deleteCategory("crash");
        assertStatusAndEnvelopeCode(c.deleteCategory("crash"), 500, -1);
    }

    // ── ⑦ recommend 兜底语义不变 ──────────────────────────────────────────

    @Test
    @DisplayName("recommend：LLM 异常兜底仍是 200＋空候选（既有合规兜底不因本批改动）")
    void recommendFallbackStaysSuccess() {
        when(navService.recommend(anyString())).thenThrow(new IllegalStateException("llm down"));

        ResponseEntity<ApiResponse<NavRecommendVO>> resp = controller().recommend("a-1");
        assertSuccess(resp);
        assertTrue(resp.getBody().getData().getTags().isEmpty(), "兜底候选必须是空集而非异常");
    }

    @Test
    @DisplayName("recommend：权限拒绝腿落 403（兜底 catch-all 不得把 403 一起吃成 200）")
    void recommendForbiddenLegCarriesStatusLine() {
        when(navService.recommend(anyString())).thenThrow(new ForbiddenException("权限不足"));

        assertSameNumber(controller().recommend("a-1"), 403);
    }

    // ── ⑧ 签名面：有失败出口的改型、无失败出口的不改型 ────────────────────

    @Test
    @DisplayName("签名：改写方法返回 ResponseEntity；modulesList 无失败出口保持原签名")
    void signaturesArePinned() throws Exception {
        Method create = NavTaxonController.class
                .getMethod("createCategory", NavCategorySaveDTO.class);
        assertEquals(ResponseEntity.class, create.getReturnType(),
                "createCategory 返回类型回退成 ApiResponse＝状态行面在本类重新失明");

        Method remove = NavTaxonController.class
                .getMethod("removeArticleCategories", String.class, List.class);
        assertEquals(ResponseEntity.class, remove.getReturnType(),
                "removeArticleCategories 的 403 腿若退回裸回＝D-B4-59 在本文件复现");

        Method modules = NavTaxonController.class.getMethod("modulesList");
        assertEquals(ApiResponse.class, modules.getReturnType(),
                "modulesList 无失败出口，本批有意不动其签名；改型即越界");
    }

    @Test
    @DisplayName("Map 载荷出口：setArticleTags 的 409 语义面未被本批引入（仅补行，字段不动）")
    void mapPayloadShapeIsUntouched() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller().setArticleTags("a-1", List.of("t1"));
        assertSuccess(resp);
        assertEquals("replace", resp.getBody().getData().get("mode"),
                "信封 data 字段结构必须与改前一致（API 面只增不改）");
    }
}
