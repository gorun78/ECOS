package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.workspace.QueryHistoryService;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import com.chinacreator.gzcm.workspace.repository.QueryHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询历史删除面的状态行保真门禁（D97，2026-11-14 验证批）。
 *
 * <p><b>缺陷形态（活体实测）</b>：{@code DELETE /api/query/history/<不存在 id>} 返回
 * {@code HTTP 200 + body{"code":404,"message":"记录不存在: …"}}。前端若按 HTTP 状态分流，
 * 会把"删了个不存在的东西"当成功处理——错误被静默吞掉。</p>
 *
 * <p><b>修法</b>：notFound 分支由 {@code return ApiResponse.error(404, …)} 改抛
 * {@link NotFoundException}，由早已存在的 {@link WorkspaceExceptionHandler#handleNotFound}
 * 写定 404（信封形状不变，只补状态行）——与 D93 同属「状态行保真族」端点腿标准修法。</p>
 */
@DisplayName("D97 — 查询历史删除不存在记录必须 404 状态行（不得 code:404 配 HTTP 200）")
class QueryHistoryDeleteNotFoundStatusTest {

    /** repository 缺席 ⇒ {@link QueryHistoryService#delete} 恒 false ⇒ 走 notFound 分支。 */
    @SuppressWarnings("unchecked")
    private static QueryHistoryController controllerWithoutRepository() {
        ObjectProvider<QueryHistoryRepository> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return new QueryHistoryController(new QueryHistoryService(provider));
    }

    @Test
    @DisplayName("删除不存在的 id ⇒ 抛 NotFoundException（由承载面写 404，而非裸回 200 信封）")
    void deleteMissingThrowsNotFound() {
        QueryHistoryController c = controllerWithoutRepository();
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> c.delete("does-not-exist"),
                "不存在记录必须抛异常；裸 return ApiResponse.error(404,…) 会配出 HTTP 200");
        assertTrue(ex.getMessage().contains("does-not-exist"), "异常须点名 id，便于排障");
    }

    @Test
    @DisplayName("承载面：NotFoundException ⇒ HTTP 404 且信封 code=404（形状不变）")
    void handlerWrites404StatusLine() {
        WorkspaceExceptionHandler handler = new WorkspaceExceptionHandler();
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                handler.handleNotFound(new NotFoundException("记录不存在: does-not-exist"));

        assertEquals(404, resp.getStatusCode().value(), "承载面必须写 HTTP 404 状态行");
        assertNotNull(resp.getBody(), "必须仍返回信封");
        assertEquals(ApiResponse.CODE_NOT_FOUND, resp.getBody().getCode(),
                "信封 code 仍是 404，形状与修复前一致");
        assertTrue(String.valueOf(resp.getBody().getMessage()).contains("does-not-exist"),
                "信封 message 原样透出");
    }

    @Test
    @DisplayName("成功腿不受影响：存在记录删除仍返回 success 信封（不误抛）")
    void deleteExistingStillSucceeds() {
        QueryHistoryRepository repo = org.mockito.Mockito.mock(QueryHistoryRepository.class);
        org.mockito.Mockito.when(repo.deleteById("h-1")).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<QueryHistoryRepository> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(repo);

        QueryHistoryController c = new QueryHistoryController(new QueryHistoryService(provider));
        ApiResponse<QueryHistoryDeleteVO> resp = c.delete("h-1");

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), "删除成功必须仍是 success 信封");
    }

    @Test
    @DisplayName("探针自检：旧形态（code:404 + HTTP 200）确实构成误导")
    void probeRecognizesLyingStatusLine() {
        ApiResponse<?> legacy = ApiResponse.error(404, "记录不存在: x");
        assertEquals(ApiResponse.CODE_NOT_FOUND, legacy.getCode(), "旧信封 code 是 404");
        assertFalse(legacy.getCode() == ApiResponse.CODE_SUCCESS,
                "信封 code 非 0，若 Controller 直接 return 它 ⇒ Spring 给 HTTP 200，两者冲突");
    }
}
