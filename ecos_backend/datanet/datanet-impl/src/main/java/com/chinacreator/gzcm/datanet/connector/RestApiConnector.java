package com.chinacreator.gzcm.datanet.connector;

import com.chinacreator.gzcm.datanet.model.DataResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REST API 连接器 — 通过 HTTP 调用外部 REST API 作为数据源。
 * <p>
 * 支持存量客户老系统的 REST API 接入 ECOS Pipeline。
 * connectionConfig JSON 格式：
 * <pre>{@code
 * {
 *   "baseUrl": "http://legacy-system:8080/api",
 *   "authType": "TOKEN",
 *   "authValue": "Bearer xxxxx",
 *   "timeout": 30
 * }
 * }</pre>
 * <p>
 * authType 可选: NONE, BASIC, TOKEN
 *
 * @author DataBridge Datanet Team
 */
@Component
public class RestApiConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(RestApiConnector.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String supportedType() {
        return "SOURCE_REST";
    }

    @Override
    public boolean testConnection(String connectionConfig) {
        try {
            Map<String, Object> config = parseConfig(connectionConfig);
            String baseUrl = (String) config.get("baseUrl");
            if (baseUrl == null || baseUrl.isBlank()) {
                log.warn("REST connection test failed: baseUrl is empty");
                return false;
            }

            int timeout = getTimeout(config);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(timeout))
                    .GET();

            applyAuth(builder, config);

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!success) {
                log.warn("REST connection test failed: {} returned HTTP {}", baseUrl, response.statusCode());
            }
            return success;
        } catch (Exception e) {
            log.warn("REST connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<DataResource> listResources(String connectionConfig, String orgId, String orgName) {
        List<DataResource> resources = new ArrayList<>();
        Map<String, Object> config = parseConfig(connectionConfig);

        String baseUrl = (String) config.get("baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("REST listResources: baseUrl is empty");
            return resources;
        }

        int timeout = getTimeout(config);

        // 尝试常见端点发现路径
        String[] discoveryPaths = {"", "/list", "/endpoints", "/resources", "/api"};
        for (String path : discoveryPaths) {
            try {
                String url = baseUrl + path;
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(timeout))
                        .GET();
                applyAuth(builder, config);

                HttpResponse<String> response = httpClient.send(
                        builder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String body = response.body();
                    if (body != null && !body.isBlank()) {
                        // 尝试解析为端点列表或单资源
                        extractResourcesFromResponse(body, url, orgId, orgName, resources, path.isEmpty() ? baseUrl : url);
                    }
                }
            } catch (Exception e) {
                log.debug("REST discovery path {} failed: {}", path, e.getMessage());
            }
        }

        // 如果没有发现任何资源，返回 baseUrl 本身作为单个资源
        if (resources.isEmpty()) {
            DataResource r = new DataResource();
            r.setResourceId(UUID.randomUUID().toString().replace("-", ""));
            r.setResourceName(extractEndpointName(baseUrl));
            r.setResourceType("API");
            r.setOrgId(orgId);
            r.setOrgName(orgName);
            r.setSourcePath(baseUrl);
            r.setDescription("REST API endpoint: " + baseUrl);
            r.setStatus("ACTIVE");
            r.setCreateTime(LocalDateTime.now());
            r.setUpdateTime(LocalDateTime.now());
            resources.add(r);
        }

        return resources;
    }

    @Override
    public List<Map<String, Object>> queryPreview(String connectionConfig, String tableName, int limit) {
        log.debug("REST API preview not supported for: {}", tableName);
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private void extractResourcesFromResponse(String body, String url, String orgId,
                                               String orgName, List<DataResource> resources, String defaultPath) {
        try {
            Object parsed = mapper.readValue(body, Object.class);
            if (parsed instanceof List) {
                List<Object> list = (List<Object>) parsed;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    DataResource r = buildApiResource(item, i, orgId, orgName, url);
                    if (r != null) resources.add(r);
                }
            } else if (parsed instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) parsed;
                // 尝试 data/items 等常见分页字段
                Object data = map.getOrDefault("data", map.getOrDefault("items", map.getOrDefault("records", parsed)));
                if (data instanceof List) {
                    List<Object> list = (List<Object>) data;
                    for (int i = 0; i < list.size(); i++) {
                        DataResource r = buildApiResource(list.get(i), i, orgId, orgName, url);
                        if (r != null) resources.add(r);
                    }
                } else {
                    DataResource r = new DataResource();
                    r.setResourceId(UUID.randomUUID().toString().replace("-", ""));
                    r.setResourceName(extractEndpointName(url));
                    r.setResourceType("API");
                    r.setOrgId(orgId);
                    r.setOrgName(orgName);
                    r.setSourcePath(defaultPath);
                    r.setDescription("REST API response with " + map.size() + " top-level fields");
                    r.setFieldCount(map.size());
                    r.setStatus("ACTIVE");
                    r.setCreateTime(LocalDateTime.now());
                    r.setUpdateTime(LocalDateTime.now());
                    resources.add(r);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse REST response as structured data: {}", e.getMessage());
        }
    }

    private DataResource buildApiResource(Object item, int index, String orgId, String orgName, String baseUrl) {
        DataResource r = new DataResource();
        r.setResourceId(UUID.randomUUID().toString().replace("-", ""));
        if (item instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            Object name = map.getOrDefault("name", map.getOrDefault("endpoint", "resource_" + index));
            r.setResourceName(String.valueOf(name));
            r.setSourcePath(baseUrl + "/" + map.getOrDefault("path", map.getOrDefault("id", name)));
            r.setFieldCount(map.size());
            r.setDescription("API resource: " + name);
        } else {
            r.setResourceName("resource_" + index);
            r.setSourcePath(baseUrl + "/" + index);
            r.setDescription("API resource item");
        }
        r.setResourceType("API");
        r.setOrgId(orgId);
        r.setOrgName(orgName);
        r.setStatus("ACTIVE");
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        return r;
    }

    private void applyAuth(HttpRequest.Builder builder, Map<String, Object> config) {
        String authType = (String) config.getOrDefault("authType", "NONE");
        String authValue = (String) config.getOrDefault("authValue", "");

        switch (authType.toUpperCase()) {
            case "BASIC":
                String encoded = Base64.getEncoder()
                        .encodeToString(authValue.getBytes());
                builder.header("Authorization", "Basic " + encoded);
                break;
            case "TOKEN":
                builder.header("Authorization", authValue);
                break;
            case "NONE":
            default:
                break;
        }
    }

    private int getTimeout(Map<String, Object> config) {
        Object timeoutObj = config.get("timeout");
        if (timeoutObj instanceof Number) {
            return ((Number) timeoutObj).intValue();
        }
        return 30; // 默认30秒
    }

    private String extractEndpointName(String url) {
        String name = url;
        // 去掉协议和域名
        int schemeEnd = url.indexOf("://");
        if (schemeEnd > 0) {
            name = url.substring(schemeEnd + 3);
        }
        // 去掉路径参数
        int qIndex = name.indexOf('?');
        if (qIndex > 0) {
            name = name.substring(0, qIndex);
        }
        // 取最后一段
        if (name.contains("/")) {
            String[] parts = name.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isBlank()) {
                    name = parts[i];
                    break;
                }
            }
        }
        return name.isEmpty() ? "api_root" : name;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String connectionConfig) {
        try {
            return mapper.readValue(connectionConfig, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid REST connection config JSON: " + connectionConfig, e);
        }
    }
}
