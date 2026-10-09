package com.chinacreator.gzcm.workspace.trace;

import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 卷07 F07-21（W185）—— 出站 traceId 透传唯一拦截器。
 *
 * <p>workspace 全部 RestTemplate 出站（{@code WorkspaceRestClientConfig} 集中注册的
 * Bean）统一盖 {@code X-Request-Id}，值取自分册 00 单源
 * {@link EcosTraceContext#outboundHeader()}；调用点已显式带 header 时不覆盖
 * （上游透传优先）。本类不自造 ID、不新建第二套 trace 源。</p>
 */
public class TraceIdClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!request.getHeaders().containsKey(EcosTraceContext.HEADER_REQUEST_ID)) {
            EcosTraceContext.TraceHeader header = EcosTraceContext.outboundHeader();
            request.getHeaders().set(header.name(), header.value());
        }
        return execution.execute(request, body);
    }
}
