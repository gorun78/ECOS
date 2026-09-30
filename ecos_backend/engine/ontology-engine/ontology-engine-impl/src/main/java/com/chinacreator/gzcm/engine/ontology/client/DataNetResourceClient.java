package com.chinacreator.gzcm.engine.ontology.client;

import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * DataNetResourceClient — 调 datanet service REST 拿数据资源采样
 *
 * Wave B-2 · T13；F02-15 (详细设计-02, 2026-09-28) 改造：
 * <ul>
 *   <li>base URL 一律经 {@link ServiceEndpointResolver} 两态寻址（route-manifest 单源，
 *       owner=datanet，monolith :8080 / service :18082），禁引擎内硬编码端口字面量；</li>
 *   <li>{@code @Value("${ecos.route.datanet-base-url:}")} 仅作<b>可覆写</b>显式配置（默认空），
 *       非空使用用户值；为空则走 resolver；resolver 也无 → fail-loud 抛 ECOS_DATA_041；</li>
 *   <li>所有端点调用失败 fail-loud（503 + {@code ECOS-DATA-041}），<b>禁返回空列表</b>降级
 *       （D-14：旧版 listResourceFields 静默空列表会让本体/知识构建"看起来成功"）；</li>
 *   <li>listResourceFields 改真实端点 {@code GET /api/v1/datanet/assets/{assetId}/fields}
 *       （data-engine-impl AssetController，原 /resources/{id}/fields 404，D-14）
 *       （页面 items 元素含 fieldName / fieldType / primaryKey 兼容键）。</li>
 * </ul>
 */
@Component
public class DataNetResourceClient {

    private static final Logger log = LoggerFactory.getLogger(DataNetResourceClient.class);

    /** 跨引擎取数依赖不可用（F02-15/分册 00 D.5 错误码表；P0 fail-loud）。
     *  注：common-api 的 DataDomainErrorCode.ECOS_DATA_041 落地后应切换为常量引用。 */
    static final String CODE_CROSS_ENGINE_UNAVAILABLE = "ECOS-DATA-041";

    /** fail-loud 的 HTTP 状态（网关 GlobalExceptionHandler 依 DataBridgeException.httpStatus 透出）。 */
    static final int STATUS_CROSS_ENGINE_UNAVAILABLE = 503;

    private static final String OWNERSHIP_ERROR = "datanet 服务寻址失败（route-manifest 缺 datanet 条目或 resolver 不可用）";

    private final RestTemplate restTemplate;

    /** 跨引擎 base URL 唯一寻址出口（optional：boot 调试态可能不装配，缺席即 fail-loud）。 */
    @Autowired(required = false)
    private ServiceEndpointResolver endpointResolver;

    /** 可覆写显式配置（默认空 = 走 resolver）；禁带硬编码端口默认值（F02-15 ①）。 */
    @Value("${ecos.route.datanet-base-url:}")
    private String datanetBaseUrlOverride;

    public DataNetResourceClient() {
        // P1-2: 显式 3s connect/read timeout, 避免 datanet 宕机时阻塞主流程 (Wave B-2 P1-2 加固)
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 测试/特殊装配入口：base URL 由调用方供给（如固定指向死端口验证 fail-loud）。
     * 包可见，不参与 Spring 装配。
     */
    DataNetResourceClient(RestTemplate restTemplate, Supplier<String> datanetBaseUrlProvider) {
        this.restTemplate = restTemplate;
        this.datanetBaseUrlOverride = null;
        this.endpointResolver = null;
        this.baseUrlProvider = datanetBaseUrlProvider;
    }

    /** 默认 base URL 供给链：用户覆写 → resolver(MONOLITH) → fail-loud ECOS_DATA_041。 */
    private Supplier<String> baseUrlProvider = this::resolveDatanetBaseUrl;

    private String resolveDatanetBaseUrl() {
        if (datanetBaseUrlOverride != null && !datanetBaseUrlOverride.isBlank()) {
            return datanetBaseUrlOverride.trim();
        }
        if (endpointResolver == null) {
            throw failLoud(OWNERSHIP_ERROR + "，且 ServiceEndpointResolver bean 不可用");
        }
        return endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> {
                    log.error("route-manifest 未登记 datanet owner，跨引擎取数失败-loud");
                    return failLoud(OWNERSHIP_ERROR);
                });
    }

    /**
     * 构造 ECOS-DATA-041 fail-loud 异常（{@link DataBridgeException} 携带 503，
     * gateway {@code GlobalExceptionHandler#handleDataBridge} 依 httpStatus/errorCode 透出）。
     */
    private static DataBridgeException failLoud(String reason) {
        return new DataBridgeException(STATUS_CROSS_ENGINE_UNAVAILABLE, STATUS_CROSS_ENGINE_UNAVAILABLE,
                CODE_CROSS_ENGINE_UNAVAILABLE + ": " + reason);
    }

