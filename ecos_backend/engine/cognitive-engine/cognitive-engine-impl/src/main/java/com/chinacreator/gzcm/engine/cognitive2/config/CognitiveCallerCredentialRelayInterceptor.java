package com.chinacreator.gzcm.engine.cognitive2.config;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 认知引擎共享 RestTemplate 的出站凭据中继适配器（B38/P1-12）。
 *
 * <p>实证：Context 源读取器（DATA_ASSET / KNOWLEDGE / COGNITIVE）以 {@code null} 实体裸调，
 * gateway 按匿名 403 ⇒ Context Pack 全部源 `UNAVAILABLE`（digest 只剩空包，不能作为追溯证据）。
 * 本适配器把当前请求已带的 Authorization + 四枚信任头逐字中继到出站请求。</p>
 *
 * <p>薄适配器：判据与取头逻辑唯一源 = {@link CallerCredentialSource}（跨引擎不复制判据）；
 * 无请求上下文（异步/定时）不加头，保持属主 fail-closed；调用方已设 Authorization 不覆盖。</p>
 */
public class CognitiveCallerCredentialRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        if (!headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            CallerCredentialSource.applyTo(headers);
        }
        return execution.execute(request, body);
    }
}
