package com.chinacreator.gzcm.engine.kb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.config.KbLocalExceptionHandler;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode;

/**
 * 分册04 F04-03 掩蔽链护栏（验收点名 {@code KbNavMissingTableReturnsServerError#absentTableYields500Not404}）。
 *
 * <p><b>问题</b>（K-46/K-19）：网关 {@code GlobalExceptionHandler} 把未分类 {@code Exception}
 * 统一映射为 <b>404</b>；kb 侧 {@code kb_nav_*} 三表在 live 缺失却挂 19 handler，结构必败路径
 * 全部被 404 化 → 前端 catch→空态 双掩蔽 → 用户看到"暂无数据"而非"接口坏了"。</p>
 *
 * <p><b>离线直测（不 Spring 容器）</b>：直接调用 {@link KbLocalExceptionHandler} 的
 * {@code @ExceptionHandler} 方法（controller advice 可脱离容器求值），断言三条语义。
 * {@code ApiResponse.errorCode} 无 public getter（只序列化到响应体），所以串面断言经
 * {@link ApiResponse#toJson()} 做（不新增 API，只多读序列化面）：</p>
 * <ol>
 *   <li><b>absentTableYields500Not404</b>：表缺失 = {@code DataAccessResourceFailureException}
 *       子型 → 500 + {@code ECOS-KB-050}（文档 B.1 wiki 行口径"表不存在时显式 ECOS-KB-050"），
 *       <b>绝不落入 404</b>；</li>
 *   <li><b>otherDataAccessExceptionStill500</b>：非资源失败子型（超时/重复键/完整约束）也收口
 *       500（数据访问故障不归类为"无数据"）；</li>
 *   <li><b>scopeAndOrderGuards</b>：advice 仅锁 kb controller 包（basePackages 锚点）+
 *       {@code @Order(0)} 抢先于默认 ord 的网关 advice —— 防将来"再被外层 404 化"回归。</li>
 * </ol>
 */
class KbNavMissingTableReturnsServerError {

    private final KbLocalExceptionHandler advice = new KbLocalExceptionHandler();

    @Test
    @DisplayName("absentTableYields500Not404: 表缺失(DataAccessResourceFailure) → 500 + ECOS-KB-050")
    void absentTableYields500Not404() {
        DataAccessResourceFailureException ex = new DataAccessResourceFailureException(
            "relation \"ecos_knowledge.kb_nav_category\" does not exist");
        ApiResponse<Void> resp = advice.handleDataAccess(ex);

        assertEquals(500, resp.getCode(), "表缺失必须 500，禁 404 化");
        String json = resp.toJson();
        assertTrue(json.contains("\"errorCode\":\"" + KbErrorCode.KB_050 + "\""),
            "响应体 errorCode 必须为 " + KbErrorCode.KB_050 + "，实测 JSON = " + json);
        assertTrue(json.contains("kb_nav_category"), "消息须透传表名供定位");
        assertTrue(!json.contains("\"code\":404"), "响应体不得 404 化");
    }

    @Test
    @DisplayName("其他 DataAccessException 子型（超时/重复键/完整约束）同样收口 500 不 404")
    void otherDataAccessExceptionStill500() {
        for (RuntimeOrDataAccess ex : new RuntimeOrDataAccess[]{
            new RuntimeOrDataAccess("query timeout",
                new QueryTimeoutException("statement timed out")),
            new RuntimeOrDataAccess("duplicate key",
                new DuplicateKeyException("duplicate key value")),
            new RuntimeOrDataAccess("integrity",
                new DataIntegrityViolationException("null value in not null column")),
        }) {
            ApiResponse<Void> resp = advice.handleDataAccess(ex.underlying());
            assertEquals(500, resp.getCode(), ex.label + " 子型不得 404 化");
            String json = resp.toJson();
            assertTrue(json.contains("\"errorCode\":\"ECOS-KB-"),
                ex.label + " 必须带 ECOS-KB-* 串面, 实测 " + json);
        }
    }

    @Test
    @DisplayName("scope/order 守卫: advice 只锁 kb.controller 且 @Order(0) 抢先")
    void scopeAndOrderGuards() throws IOException {
        Class<?> cls = KbLocalExceptionHandler.class;
        org.springframework.core.annotation.Order order =
            cls.getAnnotation(org.springframework.core.annotation.Order.class);
        assertNotNull(order, "缺 @Order：advice 优先级将退化到默认，可能再被外层 advice 404 化");
        assertEquals(0, order.value(), "@Order 必须 0（抢默认 ord 的 GlobalExceptionHandler）");

        String src = new String(Files.readAllBytes(adviceSource()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("@RestControllerAdvice\\s*\\(\\s*basePackages\\s*=\\s*\"([^\"]+)\"")
            .matcher(src);
        assertTrue(m.find(), "basePackages 锚点缺失：advice 作用域失控");
        assertEquals("com.chinacreator.gzcm.engine.kb.controller", m.group(1),
            "作用域必须精确锁 kb.controller 包（不扩/不缩）");

        for (Class<?> handled : List.of(
                KbErrorCode.KbErrorCodeException.class,
                org.springframework.dao.DataAccessException.class,
                IllegalStateException.class,
                IllegalArgumentException.class)) {
            boolean has = false;
            for (Method meth : cls.getDeclaredMethods()) {
                var eh = meth.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class);
                if (eh != null && java.util.Arrays.stream(eh.value()).anyMatch(v -> v == handled)) {
                    has = true;
                }
            }
            assertTrue(has, "缺 @ExceptionHandler(" + handled.getSimpleName() + "): 掩蔽链语义折扣");
        }
    }

    private record RuntimeOrDataAccess(String label, org.springframework.dao.DataAccessException underlying) {}

    private static Path adviceSource() throws IOException {
        Path cur = Path.of("").toAbsolutePath();
        Path root = null;
        for (int i = 0; i < 16 && cur != null; i++, cur = cur.getParent()) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                root = cur;
                break;
            }
        }
        if (root == null) fail("repoRoot 不可达（docs + ecos_backend 双目录）");
        Path p = root.resolve("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java"
            + "/com/chinacreator/gzcm/engine/kb/config/KbLocalExceptionHandler.java");
        if (!Files.isRegularFile(p)) fail("advice 源码不可达: " + p);
        return p;
    }
}
