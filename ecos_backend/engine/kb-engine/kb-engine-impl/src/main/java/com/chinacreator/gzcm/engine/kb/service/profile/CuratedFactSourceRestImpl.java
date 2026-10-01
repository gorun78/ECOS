package com.chinacreator.gzcm.engine.kb.service.profile;

import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link KbProfileService.CuratedFactSource} 生产实现（F04-06 / REQ-KB-02，只读 CURATED 层 PUBLISHED 事实）。
 *
 * <p>经 datanet REST 两态寻址读 CURATED（S-1，禁直读 RAW / B-1）；供数不可达 / 空响应 →
 * fail-loud 抛 {@code ECOS-KB-040}（C.3 画像取数，禁静默兜底 B-1）。指标→CURATED 列映射由 datanet
 * 指标/口径编码负责（分册 03，S-4），本类只按 (metric, dims, window) 取观测值列。
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.kb.service.KbProfileAssumptionConfig} 显式装配（非 @Component，
 * 避免与配置 bean 双注册）。
 */
public class CuratedFactSourceRestImpl implements KbProfileService.CuratedFactSource {

    private static final Logger log = LoggerFactory.getLogger(CuratedFactSourceRestImpl.class);

    private final RestTemplate restTemplate;
    private final String datanetOverride;

    @Autowired(required = false)
    private ServiceEndpointResolver endpointResolver;

    public CuratedFactSourceRestImpl(RestTemplate restTemplate,
                                     @Value("${ecos.route.datanet-base-url:}") String datanetOverride) {
        this.restTemplate = restTemplate;
        this.datanetOverride = datanetOverride;
    }

    @Override
    public List<Number> fetchValues(String metricCode, List<String> dims, String windowFrom, String windowTo) {
        String url = datanetBaseUrl() + "/api/v1/datanet/curated/observations/"
                + enc(metricCode)
                + "?from=" + enc(windowFrom) + "&to=" + enc(windowTo)
                + dims(dims);
        Map<String, Object> body;
        try {
            body = restTemplate.getForObject(url, Map.class);
        } catch (RuntimeException e) {
            log.error("CURATED 供数不可达 metric={} dims={}: {}", metricCode, dims, e.getMessage());
            throw supplyUnavailable(e.getMessage());
        }
        if (body == null) {
            throw supplyUnavailable("CURATED 供数空响应 metric=" + metricCode);
        }
        Object rows = body.get("rows");
        List<Number> out = new ArrayList<>();
        if (rows instanceof List<?> list) {
            for (Object r : list) {
                if (r instanceof Map<?, ?> row && row.values().stream().anyMatch(v -> v instanceof Number)) {
                    for (Object v : row.values()) {
                        if (v instanceof Number n) {
                            out.add(n);
                        }
                    }
                }
            }
        }
        return out;
    }

    private String dims(List<String> dims) {
        if (dims == null || dims.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String d : dims) {
            sb.append("&dim=").append(enc(d));
        }
        return sb.toString();
    }

    private String datanetBaseUrl() {
        if (datanetOverride != null && !datanetOverride.isBlank()) {
            return datanetOverride.trim();
        }
        if (endpointResolver == null) {
            throw supplyUnavailable("ServiceEndpointResolver 不可用，无法寻址 datanet");
        }
        return endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> supplyUnavailable("route-manifest 缺 datanet 条目"));
    }

    private static String enc(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** 供数不可达 → 控制器映射 ECOS-KB-040（502）。 */
    private static RuntimeException supplyUnavailable(String msg) {
        return KbErrorCodes.CURATED_SUPPLY_UNREACHABLE.exception(msg);
    }
}
