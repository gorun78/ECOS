package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.security.CallerCredentialRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 必需边契约探测的生产实现（二期 D-W8-1：{@link ContractProbeFacade} 此前全仓零生产实现，
 * 致 {@code EdgeContractProbe} 恒走 facade==null 分支 ⇒ 连边覆盖率恒 0 且无 probeError，
 * 库内真实存在的映射/导入契约在场景工作台不可见）。
 *
 * <p><b>只走 api 门面 HTTP</b>（卷07 F07-03-2 铁律：禁直读对方 schema），客户端一律注入
 * {@code WorkspaceRestClientConfig} 的限定 Bean（base-url 经 route-manifest 两态寻址，F07-17），
 * 出站凭证经 {@link CallerCredentialRelay} 中继入口请求的 Authorization/信任头——
 * 不中继则引擎侧默认 DENY，会把 plumbing 缺口读成"契约不存在"（D-W8-3 同源教训）。</p>
 *
 * <p><b>三边探测源（2026-10-17 live 实测）</b>：
 * <ul>
 *   <li>MAPPING（DATASET→OBJECT_TYPE）= {@code GET /ontology/entity-mappings}，
 *       计数取 data 数组长度，最近校验时间取 max(updatedAt, createdAt)；</li>
 *   <li>EXTRACTION（OBJECT_TYPE→KNOWLEDGE_BASE）= {@code GET /knowledge/sync/status}，
 *       计数取各 objectType 的 syncedCount 之和，时间取 max(lastSyncTime)；</li>
 *   <li>COGNITION（KNOWLEDGE_BASE→AI_AGENT）= <b>无契约载体</b>：智能体注册表
 *       （{@code ecos_ai.ecos_agent_registry}）只有 capability/metadata，全仓零处持有知识库引用，
 *       故本实现显式抛错而不是返回 0，让缺失原因以 probeError 面暴露（登记于二期规划册），
 *       禁止把"数据模型缺列"冒充成"演示数据缺行"。</li>
 * </ul></p>
 *
 * <p>失败语义：探测异常一律上抛，由 {@link EdgeContractProbe} 降级为 missing，本类不外溢到端点。</p>
 */
@Component
public class HttpContractProbeFacade implements ContractProbeFacade {

    private static final Logger log = LoggerFactory.getLogger(HttpContractProbeFacade.class);

    private static final String MAPPING_PATH = "/ontology/entity-mappings";
    private static final String EXTRACTION_PATH = "/knowledge/sync/status";

    private final RestTemplate ontologyRest;
    private final RestTemplate knowledgeRest;

    public HttpContractProbeFacade(@Qualifier("workspaceBuszhiRest") RestTemplate ontologyRest,
                                   @Qualifier("workspaceDcchengRest") RestTemplate knowledgeRest) {
        this.ontologyRest = ontologyRest;
        this.knowledgeRest = knowledgeRest;
    }

    @Override
    public ContractSnapshot fetch(RequiredEdge edge) {
        if (edge == null || edge.getType() == null) {
            throw new IllegalArgumentException("契约探测缺少边声明");
        }
        return switch (edge.getType()) {
            case MAPPING -> probeMapping();
            case EXTRACTION -> probeExtraction();
            case COGNITION -> throw new IllegalStateException(
                    "COGNITION 边无契约载体：智能体注册表未持有知识库引用（需求侧缺口，非探测故障）");
            case GOVERN, EXPOSE -> throw new IllegalStateException(
                    "非必需边不参与契约探测: " + edge.getType());
        };
    }

    private ContractSnapshot probeMapping() {
        List<?> rows = requestList(MAPPING_PATH, ontologyRest, "buszhi");
        Instant latest = null;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> m)) {
                continue;
            }
            Instant ts = maxInstant(parseTimestamp(m.get("updatedAt")), parseTimestamp(m.get("createdAt")));
            latest = maxInstant(latest, ts);
        }
        return new ContractSnapshot(rows.size(), latest);
    }

    private ContractSnapshot probeExtraction() {
        Map<?, ?> data = requestMap(EXTRACTION_PATH, knowledgeRest, "dccheng");
        long total = 0L;
        Instant latest = null;
        Object objectTypes = data.get("objectTypes");
        if (objectTypes instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                total += asLong(m.get("syncedCount"));
                latest = maxInstant(latest, parseTimestamp(m.get("lastSyncTime")));
            }
        }
        return new ContractSnapshot(total, latest);
    }

    // ═══════════════ 私有辅助 ═══════════════

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object requestData(String path, RestTemplate rest, String serviceName) {
        HttpHeaders headers = new HttpHeaders();
        CallerCredentialRelay.applyTo(headers);
        ResponseEntity<Map> resp = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> body = resp.getBody();
        if (body == null) {
            throw new IllegalStateException(serviceName + " 契约探测应答为空体");
        }
        if (body.get("code") instanceof Number n && n.intValue() != 0) {
            throw new IllegalStateException(serviceName + " 契约探测业务错误 code=" + n.intValue()
                    + " message=" + body.get("message"));
        }
        Object data = body.get("data");
        if (data == null) {
            throw new IllegalStateException(serviceName + " 契约探测应答缺 data 段（契约面形态漂移）");
        }
        return data;
    }

    private List<?> requestList(String path, RestTemplate rest, String serviceName) {
        Object data = requestData(path, rest, serviceName);
        if (!(data instanceof List<?> list)) {
            throw new IllegalStateException(serviceName + " 契约探测 data 非数组形态: "
                    + data.getClass().getSimpleName());
        }
        log.debug("契约探测 {} 计数={}", path, list.size());
        return list;
    }

    private Map<?, ?> requestMap(String path, RestTemplate rest, String serviceName) {
        Object data = requestData(path, rest, serviceName);
        if (!(data instanceof Map<?, ?> map)) {
            throw new IllegalStateException(serviceName + " 契约探测 data 非对象形态: "
                    + data.getClass().getSimpleName());
        }
        return map;
    }

    /** 兼容引擎侧两种时间形态：{@code 2026-10-17 02:44:40.631153}（本地无时区）与 ISO（含 Z/偏移）。 */
    static Instant parseTimestamp(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        String normalized = text.replace(' ', 'T');
        // 先按「带时区段」形态整体解析，再回落「无时区段」本地形态。
        // 原先用 regionMatches(10, "+", …) 探偏移，索引 10 恒落在日期后的 T/时分首位，
        // `2026-10-17T02:44:40+01:00` 只走到 LocalDateTime.parse ⇒ 抛异常后被吞成 null
        // （= 契约探测把"引擎给了时间"误判成"引擎没给"，probe 假缺）。
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeParseException ignored) {
            // 落到本地无时区形态
        }
        try {
            return LocalDateTime.parse(normalized).atZone(java.time.ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Instant maxInstant(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private static long asLong(Object raw) {
        return raw instanceof Number n ? n.longValue() : 0L;
    }
}
