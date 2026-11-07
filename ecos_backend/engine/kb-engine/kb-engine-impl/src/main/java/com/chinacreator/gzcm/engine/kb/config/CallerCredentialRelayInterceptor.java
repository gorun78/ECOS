package com.chinacreator.gzcm.engine.kb.config;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 跨引擎出站凭据中继拦截器（B33/P1-12 批量收口，kb 侧）。
 *
 * <p>实证背景：kb 抽取 {@code GET /api/v1/engine/data/layers/CURATED}、ontology
 * {@code SecurityEngineClient}、ai {@code AiSecurityEngineClient} 等裸注入共享
 * {@code RestTemplate} 的客户端一律不带凭据 ⇒ gateway/属主按匿名 403
 * （知识同步 job {@code ECOS-DATA-041} 即此腿）。</p>
 *
 * <p>纪律（与 {@link CallerCredentialSource} 单源一致）：
 * ①逐字中继当前请求已带的 Authorization + 四枚信任头，不拼接不改写不升权；
 * ②无请求上下文（异步/定时/SSE 线程）＝不加任何头，交由属主默认 DENY（fail-closed）；
 * ③已显式设置的 Authorization 不被覆盖（调用方自带头优先）。</p>
 */
public class CallerCredentialRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        if (!headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            // 单源中继：applyTo 只 putIfPresent 已携带的头，无上下文时不发明主语
            CallerCredentialSource.applyTo(headers);
        }
        return execution.execute(request, body);
    }
}
