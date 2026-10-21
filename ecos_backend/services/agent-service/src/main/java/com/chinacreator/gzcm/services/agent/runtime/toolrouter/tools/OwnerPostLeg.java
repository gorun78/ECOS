package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「向属主引擎发一次 POST 并按 {@link Outcome} 分态」的<b>传输骨架单源</b>
 * （#142/B4-3e-22，随 <b>D-B4-49 余项</b>写腿收口引入）。
 *
 * <p><b>为什么要有这个文件</b>：本域此前每条出腿都自带一套「拼请求头 → 发 → 抓异常 → 读信封」
 * 的四步代码。同一批要接两支写腿，若照旧各写一遍，就会出现三条各有细微差别的传输腿
 * （漏了 {@code Accept}、把 409 当 500、异常文案不一）——这正是 D-B4-49/60/61 一路在关的
 * 「同一件事多个实现面」。骨架在此收敛一次，<b>各腿只留自己专属的部分</b>：
 * 前置判据、请求体键面、成功时要回传的键。</p>
 *
 * <p><b>骨架钉死的四件事</b>：
 * ①<b>凭证经单源中继</b>：调用方凭证只由 {@link CallerCredentialSource#applyTo(HttpHeaders)} 写入，
 * 取不到就是不带（属主侧默认 DENY），本骨架不伪造主语、不做 anonymous 兜底（D-B4-61 三纪律）；
 * ②<b>非 2xx 状态行与信封码共用一套词表</b>：两条来源都只经 {@link Outcome} 落地，
 * 本文件之外不许出现状态码分派；
 * ③<b>传输失败＝结论未知</b>（{@link Outcome#UNREACHABLE}），不许折成拒绝或失败；
 * ④<b>属主的 {@code data} 段原样透传</b>，受理凭据（runId／评估 id 等）只能来自属主，
 * 本骨架与调用方都不自造。</p>
 *
 * <p><b>在册遗留一腿</b>：{@link QueryPhysicalTableTool}（#142/B4-3e-16）自带同一形状的四步骨架，
 * 其前置判据与只读语义专属、文案已被契约测试钉死，本批不并骨；
 * 由 {@code AgentToolOwnerWriteChannelSingleSourceTest} 把它登记为唯一的非骨架 {@code HttpStatusCodeException}
 * 持有者，<b>新增即红、下降也红</b>（下降说明骨架外的腿被改坏或基线未随批下调）。</p>
 */
final class OwnerPostLeg {

    private static final Logger log = LoggerFactory.getLogger(OwnerPostLeg.class);

    private OwnerPostLeg() {
    }

    /**
     * 一次出向调用的结论。
     *
     * @param outcome 分态结论（唯一成功出口由调用方经 {@code onSuccess} 指定）
     * @param message 属主理由或本地异常文案（可为 {@code null}）
     * @param data    属主 {@code data} 段（仅成功时非空，原样透传）
     */
    record Reply(Outcome outcome, String message, Map<String, Object> data) {

        /** 成功时的载荷：{@code available}＋{@code state}＋属主 {@code data} 段平铺。 */
        Map<String, Object> payloadWith(Map<String, Object> extra) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(AgentToolResponses.KEY_AVAILABLE, true);
            out.put(AgentToolResponses.KEY_STATE, outcome.name());
            if (extra != null) {
                out.putAll(extra);
            }
            if (data != null) {
                out.putAll(data);
            }
            if (message != null && !message.isBlank()) {
                out.putIfAbsent(AgentToolResponses.KEY_MESSAGE, message);
            }
            return out;
        }

        /** 非成功态的载荷（族名仅用于日志定位，不进机读面）。 */
        Map<String, Object> payload() {
            return Outcome.payload(outcome, message);
        }
    }

    /**
     * 发一次 JSON POST 并把结果归一成 {@link Reply}。
     *
     * @param base       已由 {@code AgentToolEndpointResolver} 解析出的属主基址（调用方负责取到非空）
     * @param path       属主端点路径（可含已拼好的查询串；调用方必须已对入参做形状判据）
     * @param body       请求体（{@code null} 视为空体）
     * @param onSuccess  {@code code == 0} 时的终态（写腿传 {@link Outcome#ACCEPTED}）
     */
    static Reply post(RestTemplate restTemplate, String base, String path,
                      Map<String, Object> body, Outcome onSuccess) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        CallerCredentialSource.applyTo(headers);

        Object envelope;
        try {
            envelope = restTemplate.postForObject(base + path, new HttpEntity<>(body == null ? Map.of() : body, headers),
                    Object.class);
        } catch (HttpStatusCodeException e) {
            Outcome outcome = Outcome.fromHttpStatus(e.getStatusCode().value());
            log.warn("[agent-tool] 属主返回非 2xx 状态: path={}, status={}, state={}", path, e.getStatusCode(), outcome);
            return new Reply(outcome, e.getMessage(), null);
        } catch (RestClientException e) {
            log.warn("[agent-tool] 调用属主端点失败（按结论未知处置，不冒充结果）: path={}, cause={}", path, e.getMessage());
            return new Reply(Outcome.UNREACHABLE, "属主端点调用失败：" + e.getMessage(), null);
        }
        return readEnvelope(envelope, onSuccess);
    }

    /**
     * 要拼进 URL 路径段的入参形状判据（单源）。
     *
     * <p>属主端点的 {@code @PathVariable} 是标识符而非自由文本：LLM 给的 id 里出现
     * {@code /}、{@code ?}、空白就能改写这条请求的落点（打到别的端点、或带出额外查询参数），
     * 这与「谁有权调」是两件事，必须在前置判据里挡掉，不许交给属主去猜。
     * 形状：{@code [A-Za-z0-9_.:-]{1,64}}。</p>
     */
    static boolean safePathSegment(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == ':';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 属主 {@code ApiResponse} 信封 → 分态；形态不可识别一律 {@link Outcome#UNREACHABLE}，不折成拒绝。 */
    private static Reply readEnvelope(Object envelope, Outcome onSuccess) {
        if (!(envelope instanceof Map<?, ?> map)) {
            return new Reply(Outcome.UNREACHABLE, "属主应答形态不可识别（非 envelope 对象），本次结论未知", null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> env = (Map<String, Object>) map;
        Outcome outcome = Outcome.fromEnvelope(Outcome.codeOf(env), Outcome.errorCodeOf(env), onSuccess);
        if (!outcome.succeeded()) {
            return new Reply(outcome, Outcome.messageOf(env), null);
        }
        Map<String, Object> data = env.get("data") instanceof Map<?, ?> dataMap
                ? new LinkedHashMap<>(cast(dataMap)) : null;
        return new Reply(outcome, Outcome.messageOf(env), data);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
