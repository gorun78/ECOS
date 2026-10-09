package com.chinacreator.gzcm.workspace.knowledge.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.knowledge.dto.KnowledgeEngineConfigVO;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识工作台引擎配置聚合器 — E-A#3 产物。
 *
 * <p>scope 分发（按 PMO-55 E-A#3 要求）：
 * <ul>
 *   <li>{@code knowledge} — REST GET {@code {dccheng}/api/v1/knowledge/settings}
 *       （dccheng 承接 kb 引擎，settings 端点已存在）；</li>
 *   <li>{@code cognitive} — REST GET {@code {aiming}/api/v1/cognitive/config}
 *       （aiming 承接 cognitive config）；</li>
 *   <li>{@code all} — 上面两者 REST + 本地 sys_config 两域（本地优先覆盖）。</li>
 * </ul>
 *
 * <p>上下游响应体都是 {@code ApiResponse}（含 code/message/data），
 * 本聚合统一只解 data 载荷，写侧上游返回 {@code data.updated}=actual 写回行数。
 *
 * <p>降级：单次 REST 失败 → 该 scope 返回 _remote_failed 标记项（不 500，前端可见）。
 *
 * <p>卷07 F07-17 底座收口：base-url 由 {@code WorkspaceRestClientConfig} 经
 * runtime-access ServiceEndpointResolver 两态寻址解析（禁自建 RestTemplate / 硬编码
 * localhost:1808x），本类注入短超时（1500ms）探活 Bean，只传相对路径。
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-A
 */
