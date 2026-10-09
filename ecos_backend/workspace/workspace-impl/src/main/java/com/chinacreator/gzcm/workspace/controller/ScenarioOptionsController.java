package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.AvailableItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场景资源选项池 REST API — 6 类真 ID 跨服务联查（PMO-60 v2.0 P1）。
 *
 * <p>全部只读联查，跨服务 GET。各 service 不可达时返 503（铁律 §2.4-6 默认 DENY）。
 * 卷07 F07-17 底座收口：跨服务 base-url 不再硬编码，经 {@code WorkspaceRestClientConfig}
 * 注入带 rootUri 的共享 RestTemplate（runtime-access {@code ServiceEndpointResolver}
 * 两态寻址，ADR-15），业务侧只传相对路径。</p>
 * <pre>
 * GET /api/v1/workspace/scenarios/available/datasets   — datanet 数据源
 * GET /api/v1/workspace/scenarios/available/objects    — buszhi 本体实体
 * GET /api/v1/workspace/scenarios/available/knowledge  — dccheng 知识资产
 * GET /api/v1/workspace/scenarios/available/agents     — aiming Agent 配置
 * GET /api/v1/workspace/scenarios/available/security   — sysman 安全策略
 * GET /api/v1/workspace/scenarios/available/interfaces — sysman 接口引用
 * </pre></p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioOptionsController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioOptionsController.class);

    private final RestTemplate datanetRest;
    private final RestTemplate buszhiRest;
    private final RestTemplate dcchengRest;
    private final RestTemplate aimingRest;
    private final RestTemplate sysmanRest;

    public ScenarioOptionsController(
            @Qualifier("workspaceDatanetRest") RestTemplate datanetRest,
            @Qualifier("workspaceBuszhiRest") RestTemplate buszhiRest,
            @Qualifier("workspaceDcchengRest") RestTemplate dcchengRest,
            @Qualifier("workspaceAimingRest") RestTemplate aimingRest,
            @Qualifier("workspaceSysmanRest") RestTemplate sysmanRest) {
        this.datanetRest = datanetRest;
        this.buszhiRest = buszhiRest;
        this.dcchengRest = dcchengRest;
        this.aimingRest = aimingRest;
        this.sysmanRest = sysmanRest;
    }

    // ═══════════════ 6 类选项池端点 ═══════════════

    /** 可用数据集（datanet 数据源）。 */
    @GetMapping("/available/datasets")
    public ApiResponse<?> availableDatasets() {
        return callRemote(datanetRest, "/datanet/datasource", "datanet", "id", "name");
    }

    /** 可用本体实体（buszhi ontology entities，按 domain 过滤）。 */
    @GetMapping("/available/objects")
    public ApiResponse<?> availableObjects(@RequestParam(required = false) String domain) {
        return callRemoteObjects(domain);
    }

    /** 可用知识资产（dccheng knowledge assets）。 */
    @GetMapping("/available/knowledge")
    public ApiResponse<?> availableKnowledge(@RequestParam(required = false) String domain) {
        String path = (domain != null && !domain.isBlank())
                ? "/knowledge/assets?status=active&domain=" + domain
                : "/knowledge/assets?status=active";
        return callRemote(dcchengRest, path, "dccheng", "id", "title");
    }

    /** 可用 AI Agent（aiming agent profiles）。 */
    @GetMapping("/available/agents")
    public ApiResponse<?> availableAgents() {
        return callRemote(aimingRest, "/agent/profiles", "aiming", "id", "name");
    }

    /** 可用安全策略（sysman security policies — P1 新表 ecos_security_policy）。 */
    @GetMapping("/available/security")
    public ApiResponse<?> availableSecurity() {
        return callRemote(sysmanRest, "/security/policies", "sysman", "id", "name");
    }

    /** 可用接口引用（sysman interfaces — P1 新表 ecos_interface_ref）。 */
    @GetMapping("/available/interfaces")
    public ApiResponse<?> availableInterfaces() {
        return callRemote(sysmanRest, "/interfaces", "sysman", "id", "name");
    }

    // ═══════════════ 私有辅助 ═══════════════

    /**
     * 通用跨服务 GET 调用 → 解析 ApiResponse.data → 映射为 List<AvailableItemVO>。
     * 服务不可达返 503。path 为相对 Bean rootUri（base-url 单源=route-manifest，F07-17）的路径。
     */
    private ApiResponse<?> callRemote(RestTemplate restTemplate, String path, String serviceName,
                                      String idField, String nameField) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return unavailable(serviceName, "empty response");
            }
            Object code = body.get("code");
            if (code instanceof Number n && n.intValue() != 0) {
                log.warn("{} 业务错误 code={} msg={}", serviceName, n.intValue(), body.get("message"));
                return ApiResponse.error(n.intValue(), "remote_error", "upstream " + serviceName + ": " + body.get("message"));
            }
            Object data = body.get("data");
            List<AvailableItemVO> items = new ArrayList<>();
            if (data instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        items.add(toItem(m, idField, nameField));
                    }
                }
            }
            return ApiResponse.success(items);
        } catch (Exception ex) {
            log.warn("{} 不可达 {}: {}", serviceName, path, ex.getMessage());
            return unavailable(serviceName, ex.getMessage());
        }
    }

    /**
     * 本体对象联查：先调 buszhi 列出 ontology，再取首个 ontology 的 entities。
     * 简化实现：仅取 domain 下首个 ontology 的实体列表。
     */
    private ApiResponse<?> callRemoteObjects(String domain) {
        String path = "/ecos/ontologies";
        if (domain != null && !domain.isBlank()) {
            path += "?domain=" + domain;
        }
        try {
            ResponseEntity<Map> resp = buszhiRest.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return unavailable("buszhi", "empty response");
            }
            Object code = body.get("code");
            if (code instanceof Number n && n.intValue() != 0) {
                return ApiResponse.error(n.intValue(), "remote_error", "upstream buszhi: " + body.get("message"));
            }
            Object data = body.get("data");
            List<AvailableItemVO> items = new ArrayList<>();
            if (data instanceof List<?> ontologies) {
                // 取首个 ontology 的 id，再查其 entities
                for (Object o : ontologies) {
                    if (o instanceof Map<?, ?> m) {
                        String oid = asString(m.get("id"));
                        if (oid == null) continue;
                        try {
                            ResponseEntity<Map> entResp = buszhiRest.exchange(
                                "/ecos/ontologies/" + oid + "/entities",
                                HttpMethod.GET, HttpEntity.EMPTY, Map.class);
                            Map<String, Object> entBody = entResp.getBody();
                            if (entBody == null) continue;
                            Object entData = entBody.get("data");
                            if (entData instanceof List<?> entities) {
                                for (Object e : entities) {
                                    if (e instanceof Map<?, ?> em) {
                                        AvailableItemVO item = toItem(em, "id", "name");
                                        if (item.getExtra() == null) {
                                            item.setExtra(new LinkedHashMap<>());
                                        }
                                        item.getExtra().put("ontologyId", oid);
                                        item.getExtra().put("code", asString(em.get("code")));
                                        items.add(item);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            log.debug("ontology {} entities 查询失败: {}", oid, ex.getMessage());
                        }
                    }
                }
            }
            return ApiResponse.success(items);
        } catch (Exception ex) {
            log.warn("buszhi 不可达 {}: {}", path, ex.getMessage());
            return unavailable("buszhi", ex.getMessage());
        }
    }

    private ApiResponse<?> unavailable(String serviceName, String reason) {
        // 卷07 F07-12 / X-17：禁把 503 包成 HTTP 200（A3 红线）。抛领域异常，真实 503 由
        // WorkspaceExceptionHandler（F07-13 advice）映射；本层绝不再返回 ApiResponse.error(503,…)。
        String clipped = reason != null && reason.length() > 128 ? reason.substring(0, 128) : reason;
        throw new com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException(
                serviceName + " unreachable: " + clipped);
    }

    /** 安全字段白名单——仅这些 key 允许透传到前端 extra（防 host/port/username/jdbcUrl/password 泄漏） */
    private static final Set<String> EXTRA_WHITELIST = Set.of(
            "code", "domain", "status", "type", "health", "labels",
            "name", "description", "tags", "category", "priority", "enabled"
    );

    @SuppressWarnings("unchecked")
    private static AvailableItemVO toItem(Map<?, ?> m, String idField, String nameField) {
        String id = asString(m.get(idField));
        String name = asString(m.get(nameField));
        if (name == null) {
            name = asString(m.get("label"));
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (EXTRA_WHITELIST.contains(key.toLowerCase()) && e.getValue() != null
                    && !key.equals(idField) && !key.equals(nameField) && !"label".equals(key)) {
                extra.put(key, e.getValue());
            }
        }
        return new AvailableItemVO(id, name, extra);
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
