package com.chinacreator.gzcm.engine.kb.route;

import com.chinacreator.gzcm.engine.kb.support.KbErrorAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-03 验收 — kb_nav_* 表缺失/DML 失败应映射为 5xx（而非 404）并带 ECOS-KB-* 错误码。
 *
 * <p>接收录径：kb 局部 {@link KbErrorAdvice}（W94 三级掩蔽链第 1 级）必须先于网关
 * {@code GlobalExceptionHandler} 的 Exception→404 兜底命中。纯单元样式、无 Spring 上下文，
 * 通过反射断言 advice 存在性 + DataAccessException 处理器存在 + 其响应基线是 500（非 404），
 * 并断言网关 404 兜底未被删除（只是被 kb advice 抢先）。</p>
 */
class KbNavMissingTableReturnsServerError {

    @Test
    @DisplayName("kb 局部 advice 先行将表缺失(DAE)映射为 5xx + ECOS-KB-050，而非网兜 404")
    void absentTableYields500Not404() throws Exception {
        // 1) KbErrorAdvice 存在于 kb.support 包且是 @RestControllerAdvice
        assertNotNull(KbErrorAdvice.class.getPackage(), "KbErrorAdvice 必须有包");
        assertEquals("com.chinacreator.gzcm.engine.kb.support", KbErrorAdvice.class.getPackage().getName(),
                "KbErrorAdvice 应位于 kb 局部包 com.chinacreator.gzcm.engine.kb.support");
        assertTrue(KbErrorAdvice.class.isAnnotationPresent(RestControllerAdvice.class),
                "KbErrorAdvice 必须带 @RestControllerAdvice，才能先于网关兜底命中");

        // 2) 存在针对 DataAccessException 的 @ExceptionHandler
        Method onDa = null;
        for (Method m : KbErrorAdvice.class.getDeclaredMethods()) {
            ExceptionHandler eh = m.getAnnotation(ExceptionHandler.class);
            if (eh == null) { continue; }
            for (Class<?> c : eh.value()) {
                if (c == DataAccessException.class) { onDa = m; break; }
            }
            if (onDa != null) { break; }
        }
        assertNotNull(onDa, "KbErrorAdvice 必须有 @ExceptionHandler(DataAccessException.class)");

        // 3) 实际调用处理器：表缺失类 DAE → 500（>=500 且 <600，绝不是 404）+ errorCode 含 ECOS-KB-
        //    DataAccessException 是 abstract，用具体子类 BadSqlGrammarException 构造"表不存在"场景。
        DataAccessException simulated = new org.springframework.jdbc.BadSqlGrammarException(
                "SELECT * FROM kb_nav_node", "SELECT 1",
                new java.sql.SQLSyntaxErrorException("relation \"kb_nav_node\" does not exist"));
        ResponseEntity<?> resp = invoke(onDa, simulated);
        assertNotNull(resp, "onDa 处理器应返回 ResponseEntity");
        int status = resp.getStatusCode().value();
        assertTrue(status >= 500 && status < 600,
                "表缺失应映射为 5xx（500），实际 HTTP status=" + status + "（不得是 404 伪路由失败）");
        assertEquals(500, status, "kb_nav_* 表缺失基线为 500");

        Object body = resp.getBody();
        assertNotNull(body, "onDa 响应体不应为空");
        // ApiResponse.toString() 只输出 code+message，errorCode 需通过 getter 断言
        com.chinacreator.gzcm.common.base.ApiResponse<?> apiResp =
                (com.chinacreator.gzcm.common.base.ApiResponse<?>) body;
        String code = apiResp.getErrorCode();
        assertNotNull(code, "onDa 响应体必须携带 errorCode（空则未走 KbErrorAdvice）");
        assertTrue(code.contains("ECOS-KB"),
                "errorCode 应形如 ECOS-KB-*，实际=" + code);
        String msg = apiResp.getMessage();
        assertNotNull(msg, "onDa 响应须有 message");
        assertTrue(msg.contains("kb_nav"), "onDa 消息须提示 kb_nav_* 表缺失，实际=" + msg);

        // 4) 网关 404 兜底未被删除——kb 局部 advice 只是"抢跑"它，而非覆盖兜底
        Path gatewayGHE = locateRepoRoot()
                .resolve("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/GlobalExceptionHandler.java");
        assertTrue(Files.isRegularFile(gatewayGHE),
                "网关 GlobalExceptionHandler 必须仍保留（kb advice 仅先于它命中，不允许删除兜底）：" + gatewayGHE);
    }

    // ── 辅助 ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> ResponseEntity<T> invoke(Method m, DataAccessException ex) {
        m.setAccessible(true);
        try {
            Object advice = m.getDeclaringClass().getDeclaredConstructor().newInstance();
            return (ResponseEntity<T>) m.invoke(advice, ex);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("调用 onDa 处理器失败：" + e.getMessage(), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("构造 KbErrorAdvice 实例失败：" + e.getMessage(), e);
        }
    }

    /** 从运行目录向上遍历，定位同时含 ecos_frontend 与 ecos_backend 的仓库根。 */
    private static Path locateRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("ecos_frontend"))
                    && Files.isDirectory(dir.resolve("ecos_backend"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "无法定位仓库根：自 user.dir 向上 12 级未找到同时含 ecos_frontend 与 ecos_backend 的目录");
    }
}
