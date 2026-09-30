package com.chinacreator.gzcm.gateway.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;

/**
 * F02-08 验收《DqGovernanceEndpointReachabilityTest》— DQ 治理端点可达性（禁 404）。
 *
 * <p>设计 §F02-08 规格 1/验收：{@code /api/v1/dq/{rules,work-orders,scores/system,health/selfcheck}}
 * 四路端点必须 <b>200 或明确业务错误，禁 404</b>。404 的唯一合法来源 = 真实路由不存在
 * （{@code NoHandlerFound}/{@code NoResourceFound}）。本测试从两个维度证明「路由已注册、
 * 非 404」：</p>
 * <ol>
 *   <li><b>路由注册</b>：源码扫描 4 个 DQ 控制器的 {@code @RequestMapping}（类级 + 方法级）
 *       全量端点，断言 4 路目标端点分别被某控制器（类级前缀+方法路径）覆盖 = 真路由存在，
 *       而非落入 404 兜底。</li>
 *   <li><b>错误掩蔽修复</b>：复用 {@link GlobalExceptionHandler}（同包，F02-08 规格 1 已修）
 *       断言 {@code DataAccessException → 500 + ECOS-DATA-031 + traceId}，即 DB 失败不再被
 *       掩蔽为 404（W46/D-6 反证）。</li>
 * </ol>
 */
@DisplayName("F02-08：DQ 治理端点可达（路由已注册 + DB 失败不掩蔽为 404）")
class DqGovernanceEndpointReachabilityTest {

    private static final String TRACE_ID = "tc-dq-0001";

    /** 4 路目标端点（设计验收清单：rules / work-orders / scores/system / health/selfcheck）。 */
    private static final Set<String> TARGET_ENDPOINTS = Set.of(
            "GET /api/v1/dq/rules",
            "GET /api/v1/dq/work-orders",
            "GET /api/v1/dq/scores/system",
            "POST /api/v1/dq/health/selfcheck"
    );

    /** 覆盖 4 路的 4 个 DQ 控制器源文件（类级 @RequestMapping 前缀各自不同）。 */
    private static final List<String> CONTROLLER_FILES = List.of(
            "DqGovernanceController.java",    // /api/v1/dq        (+ /rules)
            "DqWorkOrderController.java",     // /api/v1/dq/work-orders
            "DqScoreController.java",         // /api/v1/dq/scores  (+ /system)
            "DqSelfCheckController.java"      // /api/v1/dq/health  (+ /selfcheck)
    );

    private static final String CONTROLLER_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/data/quality/controller";

