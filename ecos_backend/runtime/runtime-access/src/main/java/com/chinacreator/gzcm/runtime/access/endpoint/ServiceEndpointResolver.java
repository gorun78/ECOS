package com.chinacreator.gzcm.runtime.access.endpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 详细设计-00 C.2.3 两态寻址（ADR-15）唯一寻址出口。
 *
 * <p>所有引擎跨服务调用的 base-url 必须经本 resolver 解析，<b>禁止各引擎自拼</b>
 * {@code http://<host>:180xx}。数据源 = classpath {@code /route-manifest.json}
 * （单真源，gateway 构建期提供），两条 route 形态：
 * <ul>
 *   <li>{@link RouteMode#MONOLITH} → {@code http://localhost:<monolithPort>}（当前 fat-jar 承流）</li>
 *   <li>{@link RouteMode#SERVICE}  → {@code http://localhost:<servicePort>}（切流后独立 JAR）</li>
 * </ul>
 *
 * <p>容错：manifest 缺失 / 无该 service → 返回空，调用方决定降级（不抛到跨服务链路）。</p>
 */
@Component
public class ServiceEndpointResolver {

    public enum RouteMode { MONOLITH, SERVICE }

    private static final Logger log = LoggerFactory.getLogger(ServiceEndpointResolver.class);
    private static final String MANIFEST_CLASSPATH = "/route-manifest.json";
    private static final String HOST = "http://localhost";

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Map<String, ManifestRef> cache;

    public static final class ManifestRef {
        public final String owner;
        public final int monolithPort;
        public final int servicePort;
        public final String route;

        public ManifestRef(String owner, int monolithPort, int servicePort, String route) {
            this.owner = owner;
            this.monolithPort = monolithPort;
            this.servicePort = servicePort;
            this.route = route;
        }
    }

    /**
     * E.5 两态开关链（《从零实施版》§2.5.3）：
     * ① 显式运营覆写 {@link EcosRouteOverride}（{@code ecos.route.<svc>=monolith|service}）
     * ② route-manifest {@code route} 字段（切流登记面）
     * ③ 缺省 MONOLITH（设计默认态）。
     * 任一层给出 service 形态均受 manifest 切流事实约束：route=monolith 的条目
     * 即使运营键显式写 service 也回落 monolith port（上层 leg，不改运行期端口）。
     */
    public RouteMode effectiveMode(String service) {
        EcosRouteOverride lit = override;
        if (lit != null) { RouteMode m = lit.literalFor(service); if (m != null) return m; }
        ManifestRef ref = lookup(service).orElse(null);
        if (ref != null && ref.route != null && ref.route.equals("service")) {
            return RouteMode.SERVICE;
        }
        return RouteMode.MONOLITH;
    }

    /**
     * 按 service 名与运行形态返回 base-url（不含尾斜杠）。
     *
     * @param service 归属服务名（route-manifest 的 {@code owner} 字段：sysman/datanet/.../gateway）
     * @param mode    当前运行形态
     * @return base-url，如 {@code http://localhost:18082}；未登记返回 {@link Optional#empty}
     */
    public Optional<String> resolve(String service, RouteMode mode) {
        RouteMode requested = mode == RouteMode.MONOLITH ? RouteMode.MONOLITH : effectiveMode(service);
        return resolveWithMode(service, requested);
    }

    Optional<String> resolveWithMode(String service, RouteMode requested) {
        ManifestRef ref = lookup(service).orElse(null);
        if (ref == null) return Optional.empty();
        // service 形态但 route=monolith（尚未切流）→ 回落 monolith port，避免指向不承流的 service
        boolean useService = requested == RouteMode.SERVICE
                && ref.route != null && ref.route.equals("service");
        int port = useService ? ref.servicePort : ref.monolithPort;
        return Optional.of(HOST + ":" + port);
    }

    /** E.5 覆写位接线（gateway 单体内 {@code @Autowired @Lazy}；独立 JAR 未装配即 null＝未覆写）。 */
    void attachOverride(EcosRouteOverride override) {
        this.override = override;
    }

    private volatile EcosRouteOverride override;

    private Optional<ManifestRef> lookup(String service) {
        Map<String, ManifestRef> m = cache();
        return Optional.ofNullable(m.get(service == null ? "" : service.toLowerCase()));
    }

    private Map<String, ManifestRef> cache() {
        Map<String, ManifestRef> c = cache;
        if (c == null) {
            synchronized (this) {
                if (cache == null) cache = c = load();
            }
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ManifestRef> load() {
        try (InputStream in = ServiceEndpointResolver.class.getResourceAsStream(MANIFEST_CLASSPATH)) {
            if (in == null) {
                log.warn("route-manifest.json 未在 classpath 找到，ServiceEndpointResolver 退化为空表");
                return Collections.emptyMap();
            }
            Map<String, Object> root = mapper.readValue(in, new TypeReference<Map<String, Object>>() {});
            Object entries = root.get("entries");
            if (!(entries instanceof java.util.List)) return Collections.emptyMap();
            Map<String, ManifestRef> out = new HashMap<>();
            for (Object o : (java.util.List<Object>) entries) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> e = (Map<String, Object>) o;
                String owner = asString(e.get("owner"));
                if (owner == null) continue;
                Map<String, Object> ports = e.get("ports") instanceof Map
                        ? (Map<String, Object>) e.get("ports") : Collections.emptyMap();
                int mono = asInt(ports.get("monolith"), 8080);
                int svc = asInt(ports.get("service"), mono);
                out.put(owner.toLowerCase(), new ManifestRef(owner, mono, svc, asString(e.get("route"))));
            }
            return Collections.unmodifiableMap(out);
        } catch (Exception ex) {
            log.error("route-manifest.json 解析失败: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private static int asInt(Object o, int dflt) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) { try { return Integer.parseInt((String) o); } catch (NumberFormatException ignore) {} }
        return dflt;
    }
}