    /** 当前生效的 datanet base URL（寻址失败抛 ECOS-DATA-041）。 */
    private String baseUrl() {
        return baseUrlProvider.get();
    }

    /**
     * 拿数据资源（资产）下的候选字段(用于本体自动发现预览)。
     *
     * <p>F02-15 ③：端点不可达 / 响应非法一律 fail-loud 抛
     * {@code DataBridgeException}(503, {@code ECOS-DATA-041})，<b>禁返回空列表</b>；
     * 调用方（AutoDiscover）须向 UI 明示降级。
     *
     * @param assetId 数据资产 id（data-engine 资产主键 assetId）
     * @return 候选字段列表（每项含 fieldName / fieldType / primaryKey 兼容键），调用成功时非 null
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listResourceFields(String assetId) {
        String url = baseUrl() + "/api/v1/datanet/assets/" + assetId + "/fields";
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof Map<?, ?> page) {
                // AssetController 契约：data = DataAssetFieldPageVO（分页，items 为本页字段）
                Object items = page.get("items");
                if (items instanceof List) {
                    return normalizeFieldItems((List<Map<String, Object>>) items);
                }
                throw new DataAccessException("datanet 资产字段响应非分页结构: " + url);
            }
            if (data instanceof List) {
                return normalizeFieldItems((List<Map<String, Object>>) data);
            }
            throw new DataAccessException("datanet 资产字段响应非法: " + url);
        } catch (DataBridgeException e) {
            log.error("datanet 资产字段端点 fail-loud ({}): {}", url, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("datanet 资产字段端点不可用 ({})", url, e);
            throw failLoud("datanet 资产字段端点不可用: " + url
                    + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
    }

    /** 兼容历史消费方：DataAssetFieldVO 的 fieldType/confirmed 是否有用；primaryKey 缺失时置 false。 */
    private static List<Map<String, Object>> normalizeFieldItems(List<Map<String, Object>> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        for (Map<String, Object> item : items) {
            if (!item.containsKey("primaryKey")) {
                item.put("primaryKey", false);
            }
        }
        return items;
    }

    /**
     * 按分层拉取数据资源清单（PMO-B2 C4 映射校验用）。
     *
     * <p>端点：{@code GET /api/v1/engine/data/layers/{layer}}（已有端点），
     * 响应 {@code ApiResponse<Map>}，资源数组在 {@code data.resources}。
     *
     * @param layer 分层名（映射校验固定用 {@code CURATED}，即 DW 层）
     * @return 资源行列表（含 {@code resource_id / resource_name / source_path}）
     * @throws DataBridgeException datanet service 不可达或响应非法时抛出（503 + ECOS-DATA-041；
     *                             调用方据此区分「元数据不可用」与「表不存在」）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listResourcesByLayer(String layer) {
        String url = baseUrl() + "/api/v1/engine/data/layers/" + layer;
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Object resources = dataMap.get("resources");
                if (resources instanceof List) {
                    return (List<Map<String, Object>>) resources;
                }
            }
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
            throw new DataAccessException("datanet 分层资源响应非法: " + url);
        } catch (DataBridgeException e) {
            log.error("datanet 分层资源端点响应非法 ({})", url, e);
            throw e;
        } catch (Exception e) {
            log.error("datanet 分层资源端点不可用 ({})", url, e);
            throw failLoud("datanet 分层资源端点不可用: " + url
                    + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
    }

    /**
     * 拉取数据资源的列定义（PMO-B2 C4 映射校验用）。
     *
     * <p>端点：{@code GET /api/v1/datanet/metadata/fields/{resourceId}}（已有端点），
     * 响应 {@code {code, success, message, data: DataField[]}}，元素含 {@code fieldName / dataType}。
     *
     * @param resourceId 数据资源 ID（data-engine 数据资源主键 resource_id）
     * @return 字段行列表
     * @throws DataBridgeException datanet service 不可达或响应非法时抛出（503 + ECOS-DATA-041）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMetadataFields(String resourceId) {
        String url = baseUrl() + "/api/v1/datanet/metadata/fields/" + resourceId;
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
            throw new DataAccessException("datanet 元数据字段响应非法: " + url);
        } catch (DataBridgeException e) {
            log.error("datanet 元数据字段端点响应非法 ({})", url, e);
            throw e;
        } catch (Exception e) {
            log.error("datanet 元数据字段端点不可用 ({})", url, e);
            throw failLoud("datanet 元数据字段端点不可用: " + url
                    + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
    }

    /**
     * 构造 listResourceFields 的目标 URL（不含 base 寻址副作用，寻址失败时抛 ECOS-DATA-041）。
     *
     * <p>供单测断言端点契约（F02-15 验收 {@code DataNetResourcePathReachableTest}：
     * URL 前缀必须等于 data-engine-impl AssetController 的真实映射
     * {@code /api/v1/datanet/assets}）。
     */
    String buildFieldsUrl(String assetId) {
        return baseUrl() + "/api/v1/datanet/assets/" + assetId + "/fields";
    }
}