    private static final Pattern CLASS_REQUEST_MAPPING =
            Pattern.compile("@RequestMapping\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern METHOD_MAPPING =
            Pattern.compile("@(Get|Post|Put|Delete|Request)Mapping\\s*(?:\\(\\s*\"?([^)\",]*)\"?)?");

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MDC.put("traceId", TRACE_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove("traceId");
    }

    @Test
    @DisplayName("4 路 DQ 治理端点均为已注册路由（控制器前缀+方法路径覆盖，非 404 兜底）")
    void dqGovernanceEndpointsAreRegisteredRoutes() throws Exception {
        Set<String> routes = collectRegisteredRoutes();
        assertTrue(routes.size() >= 4,
                "DQ 控制器至少应注册 4 个治理端点，实测仅 " + routes.size() + " 个：" + routes);

        List<String> missing = new ArrayList<>();
        for (String target : TARGET_ENDPOINTS) {
            String normalized = normalize(target);
            final boolean hit = routes.stream().anyMatch(normalized::equals);
            if (!hit) {
                missing.add(target);
            }
        }
        assertTrue(missing.isEmpty(),
                "以下 DQ 治理端点未在任何控制器注册（将落入 404 兜底，F02-08 禁 404）：\n"
                        + String.join("\n", missing)
                        + "\n已注册路由：" + routes);
    }

    @Test
    @DisplayName("DB 失败（DataAccessException）→ 500 + ECOS-DATA-031 + traceId，绝非 404")
    void dataAccessFailureNotMaskedAs404() {
        DataAccessException in = new DataAccessException("simulated sql failure") {
            private static final long serialVersionUID = 1L;
        };
        ApiResponse<Void> resp = handler.handleSpringDataAccess(in);
        assertNotNull(resp, "handleSpringDataAccess 必须返回 ApiResponse");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), resp.getCode(),
                "F02-08 规格 1：DB 失败必须 500，不得掩蔽为 404");
        assertEquals(DataDomainErrorCode.ECOS_DATA_031, readErrorCodeViaReflection(resp),
                "数据层失败 errorCode 必须 = ECOS-DATA-031");
        assertEquals(TRACE_ID, resp.getTraceId(), "DB 失败响应体必须附 traceId");
        assertResponseStatus("handleSpringDataAccess", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    /** 扫描 4 个 DQ 控制器，组装「METHOD fullPath」集合（类级前缀 + 方法路径）。 */
    private static Set<String> collectRegisteredRoutes() throws Exception {
        Path root = findRepoRoot();
        Set<String> routes = new LinkedHashSet<>();
        for (String file : CONTROLLER_FILES) {
            Path f = root.resolve(CONTROLLER_REL).resolve(file);
            assertTrue(Files.isRegularFile(f), "缺 DQ 控制器源文件: " + f);
            String src = Files.readString(f, StandardCharsets.UTF_8);

            // 类级前缀
            String prefix = "";
            Matcher cm = CLASS_REQUEST_MAPPING.matcher(src);
            if (cm.find()) {
                prefix = cm.group(1);
            }
            // 方法级映射：@GetMapping("/xxx") 或 @GetMapping（空 → 类级路径本身）
            Matcher mm = METHOD_MAPPING.matcher(src);
            while (mm.find()) {
                String http = mm.group(1);
                String sub = mm.group(2) == null ? "" : mm.group(2).trim();
                if ("Request".equals(http) && sub.startsWith("/api/v1/dq")) {
                    // 类级 @RequestMapping 已单独抽取，跳过避免重复为 4 种方法
                    continue;
                }
                String method = "Request".equals(http) ? "GET" : http.toUpperCase(Locale.ROOT);
                String path = joinPath(prefix, sub);
                routes.add(method + " " + path);
            }
        }
        return routes;
    }

    /** 把「GET /api/v1/dq/rules」归一化（去多余空、尾斜杠）。 */
    private static String normalize(String route) {
        String[] parts = route.split("\\s+", 2);
        String method = parts[0].toUpperCase(Locale.ROOT);
        String path = parts.length > 1 ? parts[1] : "";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return method + " " + path;
    }

    /** 拼接类级前缀与方法子路径（均可能为空）。 */
    private static String joinPath(String prefix, String sub) {
        if (sub.isEmpty()) {
            return prefix.isEmpty() ? "/" : prefix;
        }
        if (sub.startsWith("/")) {
            return prefix + sub;
        }
        return (prefix.endsWith("/") ? prefix + sub : prefix + "/" + sub);
    }

    private static String readErrorCodeViaReflection(ApiResponse<?> resp) {
        try {
            java.lang.reflect.Field f = resp.getClass().getDeclaredField("errorCode");
            f.setAccessible(true);
            Object v = f.get(resp);
            return v == null ? null : v.toString();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("无法读取 ApiResponse.errorCode 字段", e);
        }
    }

    private void assertResponseStatus(String methodName, HttpStatus expected) {
        boolean found = false;
        for (java.lang.reflect.Method m : handler.getClass().getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                ResponseStatus ann = m.getAnnotation(ResponseStatus.class);
                assertNotNull(ann, methodName + " 必须声明 @ResponseStatus");
                assertEquals(expected, ann.value(), methodName + " 的 @ResponseStatus 必须 = " + expected);
                found = true;
                break;
            }
        }
        assertTrue(found, "未找到 handler 方法 " + methodName);
    }

    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))
                    || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
