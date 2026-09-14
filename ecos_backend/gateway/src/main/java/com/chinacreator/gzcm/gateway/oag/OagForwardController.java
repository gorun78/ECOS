package com.chinacreator.gzcm.gateway.oag;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * OAG 转发控制器 — 网关侧 /api/v1/oag/* 路由代理。
 *
 * <p>在 docker-compose 微服务模式 + dev(monolith) 模式下，前端/BFF 统一经 gateway :8080
 * 访问 {@code /api/v1/oag/*} 时，本控制器将请求转发到 aiming service :18084 同路径端点，
 * 解决 gateway 未直接注册 OagController（该 Controller 物理位于 ai-engine-impl 的
 * {@code com.chinacreator.gzcm.engine.ai.oag} 包，gateway 的 excludeFilters 排除了
 * engine 整包）导致的 404。采用"控制器即路由"的简洁转发风格，与现有 gateway 控制器一致
 * （非 Zuul / ForwardedRouteFilter）。</p>
 *
 * <p>端点：</p>
 * <pre>
 *   POST /api/v1/oag/chat          → POST :18084/api/v1/oag/chat          (非流式 JSON 透传)
 *   POST /api/v1/oag/chat/v2       → POST :18084/api/v1/oag/chat/v2       (非流式 JSON 透传)
 *   POST /api/v1/oag/chat/stream   → POST :18084/api/v1/oag/chat/stream   (SSE 流式透传)
 *   POST /api/v1/oag/health        → POST :18084/api/v1/oag/health        (简易健康转发)
 * </pre>
 *
 * <p><b>HTTP 客户端实现说明</b>：所有转发统一使用 Java 17 {@link java.net.http.HttpClient}
 * （JDK 自带，零额外依赖）。非流式端点 {@code send} 拿完整响应后包装为 Spring
 * {@code ResponseEntity<String>}（透传状态码 + Content-Type + body）；SSE 端点
 * {@code send(..., BodyHandler.ofInputStream)} 拿目标流 InputStream，由独立线程池逐行
 * readLine 解析 SSE 帧（{@code event:<name>} + {@code data:<json>} 成对），逐帧推回前端
 * SseEmitter，避免主线程阻塞。</p>
 *
 * <p>aiming 不可达时捕获 {@link IOException}，抛 {@link DataBridgeException}
 * 由 GlobalExceptionHandler 统一返回 5xx。</p>
 *
 * @author ECOS 网关 OAG 转发路由（PMO 任务）
 */
@RestController
@RequestMapping("/api/v1/oag")
public class OagForwardController {

    private static final Logger log = LoggerFactory.getLogger(OagForwardController.class);

    /** SSE 客户端读流缓冲大小 */
    private static final int DISPLAY_BUFFER_SIZE = 8192;

    /**
     * SSE 转发线程池：readLine 阻塞目标流，需独立线程池避免占用 Tomcat 工作线程。
     * core=max=4 常驻，队列 1000 缓冲突发，饱和时 CallerRuns 退化为同步（保命）。
     */
    private final ExecutorService sseExecutor = new ThreadPoolExecutor(
            4, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "oag-sse-forwarder");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    /** 转发用 JDK HttpClient（连接 5s / 不跟随重定向 / 全局复用） */
    private final java.net.http.HttpClient httpClient;

    /** aiming service 基础地址（端口可配置，默认 18084 对齐 docker-compose 与 ADR-7 端口隔离） */
    private final String baseUrl;

