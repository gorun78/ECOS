package com.chinacreator.gzcm.engine.ontology.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.exception.DataAccessException;

import java.util.List;
import java.util.Map;

/**
 * DataNetResourceClient — 调 data-engine（data 域）REST 拿数据资源元数据。
 *
 * <p>F02-15（详细设计-02，2026-09-30 实现）跨引擎取数契约修正：</p>
 * <ul>
 *   <li><b>两态寻址</b>：承流态为 monolith（gateway :8080，ADR-15 S0 已实测），base-url
 *       默认值由 <code>:18082</code>（未运行的独立 datanet）改为 <code>:8080</code>，
 *       显式配置项 {@code ecos.datanet.base-url} 优先。</li>
 *   <li><b>真实端点</b>：字段契约改指向存在的 <code>/api/v1/datanet/assets/{assetId}/fields</code>
 *       （旧 <code>/resources/{id}/fields</code> 在 data-engine 内无映射，结构必 404）。</li>
 *   <li><b>禁静默空值（fail-loud）</b>：依赖不可用/响应非法一律抛
 *       {@link DataAccessException}（错误码 <code>ECOS-DATA-041</code> 语义），
 *       <b>禁止再"返回空列表"让上层看起来成功</b>（D-14 根治）。</li>
 * </ul>
 *
 * @author ECOS-BE (F02-15)
 */
@Component
public class DataNetResourceClient {

    private static final Logger log = LoggerFactory.getLogger(DataNetResourceClient.class);

    /** 跨引擎依赖不可用统一错误码（详细设计-02 D.1，503 语义） */
    public static final String ECOS_DATA_041 = "ECOS-DATA-041";

    private final RestTemplate restTemplate;

    /**
     * F02-15：默认指向 monolith 承流口（gateway :8080）。
     * service 独立态（:18082）由部署方显式配置 {@code ecos.datanet.base-url} 覆盖，
     * 不再硬编码未运行的 18082 作为默认值。
     */
    @Value("${ecos.datanet.base-url:http://localhost:8080}")
    private String datanetBaseUrl;

    public DataNetResourceClient() {
        // P1-2: 显式 3s connect/read timeout, 避免依赖宕机时阻塞主流程 (Wave B-2 P1-2 加固)
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 拿数据资源下的候选字段（用于本体自动发现预览）。
     *
     * <p>F02-15：失败 fail-loud —— 依赖不可用或响应非法时抛
     * {@link DataAccessException}（不再"返回空列表"掩盖缺数据）。</p>
     *
     * @param assetId 数据资源（资产）id
     * @return 候选字段列表
     * @throws DataAccessException datanet/data-engine 不可达或响应非法（ECOS-DATA-041）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listResourceFields(String assetId) {
        String url = datanetBaseUrl + "/api/v1/datanet/assets/" + assetId + "/fields";
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
            throw new DataAccessException("datanet 资源字段响应非法 (ECOS-DATA-041): " + url);
        } catch (DataAccessException e) {
            log.error("datanet 资源字段端点响应非法 (ECOS-DATA-041, fail-loud 不降级空值): {}", url, e);
            throw e;
        } catch (Exception e) {
            log.error("datanet 资源字段端点不可用 (ECOS-DATA-041, fail-loud 不降级空值): {}", url, e);
            throw new DataAccessException(
                    "跨引擎取数依赖 data-engine 不可用 (ECOS-DATA-041): " + url, e);
        }
    }

    /**
     * 按分层拉取数据资源清单（PMO-B2 C4 映射校验用）。
     *
     * <p>端点：{@code GET /api/v1/engine/data/layers/{layer}}（已有端点），
     * 响应 {@code ApiResponse<Map>}，资源数组在 {@code data.resources}。
     *
     * @param layer 分层名（映射校验固定用 {@code CURATED}，即 DW 层）
     * @return 资源行列表（含 {@code resource_id / resource_name / source_path}）
     * @throws DataAccessException datanet service 不可达或响应非法时抛出（调用方据此区分「元数据不可用」与「表不存在」）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listResourcesByLayer(String layer) {
        String url = datanetBaseUrl + "/api/v1/engine/data/layers/" + layer;
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
        } catch (DataAccessException e) {
            log.error("datanet 分层资源端点响应非法 ({})", url, e);
            throw e;
        } catch (Exception e) {
            log.error("datanet 分层资源端点不可用 ({})", url, e);
            throw new DataAccessException("datanet 分层资源端点不可用: " + url, e);
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
     * @throws DataAccessException datanet service 不可达或响应非法时抛出
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMetadataFields(String resourceId) {
        String url = datanetBaseUrl + "/api/v1/datanet/metadata/fields/" + resourceId;
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
            throw new DataAccessException("datanet 元数据字段响应非法: " + url);
        } catch (DataAccessException e) {
            log.error("datanet 元数据字段端点响应非法 ({})", url, e);
            throw e;
        } catch (Exception e) {
            log.error("datanet 元数据字段端点不可用 ({})", url, e);
            throw new DataAccessException("datanet 元数据字段端点不可用: " + url, e);
        }
    }
}