@Service("ecosKnowledgeConfigAggregator")
public class KnowledgeConfigAggregator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeConfigAggregator.class);

    /** 允许的 scope 枚举 */
    private static final String[] SCOPES = {"knowledge", "cognitive", "all"};
    private static final String KNOWLEDGE_GROUP = "knowledge";
    private static final String COGNITIVE_GROUP = "cognitive-engine";

    private final WorkspaceDataDao dao;
    /** kb settings 客户端（rootUri = dccheng 基址 + /api/v1，1500ms 短超时）。 */
    private final RestTemplate kbRest;
    /** cognitive config 客户端（rootUri = aiming 基址 + /api/v1，1500ms 短超时）。 */
    private final RestTemplate cognitiveRest;

    public KnowledgeConfigAggregator(WorkspaceDataDao dao,
                                     @Qualifier("workspaceKbSettingsRest") RestTemplate kbRest,
                                     @Qualifier("workspaceCognitiveCfgRest") RestTemplate cognitiveRest) {
        this.dao = dao;
        this.kbRest = kbRest;
        this.cognitiveRest = cognitiveRest;
    }

    /**
     * 读取指定 scope 的引擎配置。
     *
     * @param scope knowledge / cognitive / all
     * @return KnowledgeEngineConfigVO（REST 或本地 sys_config 结果）
     */
    public KnowledgeEngineConfigVO read(String scope) {
        if (scope == null || !isValidScope(scope)) {
            throw new BusinessException("scope must be one of: " + String.join("/", SCOPES));
        }
        KnowledgeEngineConfigVO vo = new KnowledgeEngineConfigVO();
        vo.setScope(scope);
        Map<String, String> config = new LinkedHashMap<>();
        if (scope.equals("knowledge") || scope.equals("all")) {
            mergeRemote(config, "knowledge", readKnowledgeRemote(), true);
        }
        if (scope.equals("cognitive") || scope.equals("all")) {
            mergeRemote(config, "cognitive", readCognitiveRemote(), true);
        }
        if (scope.equals("all")) {
            // 本地 sys_config 双重保险：远端单引擎不可用时仍有可见项
            Map<String, String> local = fetchLocal(KNOWLEDGE_GROUP, COGNITIVE_GROUP);
            mergeRemote(config, "cognitive", local, false);
            // 用本地覆盖（如果远端没拿到 _remote_failed，本地是兜底）；
            // 若远端拿到真值（非 _remote_failed），保留远端版本
            for (Map.Entry<String, String> e : local.entrySet()) {
                if (!config.containsKey(e.getKey())) {
                    config.put(e.getKey(), e.getValue());
                }
            }
        }
        vo.setConfig(config);
        vo.setCount(config.size());
        return vo;
    }

    /**
     * 批量更新引擎配置（按 scope 转发到上游 settings / cognitive config PUT）。
     *
     * @param scope   knowledge 或 cognitive
     * @param entries key→value
     * @return 上游报告的写回行数
     */
    public int update(String scope, Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new BusinessException("entries required");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        List<Map<String, String>> reqBody = toBody(entries);
        HttpEntity<List<Map<String, String>>> entity = new HttpEntity<>(reqBody, headers);

        if ("knowledge".equals(scope)) {
            try {
                @SuppressWarnings("rawtypes")
                ResponseEntity<Map> resp = kbRest.exchange("/knowledge/settings", HttpMethod.PUT, entity, Map.class);
                return extractUpdated(resp.getBody());
            } catch (Exception ex) {
                log.error("kb settings remote PUT failed: {}", shortMsg(ex));
                throw new BusinessException("kb settings PUT failed: " + shortMsg(ex));
            }
        }
        if ("cognitive".equals(scope)) {
            try {
                @SuppressWarnings("rawtypes")
                ResponseEntity<Map> resp = cognitiveRest.exchange("/cognitive/config", HttpMethod.PUT, entity, Map.class);
                return extractUpdated(resp.getBody());
            } catch (Exception ex) {
                log.error("cognitive config remote PUT failed: {}", shortMsg(ex));
                throw new BusinessException("cognitive config PUT failed: " + shortMsg(ex));
            }
        }
        throw new BusinessException("scope must be 'knowledge' or 'cognitive' for PUT, got: " + scope);
    }

    // ── 远端 REST 读 ───────────────────────────────────

    private Map<String, String> readKnowledgeRemote() {
        return remoteGetConfigRows(kbRest, "/knowledge/settings", "kb");
    }

    private Map<String, String> readCognitiveRemote() {
        return remoteGetConfigRows(cognitiveRest, "/cognitive/config", "cognitive");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> remoteGetConfigRows(RestTemplate restTemplate, String path, String label) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<String, Object> body = resp.getBody();
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof List<?> list)) {
                map.put("_remote_failed", "upstream non-list data: " + (data == null ? "null" : data.getClass().getName()));
                return map;
            }
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String k = asString(m.get("config_key"));
                    String v = asString(m.get("config_value"));
                    if (k != null) {
                        map.put(k, v == null ? "" : v);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("{} settings remote read failed ({}): {}", label, shortMsg(ex), path);
            map.put("_remote_failed", shortMsg(ex));
        }
        return map;
    }

    // ── 远端 PUT 解析 ─────────────────────────────────

    private static int extractUpdated(Map<String, Object> body) {
        if (body == null) {
            return 0;
        }
        Object code = body.get("code");
        if (code instanceof Number n && n.intValue() != 0) {
            return 0; // upstream business error
        }
        Object data = body.get("data");
        if (data instanceof Map<?, ?> m) {
            Object updated = m.get("updated");
            if (updated instanceof Number n) {
                return Math.max(0, n.intValue());
            }
        }
        return 0;
    }

    // ── 本地 sys_config 兜底 (scope=all) ────────────────

    private Map<String, String> fetchLocal(String... groups) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String group : groups) {
            try {
                List<Map<String, Object>> rows = dao.listSysConfigByGroup(group);
                for (Map<String, Object> row : rows) {
                    Object k = row.get("config_key");
                    Object v = row.get("config_value");
                    if (k != null) {
                        map.put(String.valueOf(k), v == null ? "" : String.valueOf(v));
                    }
                }
            } catch (Exception ex) {
                log.warn("knowledge config local fetch failed group={}: {}", group, shortMsg(ex));
            }
        }
        return map;
    }

    // ── 辅助 ──────────────────────────────────────────

    private static void mergeRemote(Map<String, String> target, String label,
                                    Map<String, String> source, boolean warnIfNull) {
        if (source == null) {
            if (warnIfNull) {
                log.warn("knowledge config merge: {} returned null", label);
            }
            return;
        }
        for (Map.Entry<String, String> e : source.entrySet()) {
            target.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    private static boolean isValidScope(String scope) {
        for (String s : SCOPES) {
            if (s.equals(scope)) {
                return true;
            }
        }
        return false;
    }

    private static List<Map<String, String>> toBody(Map<String, String> entries) {
        List<Map<String, String>> body = new java.util.ArrayList<>(entries.size());
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            Map<String, String> m = new LinkedHashMap<>();
            m.put("config_key", e.getKey());
            m.put("config_value", e.getValue() == null ? "" : String.valueOf(e.getValue()));
            body.add(m);
        }
        return body;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String shortMsg(Throwable t) {
        String msg = t.getClass().getSimpleName();
        if (t.getMessage() != null) {
            msg = msg + ": " + t.getMessage();
        }
        return msg.length() > 160 ? msg.substring(0, 160) : msg;
    }
}
