package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 度量取数面共享出向（F10-08「取度量」枝的跨引擎 REST 通道，#146b 落位）。
 *
 * <p><b>为什么单独一个客户端面而不是散在各 Source 里</b>：四个跨引擎 Source
 * （data.staleness_days / onto.conflict_count / know.sample_size / cog.cm_ready+backtest_ready）
 * 共享同一条纪律——基址经 gateway 承流（与 {@link EngineProbeClient} 同基址口径，
 * 注入同一枚 {@code wagentEngineProbeRest} Bean：3s 超时 + 基址延迟解析 Handler
 * + X-Request-Id 透传，两用途同形，禁另立客户端面）、<b>透传入站凭证</b>
 * （裁决 P0-3 同款：datanet/ontology/knowledge 业务面不登记匿名，裸调必 401 ⇒
 * 一律 {@link CallerCredentialSource} 逐字中继，禁自造服务身份头）、
 * 以及统一的 fail-closed 折叠（超时/非 2xx/空体/缺字段 ⇒ {@code absent}，
 * 401/403 与 404/5xx 分态留痕，禁折叠成"取到了空"）。散落在各 Source 的四支
 * 复制体一旦各自加一个默认主语或一个 "0 兜底"，就是差距分析要消灭的假真理同构体。</p>
 *
 * <p><b>异步腿的已知盲区（如实登记，不假装通了）</b>：{@link CallerCredentialSource}
 * 载体线程绑定，refill 复评若跑在调度线程（无 HTTP 上下文）⇒ 取不到凭证 ⇒
 * 401 ⇒ absent。这是有意的 fail-closed（D-B4-61 纪律③：把「取不到凭证」读成
 * 「无证据」而不是「策略拒绝」），服务间凭证签名腿另批处置；本类<b>不设任何
 * anonymous 兜底</b>。</p>
 */
public class ReadinessMetricFetchClient {

    private static final Logger log = LoggerFactory.getLogger(ReadinessMetricFetchClient.class);

    private final RestTemplate restTemplate;

    public ReadinessMetricFetchClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * GET 一个属主读端点并解开 {@code ApiResponse} 信封。
     *
     * @param path 完整路径（调用方按 {@link WAgentEngineCatalog} 前缀族拼定，本类不拼路径）
     * @return data 段（Map 形态）；任何"没取到"返回 null 并携带机器可读原因（见
     *         {@link FetchResult#reason()}），<b>永不抛</b>（一次评估不被一个取数面带崩）
     */
    public FetchResult fetchEnvelopeData(String path) {
        if (!WAgentEngineCatalog.isLiveServicePrefix(path)) {
            // 路径不在属主前缀族内 = 幻觉路径（枚举错/目录漂移），按不可达记，禁发出去 404 碰运气
            return FetchResult.fail("prefix-not-own:" + path);
        }
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(path, HttpMethod.GET,
                    credentialEntity(), Map.class);
            Map<?, ?> body = resp.getBody();
            if (body == null || body.isEmpty()) {
                return FetchResult.fail("remote-empty");
            }
            Object dataNode = body.get("data");
            if (!(dataNode instanceof Map<?, ?> data)) {
                return FetchResult.fail("envelope-missing-data");
            }
            return FetchResult.ok((Map) data);
        } catch (IllegalStateException baseUnresolved) {
            log.warn("[F10-08] 度量取数基址不可解析: path={} err={}", path, baseUnresolved.getMessage());
            return FetchResult.fail("base-url-unresolvable");
        } catch (HttpStatusCodeException httpErr) {
            log.warn("[F10-08] 度量取数回非 2xx: path={} status={}", path, httpErr.getRawStatusCode());
            return FetchResult.fail("http-" + httpErr.getRawStatusCode());
        } catch (ResourceAccessException ioOrTimeout) {
            log.warn("[F10-08] 度量取数超时/连接失败: path={} err={}", path, ioOrTimeout.getMessage());
            return FetchResult.fail("unreachable:timeout-or-connect");
        } catch (RestClientException malformed) {
            log.warn("[F10-08] 度量取数响应不可解析: path={} err={}", path, malformed.getMessage());
            return FetchResult.fail("unparsable-body");
        } catch (RuntimeException unexpected) {
            log.warn("[F10-08] 度量取数任务异常终止: path={} cause={}", path, String.valueOf(unexpected));
            return FetchResult.fail("fetch-task-failed");
        }
    }

