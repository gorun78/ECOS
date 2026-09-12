package com.chinacreator.gzcm.engine.ontology.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DataNetResourceClient — 调 datanet service REST 拿数据资源采样
 *
 * Wave B-2 · T13
 * 来源: 肖国荣 / 日期: 2026-09-12 / 责任人: fullstack-implementer
 *
 * 调用 /api/v1/datanet/resources/{resourceId}/fields (datanet :18082)
 * 端点合同:
 *   GET /api/v1/datanet/resources/{resourceId}/fields
 *   返回 ApiResponse<List<FieldInfo>>, FieldInfo 含 fieldName/fieldType/description/primaryKey
 *
 * 临时处理(datanet 端点未落地):
 *   当前 datanet service 还没暴露该端点, client 在 Spring 配置项
 *   `ecos.datanet.base-url` 不可达或端点 404 时, 返回空列表,
 *   并在 log.warn 中明确标注 `TODO PMO-06 T19 补 datanet /resources/{id}/fields 端点合同`。
 *   这样 AutoDiscover 在端点就绪前不会 500, 只是不再显示候选字段, 等 datanet 端点补上即可无缝切回。
 */
@Component
public class DataNetResourceClient {

    private static final Logger log = LoggerFactory.getLogger(DataNetResourceClient.class);

    private final RestTemplate restTemplate;

    @Value("${ecos.datanet.base-url:http://localhost:18082}")
    private String datanetBaseUrl;

    public DataNetResourceClient() {
        // P1-2: 显式 3s connect/read timeout, 避免 datanet 宕机时阻塞主流程 (Wave B-2 P1-2 加固)
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 拿数据资源下的候选字段(用于本体自动发现预览)。
     *
     * @param resourceId 资源 id
     * @return 候选字段列表; 不可达或端点缺失时返回空列表(不抛异常)
     */
    public List<Map<String, Object>> listResourceFields(String resourceId) {
        String url = datanetBaseUrl + "/api/v1/datanet/resources/" + resourceId + "/fields";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
        } catch (Exception e) {
            // datanet 不可达或端点 404: 降级为空候选(不阻断自动发现)
            // TODO PMO-06 T19 补 datanet /resources/{id}/fields 端点合同
            log.warn("datanet fields 端点不可用 ({}), 自动发现候选将为空. {}", url, e.getMessage());
        }
        return Collections.emptyList();
    }
}
