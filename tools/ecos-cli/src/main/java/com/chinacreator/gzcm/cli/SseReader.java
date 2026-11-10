package com.chinacreator.gzcm.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * SSE 读取（{@code text/event-stream}），用于 {@code agent inspect --follow}。
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>只解析 SSE 三段（{@code event:} / {@code id:} / {@code data:}），不解释业务语义；</li>
 *   <li>支持 {@code Last-Event-ID} 断点续传（传 {@code sinceEventId} 即带上该头）；</li>
 *   <li>默认读取上限由 {@code maxEvents} 控制，避免无界流把内存打满；</li>
 *   <li>{@code --follow=false} 时由调用方决定不进入本类（本类不做交互判断）。</li>
 * </ul>
 */
public final class SseReader {

    public record Event(String event, String id, String data) {
    }

    private final String baseUrl;
    private final String traceId;
    private final long timeoutMs;
    private final HttpClient client;

    public SseReader(String baseUrl, String traceId, long timeoutMs) {
        this.baseUrl = baseUrl;
        this.traceId = traceId;
        this.timeoutMs = timeoutMs;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000L)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** 逐事件回调；返回已读取事件数。 */
    public int stream(String pathWithQuery, String token, String sinceEventId, int maxEvents,
                      Consumer<Event> consumer) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + pathWithQuery))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "text/event-stream")
                .header("X-Client", "cli")
                .header("X-Trace-Id", traceId)
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (sinceEventId != null && !sinceEventId.isBlank()) {
            builder.header("Last-Event-ID", sinceEventId);
        }
        HttpResponse<Stream<String>> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception e) {
            return -1;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return -1;
        }
        int count = 0;
        String event = null;
        String id = null;
        List<String> dataLines = new ArrayList<>();
        try (Stream<String> lines = response.body()) {
            var it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (line.isEmpty()) {
                    if (dataLines.size() > 0) {
                        consumer.accept(new Event(event, id, String.join("\n", dataLines)));
                        count++;
                        if (maxEvents > 0 && count >= maxEvents) {
                            break;
                        }
                    }
                    event = null;
                    id = null;
                    dataLines.clear();
                    continue;
                }
                if (line.startsWith(":")) {
                    continue; // 注释/心跳
                }
                int colon = line.indexOf(':');
                String field = colon < 0 ? line : line.substring(0, colon);
                String value = colon < 0 ? "" : line.substring(colon + 1).replaceFirst("^ ", "");
                switch (field) {
                    case "event" -> event = value;
                    case "id" -> id = value;
                    case "data" -> dataLines.add(value);
                    default -> { /* retry 等字段不解释 */ }
                }
            }
        }
        return count;
    }

    /** 事件 → 输出结构（脱敏后写入 data）。 */
    public static Map<String, Object> toRow(Event e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event", e.event());
        row.put("id", e.id());
        row.put("data", Redactor.redact(e.data()));
        return row;
    }
}