    /**
     * 构造器。
     *
     * @param targetPort aiming service 端口（默认 18084，对齐 docker-compose 与 services/aiming）
     */
    public OagForwardController(@Value("${ecos.oag.target.port:18084}") int targetPort) {
        this.baseUrl = "http://localhost:" + targetPort;
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
        log.info("[OAG-FWD] OAG 转发控制器已初始化, 目标 {}", baseUrl);
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. POST /api/v1/oag/chat — 非流式对话（向后兼容 Map 入参）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 转发非流式 OAG 管道对话（向后兼容 Map 入参版本）。
     *
     * @param rawBody 原始 JSON 请求体（避免 Object→Map 转换丢失字段）
     * @return 目标端点返回的完整 JSON（透传）
     */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@RequestBody String rawBody) {
        return forwardJson("/chat", rawBody);
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. POST /api/v1/oag/chat/v2 — 非流式对话（强类型 DTO 入参）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 转发非流式 OAG 管道对话（强类型 DTO 入参版本）。
     *
     * @param rawBody 原始 JSON 请求体
     * @return 目标端点返回的完整 JSON（透传）
     */
    @PostMapping(value = "/chat/v2", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chatV2(@RequestBody String rawBody) {
        return forwardJson("/chat/v2", rawBody);
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. POST /api/v1/oag/chat/stream — SSE 流式透传
    // ═══════════════════════════════════════════════════════════════

    /**
     * SSE 流式 OAG 对话透传。
     *
     * @param rawBody 原始 JSON 请求体
     * @return 前端 SseEmitter 流
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody String rawBody) {
        SseEmitter emitter = new SseEmitter(300_000L);
        URI targetUri = URI.create(baseUrl + "/api/v1/oag/chat/stream");
        HttpRequest request = HttpRequest.newBuilder(targetUri)
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8))
                .build();
        long requestStart = System.currentTimeMillis();

        try {
            HttpResponse<InputStream> resp = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                String snippet = readSnippet(resp.body());
                resp.body().close();
                log.warn("[OAG-FWD] 目标 SSE 端点返回 {}: {}", resp.statusCode(), snippet);
                emitter.completeWithError(new DataBridgeException(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        ApiResponse.CODE_INTERNAL_ERROR,
                        "OAG stream 转发失败: HTTP " + resp.statusCode() + " " + snippet));
                return emitter;
            }
            forwardSseFrame(resp.body(), emitter);
            emitter.onCompletion(() -> log.debug(
                    "[OAG-FWD] SSE 流完成, 用时 {} ms", System.currentTimeMillis() - requestStart));
            emitter.onTimeout(() -> {
                log.warn("[OAG-FWD] SSE 超时(300s), 强制 complete");
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // emitter 可能已关闭
                }
            });
            emitter.onError(ex -> log.warn("[OAG-FWD] SSE emitter 错误", ex));
            return emitter;
        } catch (Exception e) {
            log.error("[OAG-FWD] SSE 转发异常 → {}", targetUri, e);
            try {
                emitter.completeWithError(new DataBridgeException(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        ApiResponse.CODE_INTERNAL_ERROR,
                        "Aiming service (OAG) unavailable at " + baseUrl + "/chat/stream", e));
            } catch (Exception ignored) {
                // emitter 可能已发出
            }
            return emitter;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. POST /api/v1/oag/health — 简易健康检查转发
    // ═══════════════════════════════════════════════════════════════

    /**
     * 简易健康检查转发：目标返回 2xx 即健康，否则返回 503 语义。
     *
     * @return ApiResponse 包装的健康状态描述
     */
    @PostMapping("/health")
    public ApiResponse<String> health() {
        String target = baseUrl + "/api/v1/oag/health";
        HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            log.debug("[OAG-FWD] /health 转发, 目标 {}, HTTP {}", target, status);
            if (status >= 200 && status < 300) {
                return ApiResponse.success("OAG endpoint reachable: " + target + " (HTTP " + status + ")");
            }
            return ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "OAG health check failed: HTTP " + status);
        } catch (Exception e) {
            log.error("[OAG-FWD] /health 转发异常 → {}", target, e);
            throw new DataBridgeException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    ApiResponse.CODE_INTERNAL_ERROR,
                    "Aiming service (OAG) unavailable at " + target, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有工具方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 通用非流式 JSON 转发：POST rawBody 到目标 /api/v1/oag{path}，透传响应体与状态码。
     *
     * @param path    目标相对路径（含前导 /，如 /chat、/chat/v2）
     * @param rawBody 原始 JSON 请求体
     * @return 目标响应（状态码 + Content-Type + body 透传）
     * @throws DataBridgeException 网络/超时异常
     */
    private ResponseEntity<String> forwardJson(String path, String rawBody) {
        String target = baseUrl + "/api/v1/oag" + path;
        HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8))
                .build();
        log.debug("[OAG-FWD] 转发 {} → {}", path, target);
        try {
            HttpResponse<String> resp = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            MediaType contentType = resp.headers().firstValue("Content-Type")
                    .map(MediaType::parseMediaType)
                    .orElse(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(status)
                    .contentType(contentType)
                    .body(resp.body());
        } catch (Exception e) {
            log.error("[OAG-FWD] 转发异常 {} → {}", path, target, e);
            throw new DataBridgeException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    ApiResponse.CODE_INTERNAL_ERROR,
                    "Aiming service (OAG) unavailable at " + target, e);
        }
    }

    /**
     * SSE 流透传核心：从目标 body(InputStream) 在独立线程内逐行 readLine，
     * 按 SSE 帧协议（两行/帧）识别 {@code event:<name>} 与 {@code data:<payload>}，
     * 逐帧推回前端 emitter，流结束则 complete。
     *
     * <p>SSE 帧格式：{@code event:<name>\ndata:<json>\n\n}，空行结算帧；
     * 连续多个 data: 行拼接为单条 payload（HTML5 §9.2.4）。</p>
     *
     * @param body    目标响应流（text/event-stream）
     * @param emitter 前端 SseEmitter
     */
    private void forwardSseFrame(InputStream body, SseEmitter emitter) {
        sseExecutor.submit(() -> {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(body, StandardCharsets.UTF_8), DISPLAY_BUFFER_SIZE);
            String eventName = null;
            StringBuilder dataBuf = new StringBuilder();
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        // 空行 = 帧结算
                        if (dataBuf.length() > 0 || eventName != null) {
                            sendSseFrame(emitter, eventName,
                                    dataBuf.length() > 0 ? dataBuf.toString() : null);
                            dataBuf.setLength(0);
                            eventName = null;
                        }
                        continue;
                    }
                    if (line.charAt(0) == ':') {
                        // SSE 注释行，跳过
                        continue;
                    }
                    int colon = line.indexOf(':');
                    String field = colon >= 0 ? line.substring(0, colon) : line;
                    String value = colon >= 0
                            ? line.substring(colon + 1).replaceFirst("^\\s", "")
                            : "";
                    if ("event".equals(field)) {
                        eventName = value;
                    } else if ("data".equals(field)) {
                        if (dataBuf.length() > 0) {
                            dataBuf.append('\n');
                        }
                        dataBuf.append(value);
                    }
                    // id / retry 等控制帧不透传（gateway 不透传 SSE 控制字段）
                }
                // 流正常结束，结算残余未结算帧
                if (dataBuf.length() > 0 || eventName != null) {
                    sendSseFrame(emitter, eventName,
                            dataBuf.length() > 0 ? dataBuf.toString() : null);
                }
                emitter.complete();
            } catch (IOException e) {
                log.warn("[OAG-FWD] SSE 读流中断: {}", e.getMessage());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // emitter 可能已关闭
                }
            } catch (Exception e) {
                log.error("[OAG-FWD] SSE 转发异常", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // emitter 可能已关闭
                }
            } finally {
                closeQuietly(reader);
            }
        });
    }

    /**
     * 发送单条 SSE 帧到前端 emitter。data 为 JSON 字符串（原始文本直发，
     * 避免 SseEmitter 二次 Jackson 序列化导致转义），name 为空时默认 "message"。
     *
     * @param emitter   前端 SseEmitter
     * @param eventName 事件名（node/response/done/error/blocked）；为 null 时用 "message"
     * @param data      JSON payload；为 null 时仅发事件名
     * @throws IOException 发送失败
     */
    private void sendSseFrame(SseEmitter emitter, String eventName, String data)
            throws IOException {
        String name = StringUtils.hasText(eventName) ? eventName : "message";
        SseEmitter.SseEventBuilder event = SseEmitter.event().name(name);
        if (data != null) {
            event = event.data(data);
        }
        emitter.send(event);
    }

    /**
     * 读取响应体前 512 字符用于诊断，读完关闭流。
     *
     * @param body 响应体 InputStream
     * @return 前 512 字节文本（或全部）
     */
    private String readSnippet(InputStream body) {
        try {
            byte[] buf = new byte[512];
            int n = body.read(buf);
            body.close();
            return n > 0 ? new String(buf, 0, Math.min(n, buf.length), StandardCharsets.UTF_8)
                    : "(empty)";
        } catch (IOException e) {
            return "(read-failed: " + e.getMessage() + ")";
        }
    }

    /**
     * 安静关闭 AutoCloseable，忽略 CloseException。
     *
     * @param closeable 待关闭资源
     */
    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 忽略关闭异常（流已读完或已关闭）
            }
        }
    }
}
