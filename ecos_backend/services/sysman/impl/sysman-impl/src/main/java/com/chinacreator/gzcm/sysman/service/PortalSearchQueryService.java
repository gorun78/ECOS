package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.chinacreator.gzcm.sysman.dao.PortalSearchQueryDao;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PortalSearchQueryService — Portal 全局搜索只读查询层（PMO-39 批次2 T4）。
 *
 * <p>PMO-锚点链：PMO-39 批次2 T4（来源：检查报告 §6 #4 | 消费方：T4 前端点）。
 * <p>从 PortalSearchController 下沉的 JdbcTemplate 访问（符合"Controller 禁止
 * 直接用 JdbcTemplate — 必须走 Service 层"硬规则）。实体级检索（非全文），
 * 每类检索各自 try-catch 降级为空列表（表不存在时搜索不抛错）。</p>
 */
@Service
public class PortalSearchQueryService {

    private static final Logger log = LoggerFactory.getLogger(PortalSearchQueryService.class);

    /** 跨域单点入口（monolith 承流，ADR-15；BC 安全在 monolith 态直打本进程 gateway） */
    private static final String WORKFLOW_SEARCH_BASE = "http://localhost:8080";
    private static final int WORKFLOW_SEARCH_TIMEOUT_MS = 3000;

    /** 候选 agent 表（历史命名不一，按序探测，首个存在者生效） */
    public static final List<String> AGENT_TABLE_CANDIDATES =
            List.of("ecos_agent", "agent_mesh", "t_agent_mesh", "agent");

    private final PortalSearchQueryDao dao;
    private final RestTemplate workflowSearchHttpClient;

    public PortalSearchQueryService(PortalSearchQueryDao dao,
                                    Optional<RestTemplate> workflowSearchHttpClient) {
        this.dao = dao;
        // 共享 RestTemplate 存在则复用；否则内联建带超时的短命 RestTemplate 兜底
        this.workflowSearchHttpClient = workflowSearchHttpClient
                .orElseGet(PortalSearchQueryService::newWorkflowSearchHttpClient);
    }

    private static RestTemplate newWorkflowSearchHttpClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(WORKFLOW_SEARCH_TIMEOUT_MS);
        factory.setReadTimeout(WORKFLOW_SEARCH_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /** agent 只读检索（实体级，关键词过滤；表不存在时降级为空列表） */
    public List<Map<String, Object>> searchAgents(String keyword) {
        String like = "%" + keyword + "%";
        for (String table : AGENT_TABLE_CANDIDATES) {
            try {
                return dao.queryForList(
                        "SELECT id, name, status FROM " + table
                                + " WHERE name LIKE ? OR system_prompt LIKE ? LIMIT 20",
                        like, like);
            } catch (Exception e) {
                // 表不存在/无权限 → 试下一个候选表
            }
        }
        return List.of();
    }

    /** workflow 只读检索 */
    public List<Map<String, Object>> searchWorkflows(String keyword) {
        // 跨域入口（F03-14 / W88/铁律 IR01：域表只由本域引擎访问）：
        // 改经 buszhi REST 单点，禁止直查 buszhi 域表。
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(WORKFLOW_SEARCH_BASE)
                    .path("/api/v1/ontology/workflows")
                    .queryParam("keyword", keyword)
                    .queryParam("limit", 20)
                    .build(true)
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            ResponseEntity<List> response = workflowSearchHttpClient.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Object row : response.getBody()) {
                if (row instanceof Map<?, ?> m) {
                    Map<String, Object> converted = new LinkedHashMap<>();
                    m.forEach((k, v) -> converted.put(String.valueOf(k), v));
                    mapped.add(converted);
                }
            }
            return mapped;
        } catch (Exception e) {
            // buszhi 不可达/超时：门户搜索优雅降级为空列表（不静默吞数据，只呈现空直至 buszhi 就绪）
            log.error("searchWorkflows REST 调用失败，降级为空列表: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * glossary 术语表只读检索。
     *
     * <p>PMO-30 P1-1 多租户：{@code ecos_glossary_term} 走「仓库内手工租户」约定
     * （{@code TenantAwareJdbcTemplate} 对 INSERT 追加 WHERE 属语法非法，该表已移出
     * 自动重写名单），故此处显式加租户条件：本租户行 + 共享行（{@code tenant_id IS NULL}）。
     */
    public List<Map<String, Object>> searchGlossary(String keyword) {
        String like = "%" + keyword + "%";
        String tenantId = TenantContextHolder.getTenantId();
        try {
            if (tenantId == null || tenantId.isBlank()) {
                return dao.queryForList("""
                        SELECT code AS id, name AS term, definition
                        FROM ecos_glossary_term
                        WHERE name LIKE ? OR definition LIKE ?
                        LIMIT 20
                        """, like, like);
            }
            return dao.queryForList("""
                    SELECT code AS id, name AS term, definition
                    FROM ecos_glossary_term
                    WHERE (name LIKE ? OR definition LIKE ?)
                      AND (tenant_id = ? OR tenant_id IS NULL)
                    LIMIT 20
                    """, like, like, tenantId);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** scenario 场景只读检索 */
    public List<Map<String, Object>> searchScenarios(String keyword) {
        try {
            return dao.queryForList("""
                    SELECT id, name, description
                    FROM ecos_world_scenarios
                    WHERE name LIKE ? OR description LIKE ?
                    LIMIT 20
                    """, "%" + keyword + "%", "%" + keyword + "%");
        } catch (Exception e) {
            return List.of();
        }
    }
}