    /**
     * GET 一个属主读端点并解开 {@code ApiResponse} 信封（data 段为 List 形态，
     * 如认知模型注册表列表）。折叠口径与 {@link #fetchEnvelopeData(String)} 逐字一致：
     * 同一路径前缀闸、同一凭证中继头、同一异常→原因字面映射，永不抛。
     *
     * <p>note：属主端点一律回 {@code ApiResponse} 包裹（data 段承载），本方法先解
     * {@code data} 再校验其为 List；行形态固定为 JSON 对象（Map）——行级非 Map ⇒
     * {@code envelope-row-shape}（属主端点契约漂移，禁把"看不懂"洗成"空结果"）；
     * body/data 缺失 ⇒ 与 Map 版同字面（{@code remote-empty}/{@code envelope-missing-data}）；
     * 空列表是<b>合法结论</b>（"无在册行"是实测事实，调用方据此给 false，与"取不到"分态）。</p>
     */
    @SuppressWarnings("unchecked")
    FetchResult fetchEnvelopeRows(String path) {
        if (!WAgentEngineCatalog.isLiveServicePrefix(path)) {
            return FetchResult.fail("prefix-not-own:" + path);
        }
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(path, HttpMethod.GET,
                    credentialEntity(), Map.class);
            Map<?, ?> body = resp.getBody();
            if (body == null || body.isEmpty()) {
                return FetchResult.fail("remote-empty");
            }
            Object dataNode = body.get("data");
            if (!(dataNode instanceof List<?> rows)) {
                return FetchResult.fail("envelope-missing-data");
            }
            if (rows.isEmpty()) {
                return FetchResult.okRows(List.of());
            }
            List<Map<String, Object>> out = new java.util.ArrayList<>(rows.size());
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> m)) {
                    return FetchResult.fail("envelope-row-shape");
                }
                out.add((Map<String, Object>) m);
            }
            return FetchResult.okRows(out);
        } catch (IllegalStateException baseUnresolved) {
            log.warn("[F10-08] 度量取数基址不可解析: path={} err={}", path, baseUnresolved.getMessage());
            return FetchResult.fail("base-url-unresolvable");
        } catch (HttpStatusCodeException httpErr) {
            log.warn("[F10-08] 度量取数回非 2xx: path={} status={}", path, httpErr.getRawStatusCode());
            return FetchResult.fail("http-" + httpErr.getRawStatusCode());
        } catch (ResourceAccessException ioOrTimeout) {
            log.warn("[F10-08] 度量取数超时/连接失败: path={} err={}", path, ioOrTimeout.getMessage());
            return FetchResult.fail("unreachable:timeout-or-connect");
        } catch (RestClientException malformed) {
            log.warn("[F10-08] 度量取数响应不可解析: path={} err={}", path, malformed.getMessage());
            return FetchResult.fail("unparsable-body");
        } catch (RuntimeException unexpected) {
            log.warn("[F10-08] 度量取数任务异常终止: path={} cause={}", path, String.valueOf(unexpected));
            return FetchResult.fail("fetch-task-failed");
        }
    }

    /** 出向头 = 入站凭证逐字中继（缺凭证不加头，令属主按默认 DENY 回 401，禁伪造主语）。 */
    private HttpEntity<Void> credentialEntity() {
        HttpHeaders headers = new HttpHeaders();
        CallerCredentialSource.applyTo(headers, CallerCredentialSource.current());
        return new HttpEntity<>(headers);
    }

    /**
     * 取数结论（永不含伪造值）。
     *
     * <p><b>两种成功态互斥</b>：{@code data()} ≠ null（Map 型属主端点）或 {@code rows()} ≠ null
     * （List 型属主端点）；fail 态两者皆 null 且 reason 非 null（机器可读原因落
     * {@code measured_json} 的 absent 原因）。推理一律走 {@link #ok()}，禁直接判字段——
     * 列表型成功的 {@code data()} 就是 null，直接判字段会把"取到了"读成"没取到"。</p>
     */
    public record FetchResult(Map<String, Object> data, List<Map<String, Object>> rows, String reason) {

        public FetchResult {
            if (data == null && rows == null && reason == null) {
                throw new IllegalArgumentException("FetchResult 三构件不得全空（禁无结论取数形态）");
            }
            if (data != null && rows != null) {
                throw new IllegalArgumentException("FetchResult data 与 rows 互斥（一次取数只有一种成功形态）");
            }
            if (reason != null && (data != null || rows != null)) {
                throw new IllegalArgumentException("FetchResult 成功态不得携带 reason（reason 只属于 fail 态）");
            }
        }

        /** Map 型成功（data 段为对象的属主端点）。 */
        public static FetchResult ok(Map<String, Object> data) {
            return new FetchResult(data, null, null);
        }

        /** List 型成功（data 段为数组的属主端点；空列表是合法结论，"无在册行"是实测事实）。 */
        public static FetchResult okRows(List<Map<String, Object>> rows) {
            return new FetchResult(null, rows == null ? List.of() : List.copyOf(rows), null);
        }

        public static FetchResult fail(String reason) {
            return new FetchResult(null, null, reason == null ? "fetch-failed" : reason);
        }

        /** 是否取到（两种成功形态唯一公共判据）。 */
        public boolean ok() {
            return data != null || rows != null;
        }
    }

    /** 把 Map 里的数值字段读成 BigDecimal（越域/形态错 ⇒ null，调用方折算 absent）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static java.math.BigDecimal asDecimal(Map<String, Object> data, String key) {
        Object raw = data.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return new java.math.BigDecimal(n.toString());
        }
        if (raw instanceof String s) {
            try {
                return new java.math.BigDecimal(s.trim());
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
        return null;
    }

    /** 把 Map 里的布尔/字面字段读成 Boolean（非 true/false 字面 ⇒ null，禁"非真即假"折叠）。 */
    public static Boolean asBoolean(Map<String, Object> data, String key) {
        Object raw = data.get(key);
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            String v = s.trim();
            if (v.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            }
            if (v.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            }
        }
        return null;
    }
}
