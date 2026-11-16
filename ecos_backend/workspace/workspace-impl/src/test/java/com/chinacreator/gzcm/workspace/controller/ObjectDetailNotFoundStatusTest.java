package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对象详情 404 的状态行保真门禁（D93，2026-11-14 验证批）。
 *
 * <p><b>缺陷形态（活体实测）</b>：{@code GET /api/v1/ecos/objects/Customer/<id>} 对一个
 * **确实不存在**（或落在 RLS 拒绝面）的对象返回 {@code HTTP 200 + body{"code":404}}；
 * 而同一个 id 上 {@code PUT} 更新成功、库里行也存在 ⇒ 调用方无法从传输层分辨
 * 「不存在」与「成功」。</p>
 *
 * <p><b>修法</b>：`getDetail` 的两条 notFound 分支改为抛 {@link NotFoundException}，
 * 由 {@link WorkspaceExceptionHandler#handleNotFound} 写定 404 状态行（信封形状不变）。</p>
 *
 * <p><b>为什么不改 RLS</b>：{@code RLS_NO_POLICY_MATCHED} 导致的空行按「不存在」处置是既有
 * fail-closed 基线（无策略即拒绝，且不泄露「行存在但被策略挡住」）。本批只让状态行如实反映 404。</p>
 */
@DisplayName("D93 — 对象详情不存在必须 404 状态行（不得 code:404 配 HTTP 200）")
class ObjectDetailNotFoundStatusTest {

    /** 六参构造器全传 null：本门禁只走「实体不存在」分支，不触达任何协作对象。 */
    private static ObjectController newController() {
        return new ObjectController(null, null, null, null, null, null);
    }

    @Test
    @DisplayName("未知 entityCode ⇒ 抛 NotFoundException（由承载面写 404，而非裸回 200 信封）")
    void unknownEntityThrowsNotFound() {
        ObjectController c = newController();
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> c.getDetail("NoSuchEntity", "x"),
                "未知实体必须抛异常；裸 return ApiResponse.notFound 会配出 HTTP 200");
        assertTrue(ex.getMessage().contains("NoSuchEntity"), "异常须点名实体，便于排障");
    }

    @Test
    @DisplayName("承载面：NotFoundException ⇒ HTTP 404 且信封 code=404（形状不变）")
    void handlerWrites404StatusLine() {
        WorkspaceExceptionHandler handler = new WorkspaceExceptionHandler();
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                handler.handleNotFound(new NotFoundException("Customer abc 不存在"));

        assertEquals(404, resp.getStatusCode().value(),
                "承载面必须写 HTTP 404 状态行");
        assertTrue(resp.getBody() != null, "必须仍返回信封");
        assertTrue(String.valueOf(resp.getBody().getMessage()).contains("Customer abc"),
                "信封 message 原样透出，便于调用方定位");
    }

    @Test
    @DisplayName("源码面：getDetail 不得再出现 return ApiResponse.notFound（防止回退成假 200）")
    void detailDoesNotBareReturnNotFoundEnvelope() throws Exception {
        Method m = ObjectController.class.getMethod("getDetail", String.class, String.class);
        assertTrue(m.getReturnType().equals(ApiResponse.class),
                "成功腿签名不动（状态行由承载面承担，避免同方法内非裁决腿一并改型）");

        // 反射拿不到源码文本，改用行为面反证：异常路径必须抛，不是返回信封
        ObjectController c = newController();
        assertThrows(NotFoundException.class, () -> c.getDetail("NoSuchEntity", "x"));
    }

    @Test
    @DisplayName("探针自检：旧形态（code:404 + HTTP 200）确实构成误导")
    void probeRecognizesLyingStatusLine() {
        ApiResponse<Map<String, Object>> legacy = ApiResponse.notFound("Customer abc 不存在");
        assertEquals(ApiResponse.CODE_NOT_FOUND, legacy.getCode(), "旧信封 code 是 404");
        // 旧实现在 Controller 里直接 return 该信封 ⇒ Spring 给 HTTP 200：
        // 这就是「同一 id PUT 成功、GET 说不存在」的根因。
        assertFalse(legacy.getCode() == ApiResponse.CODE_SUCCESS,
                "信封 code 非 0，若状态行是 200 则两者冲突——本门禁要消灭的正是这种冲突");
    }
}
