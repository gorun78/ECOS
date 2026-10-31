package com.chinacreator.gzcm.runtime.access.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * E.5 两态路由开关配置面单源（《从零实施版》§2.5.3 / 风险面 E.5 第 2 _items 收口载体）。
 *
 * <p>契约键面：{@code ecos.route.<service> = monolith | service}（默认 monolith）。
 * 本类是<b>显式运营覆写位</b>——绑定后按 service 名生效，缺项回落
 * {@code route-manifest.json} 的 {@code route} 字段（数据面单源仍是 manifest，
 * 本类只做覆写不误替）；未覆写的 service 一律判定 {@link ServiceEndpointResolver.RouteMode#MONOLITH}
 * （设计默认态）。</p>
 *
 * <p>fail-loud：任一覆写值不在闭集 {@code {monolith, service}} ⇒ 启动即抛
 * {@link IllegalStateException}（禁静默折叠成默认态——「拼错键名被当成没配」是本域既有假面族）。
 * 空覆写表与「未配置」同形，零行为变更。</p>
 */
@Component
public class EcosRouteOverride {

    public static final String KEY_PREFIX = "ecos.route.";
    public static final String LITERAL_MONOLITH = "monolith";
    public static final String LITERAL_SERVICE = "service";

    private static final Logger log = LoggerFactory.getLogger(EcosRouteOverride.class);

    private static final String[] ALLOWED = {LITERAL_MONOLITH, LITERAL_SERVICE};

    private volatile Map<String, String> overrides = Collections.emptyMap();

    void bind(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            this.overrides = Collections.emptyMap();
            return;
        }
        Map<String, String> normalized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String service = e.getKey() == null ? "" : e.getKey().trim().toLowerCase();
            String value = e.getValue() == null ? "" : e.getValue().trim().toLowerCase();
            boolean ok = false;
            for (String lit : ALLOWED) {
                if (lit.equals(value)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new IllegalStateException(
                        "E.5 两态路由开关非法值：ecos.route." + e.getKey() + "=" + e.getValue()
                                + "（闭集 = monolith|service，禁静默回落默认态）");
            }
            normalized.put(service, value);
        }
        this.overrides = Collections.unmodifiableMap(normalized);
        log.info("E.5 两态路由覆位在位 {} 项: {}", normalized.size(), normalized);
    }

    /** 覆写表非空判定（诊断面回显用）。 */
    public boolean hasOverrides() {
        return !overrides.isEmpty();
    }

    /** 覆写表只读投影（service → monolith|service）。 */
    public Map<String, String> overridesView() {
        return overrides;
    }

    /** 本 service 的覆写形态；未覆写返回 null（调用方回落 manifest route 字段或默认 MONOLITH）。 */
    public ServiceEndpointResolver.RouteMode literalFor(String service) {
        if (service == null || service.isBlank()) return null;
        String v = overrides.get(service.trim().toLowerCase());
        if (v == null) return null;
        return LITERAL_SERVICE.equals(v)
                ? ServiceEndpointResolver.RouteMode.SERVICE
                : ServiceEndpointResolver.RouteMode.MONOLITH;
    }
}
