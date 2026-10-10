package com.chinacreator.gzcm.gateway.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * W01/F00-01（详细设计-00 C.2.3/C.4，M0）— 跨服务寻址唯一出口。
 *
 * <p>ADR-15 两态寻址：{@code monolith}（gateway 自身 :8080）与
 * {@code service}（<host>:180xx）。base-url 一律由 classpath
 * {@code route/route-manifest.json}（C.4 唯一真源）解析得出，
 * <b>禁止各引擎自拼 base-url</b>。既有 {@code @Value("${ecos.<svc>.base-url…}")}
 * 直接仍可用（API 只增不改），但新代码必须走本 resolver。
 */
public final class ServiceEndpointResolver {

    private static final String MANIFEST = "route/route-manifest.json";

    /** 寻址模式：monolith=gateway 宿主；service=独立 :180xx */
    public enum RouteMode { monolith, service }

    public record Entry(String prefix, String owner, String artifact,
                        int monolithPort, int servicePort, String mode) {
    }

    private final List<Entry> entries;
    private final int gatewayHostPort;
    private final String host;

    public ServiceEndpointResolver(InputStream manifest, int gatewayPort, String host) throws IOException {
        this.gatewayHostPort = gatewayPort;
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(manifest);
        JsonNode arr = root.get("entries");
        List<Entry> list = new java.util.ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                JsonNode p = n.get("ports");
                list.add(new Entry(
                        n.get("prefix").asText(),
                        n.get("owner").asText(),
                        n.path("artifact").asText(),
                        p.get("monolith").asInt(gatewayPort),
                        p.get("service").asInt(),
                        n.path("mode").asText("monolith")));
            }
        }
        this.entries = List.copyOf(list);
    }

    /** 按 service/owner 名解析 base-url（含 scheme，不含尾斜杠路径） */
    public String resolve(String owner, RouteMode mode) {
        Entry e = entries.stream().filter(x -> x.owner().equals(owner)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route-manifest 无 owner=" + owner));
        int port = mode == RouteMode.service ? e.servicePort() : e.monolithPort();
        return "http://" + host + ":" + port;
    }

    /** 按前缀命中 manifest 条目，返回其当前 mode（供诊断表/切流判态） */
    public String modeOfPrefix(String prefix) {
        return entries.stream().filter(x -> x.prefix().equals(prefix))
                .map(Entry::mode).findFirst().orElse("monolith");
    }

    public List<Entry> entries() {
        return entries;
    }

    public int gatewayPort() {
        return gatewayHostPort;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ServiceEndpointResolver && Objects.equals(entries, ((ServiceEndpointResolver) o).entries);
    }
}
