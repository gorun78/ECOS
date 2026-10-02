package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-13「异常语义统一 / 200 吞异常治理」三条红线逐口可证 · 反锚点门禁（详细设计-07 验收行 498 / 矩阵行 1322）。
 *
 * <p>历史病灶 X-16（四处 catch 后仍 {@code return ApiResponse} 恒 HTTP 200）+ X-17
 * （{@code ScenarioOptionsController:206-209} 上游 503 被吞进 body）的<b>可离线证伪收口门禁</b>。
 * workspace-impl 测试类路径无 spring-test，故 (2)(3) 口用「反射双闸 + 直接驱动 advice」替代 MockMvc，
 * 与源码结构反锚点同族；<b>禁以 Mockito 全绿冒充 P-2 端到端已跑</b>，运行期真 503 由 P-2 载体承接。</p>
 *
 * <ol>
 *   <li>{@link #noEndpointReturnsHttp200WithCode503} —— 源码结构反锚点：workspace-impl 主源码
 *       非 advice 文件里<b>不得</b>出现 {@code return ApiResponse.error(5xx, …)}（注释剥离后扫）。
 *       5xx 正文的<b>唯一合法宿主</b>是 {@link WorkspaceExceptionHandler}（advice，body 与
 *       {@code @ResponseStatus} 双闸同步才真落下 HTTP 5xx）。任何业务代码自己 new 一个 5xx body
 *       直接 return（X-16/X-17 六处的复活）即红。</li>
 *   <li>{@link #engineUnavailableYieldsHttp503NotHttp200WithEmptyBody} —— 反射双闸：
 *       advice 上 cognitive / external 两不可用映射方法必须挂 {@code @ResponseStatus(SERVICE_UNAVAILABLE)}
 *       （缺了它 Spring 只发 200 + body.code=503，正是被治病灶）；直接驱动后 body.code==503 且正文非空
 *       （非 200 空 body，A3）。</li>
 *   <li>{@link #internalMessageNeverLeaksIntoResponseBody} —— 通用 Exception / DataAccess /
 *       认知引擎 cause 三种内含原文（SQL/表名/口令/堆栈）经 advice 后<b>只留固定文案</b>，原文零入体
 *       （X-41 A3 红线）。</li>
 * </ol>
 */
class ApiResponseTruthTest {

    private static final Pattern SWALLOW_5XX = Pattern.compile("ApiResponse\\.error\\(5[0-9][0-9]\\b");
    /** 5xx body 全文中允许出现的唯一合法宿主文件名。 */
    private static final String LEGIT_5XX_HOST = "WorkspaceExceptionHandler.java";

    private final WorkspaceExceptionHandler advice = new WorkspaceExceptionHandler();

    // ─────────────── (1) 源码结构反锚点：非 advice 主源码零 5xx-swallow ───────────────

    @Test
    void noEndpointReturnsHttp200WithCode503() throws IOException {
        Path mainRoot = locateWorkspaceMainRoot();
        assertTrue(Files.isDirectory(mainRoot), "workspace 主包不可达: " + mainRoot);
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainRoot)) {
            for (Path p : walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                if (LEGIT_5XX_HOST.equals(p.getFileName().toString())) {
                    continue; // advice 是 5xx body 唯一合法宿主
                }
                String stripped = stripComments(Files.readString(p, StandardCharsets.UTF_8));
                if (SWALLOW_5XX.matcher(stripped).find()) {
                    offenders.add(mainRoot.relativize(p).toString().replace('\\', '/'));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "非 advice 主源码不得 return 5xx body（X-16/X-17 200-swallow 复活）: " + offenders);
        // oracle 正控：advice 自身确实持 5xx 映射，防「指纹没扫到任何 5xx」的假绿
        Path adviceFile = mainRoot.resolve("exception/" + LEGIT_5XX_HOST);
        String adviceSrc = stripComments(Files.readString(adviceFile, StandardCharsets.UTF_8));
        assertTrue(SWALLOW_5XX.matcher(adviceSrc).find(),
                LEGIT_5XX_HOST + " 必须持 5xx body（否则本门禁在空指纹上假绿）");
        assertTrue(adviceSrc.contains("@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)"),
                "advice 5xx 须与 @ResponseStatus 双闸同步，防「body 503 无 HTTP 状态」回落 200");
    }

    // ──────────── (2) 反射双闸 + 直接驱动：cognitive/external 503 真落下，非 200 空 body ────────────

    @Test
    void engineUnavailableYieldsHttp503NotHttp200WithEmptyBody() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                requireResponseStatusOnHandler(CognitiveEngineUnavailableException.class).value(),
                "cognitive 不可用 handler 必须 @ResponseStatus(503)，缺了=回落 HTTP 200+body.code（病灶）");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                requireResponseStatusOnHandler(ExternalServiceUnavailableException.class).value(),
                "跨服务不可达 handler 必须 @ResponseStatus(503)（X-17 订正口径一致）");

        ApiResponse<Void> body = advice.handleCognitiveUnavailable(
                new CognitiveEngineUnavailableException("cognitive down"));
        assertNotNull(body, "503 响应体不可为空（A3：非 200 空 body）");
        assertEquals(503, body.getCode(), "body.code 与 @ResponseStatus 须双同步 503");
        assertFalse(body.getCode() == 200, "禁 200-swallow 伪装");
        assertNotNull(body.getMessage());
        assertFalse(body.getMessage().isBlank(), "503 正文不可全空白");

        ApiResponse<Void> ext = advice.handleExternalUnavailable(
                new ExternalServiceUnavailableException("dep", "timeout"));
        assertEquals(503, ext.getCode());
        assertFalse(ext.getCode() == 200);
        assertNotNull(ext.getMessage());
        assertFalse(ext.getMessage().isBlank());
    }

    private ResponseStatus requireResponseStatusOnHandler(Class<? extends Throwable> exType) {
        for (Method m : WorkspaceExceptionHandler.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == exType) {
                ResponseStatus rs = m.getAnnotation(ResponseStatus.class);
                assertNotNull(rs,
                        "handler(" + exType.getSimpleName() + ") 必须挂 @ResponseStatus，否则 Spring 默认 200");
                return rs;
            }
        }
        throw new IllegalStateException("advice 缺少 " + exType.getSimpleName() + " 的 @ExceptionHandler");
    }

    // ────────────────── (3) 内部原文零入体：固定文案替换异常/SQL/堆栈 ──────────────────

    @Test
    void internalMessageNeverLeaksIntoResponseBody() {
        // 通用 Exception → 500 固定文案
        ApiResponse<Void> any = advice.handleAny(
                new RuntimeException("StackFrameSecret-Pkg=com.internal.App secretLine"));
        assertEquals(500, any.getCode());
        assertFalse(textOf(any).contains("StackFrameSecret"), "通用异常内部原文不得入体");
        assertFalse(textOf(any).contains("com.internal.App"), "堆栈包名不得入体");

        // DataAccess → 500 固定文案（SQL/表名/口令零入体）—— secret 仅局部构造，不外泄共享状态
        ApiResponse<Void> da = advice.handleDataAccess(
                new InvalidDataAccessApiUsageException(
                        "SELECT secret_col FROM intros.internal_orders WHERE pwd='LEAK-PASSWORD-9'"));
        assertEquals(500, da.getCode());
        String daText = textOf(da);
        assertFalse(daText.contains("intros.internal_orders"), "内部表名不得入体");
        assertFalse(daText.contains("LEAK-PASSWORD-9"), "SQL 内口令/值不得入体");
        assertFalse(daText.contains("secret_col"), "列名不得入体");

        // 认知引擎 cause 原文 → 503 固定文案，cause message 零入体
        ApiResponse<Void> cog = advice.handleCognitiveUnavailable(new CognitiveEngineUnavailableException(
                "upstream", new RuntimeException("HeuristicStackSecret-Trace=deadbeef")));
        assertEquals(503, cog.getCode());
        assertFalse(textOf(cog).contains("HeuristicStackSecret"), "cognitive cause 堆栈原文不得入体");
        assertFalse(textOf(cog).contains("deadbeef"), "cause 内部堆栈标识不得入体");
    }

    private static String textOf(ApiResponse<Void> body) {
        StringBuilder sb = new StringBuilder();
        sb.append(body.getCode());
        if (body.getMessage() != null) sb.append(' ').append(body.getMessage());
        if (body.getData() != null) sb.append(' ').append(body.getData());
        return sb.toString();
    }

    // ─────────────────────────────────── 结构 / 工具 ───────────────────────────────────

    /** 定位 workspace-impl 主源码的 workspace 包根：优先编译产物反推（与 CWD 无关），CWD 相对路径兜底。 */
    private static Path locateWorkspaceMainRoot() {
        try {
            Path cf = Path.of(ApiResponseTruthTest.class
                    .getResource("ApiResponseTruthTest.class").toURI());
            // <moduleRoot>/target/test-classes/com/chinacreator/gzcm/workspace/exception/ApiRe...class
            Path moduleRoot = cf.getParent().getParent().getParent()
                    .getParent().getParent().getParent().getParent().getParent();
            Path main = moduleRoot.resolve("src/main/java/com/chinacreator/gzcm/workspace");
            if (Files.isDirectory(main)) {
                return main;
            }
        } catch (Exception ignore) {
            // fall through to CWD-relative
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path cand : List.of(
                cwd,
                cwd.resolve("workspace/workspace-impl"),
                cwd.resolve("workspace"))) {
            Path main = cand.toAbsolutePath().normalize()
                    .resolve("src/main/java/com/chinacreator/gzcm/workspace");
            if (Files.isDirectory(main)) {
                return main;
            }
        }
        throw new IllegalStateException("cannot locate workspace-impl main root (CWD=" + cwd + ")");
    }

    /** 剥离块注释与行注释，避免 Javadoc 里的文本造成误报。 */
    static String stripComments(String raw) {
        String noBlock = raw.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\n]*", " ");
    }
}
