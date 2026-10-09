package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 场景绑定关系边服务 — 架构铁律 §0.6.2 子图化（PMO-66 A3）。
 *
 * <p>承载六类绑定之间的有向关系（MAPPING/EXTRACTION/COGNITION/GOVERN/EXPOSE）：</p>
 * <pre>
 *   链节：DATASET → OBJECT_TYPE → KNOWLEDGE_BASE → AI_AGENT
 *   凸边：MAPPING / EXTRACTION / COGNITION / GOVERN / EXPOSE
 * </pre>
 *
 * <p>本服务独立访问 {@code ecos_scenario_binding_link} 表（本模块直接 JdbcTemplate 写入，
 * 与 {@link ScenarioSandboxLayoutService} 同族，暗承 §0.6 场景应用层三行铁律之内）；
 * 场景/binding 存在性独立查 {@code ecos_business_scenario} + {@code ecos_scenario_binding}。</p>
 */
@Service
public class ScenarioBindingLinkService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioBindingLinkService.class);

    private static final Set<String> ALLOWED_LINK_TYPES =
        Set.of("MAPPING", "EXTRACTION", "COGNITION", "GOVERN", "EXPOSE");

    private static final int MAX_LINKS_PER_SCENARIO = 12;

    private final WorkspaceDataDao dao;

    public ScenarioBindingLinkService(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    // ── 查询 ──────────────────────────────────────────────

    public List<ScenarioBindingLinkVO> listLinks(String scenarioId) {
        ensureScenario(scenarioId);
        return dao.listLinksOfScenario(scenarioId);
    }

    public ScenarioBindingLinkVO find(String scenarioId, String linkId) {
        ensureScenario(scenarioId);
        List<ScenarioBindingLinkVO> rows = dao.listLinkByIdAndScenario(linkId, scenarioId);
        if (rows.isEmpty()) {
            throw new NotFoundException("LINK-404: 边不存在");
        }
        return rows.get(0);
    }

    /**
     * 场景完整图 + 覆盖率 — A5 /graph 端点消费。
     * <p>覆盖率按 §0.6.2.3 真实连边计（非按数量算分）：</p>
     * <ul>
     *   <li>{@code d2iCoverage} = 有 MAPPING 边的 OBJECT_TYPE / 全部 OBJECT_TYPE（无 OBJECT_TYPE=1.0）</li>
     *   <li>{@code k2wCoverage} = 有 COGNITION 边的 AI_AGENT / 全部 AI_AGENT（无 AI_AGENT=1.0）</li>
     * </ul>
     */
    public ScenarioGraphVO graph(String scenarioId) {
        ensureScenario(scenarioId);

        List<Map<String, Object>> bindings = dao.listBindingRowsForGraph(scenarioId);
        List<ScenarioBindingLinkVO> links = listLinks(scenarioId);
        Set<String> touched = new HashSet<>();
        for (ScenarioBindingLinkVO l : links) {
            touched.add(l.getSourceBindingId());
            touched.add(l.getTargetBindingId());
        }

        List<ScenarioGraphNodeVO> nodes = new ArrayList<>(bindings.size());

        for (Map<String, Object> b : bindings) {
            String id = (String) b.get("id");
            String bt = (String) b.get("binding_type");
            boolean connected = touched.contains(id);
            nodes.add(new ScenarioGraphNodeVO(
                id, bt, (String) b.get("target_ref"), (String) b.get("target_id"),
                (String) b.get("target_type"), connected, "AI_AGENT".equals(bt)));
        }

        // 完整度（连边覆盖率）唯一来源是 ScenarioCompletenessService（F07-02），
        // 本拓扑仅供画布着色（connected 节点标记），不再产出任何节点占比口径的 coverage（X-23 已删除）。
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("totalNodes", nodes.size());
        topology.put("totalLinks", links.size());

        return new ScenarioGraphVO(scenarioId, nodes, links, topology);
    }

    // ── 写入 ──────────────────────────────────────────────

    /** 存盘一条边（§0.6.2.1 / §0.6.2.2 校验 + 孤岛校验）。 */
    @Transactional
    public ScenarioBindingLinkVO save(String scenarioId, ScenarioBindingLinkSaveDTO dto) {
        ensureScenario(scenarioId);

        String source = dto.getSourceBindingId();
        String target = dto.getTargetBindingId();
        if (source == null || source.isBlank()
                || target == null || target.isBlank()
                || source.equals(target)) {
            throw new BusinessException(400, "LINK-400: 双端 bindingId 不能为空或相同");
        }
        String linkType = dto.getLinkType() == null ? "" : dto.getLinkType().toUpperCase();
        if (!ALLOWED_LINK_TYPES.contains(linkType)) {
            throw new BusinessException(400,
                "LINK-400: linkType 必为 MAPPING|EXTRACTION|COGNITION|GOVERN|EXPOSE");
        }
        if ("MAPPING".equals(linkType)
                && (dto.getSourceContract() == null || dto.getSourceContract().isBlank())) {
            throw new BusinessException(400,
                "LINK-400: MAPPING 边必带 sourceContract（§0.6.2.2，指向本体↔物理表映射契约记录 id）");
        }
        requireBindingInScenario(scenarioId, source);
        requireBindingInScenario(scenarioId, target);

        Integer cnt = dao.countLinksOfScenario(scenarioId);
        if (cnt != null && cnt >= MAX_LINKS_PER_SCENARIO) {
            throw new BusinessException(400, "LINK-400: 场景边数超上限 " + MAX_LINKS_PER_SCENARIO);
        }
        Integer dup = dao.countDuplicateLink(scenarioId, source, target, linkType);
        if (dup != null && dup > 0) {
            throw new BusinessException(400, "LINK-409: 边已存在");
        }

        String operator = currentOperator();
        String id = "bsl_" + UUID.randomUUID().toString().substring(0, 12);
        dao.insertLink(
            id, scenarioId, source, target, linkType,
            dto.getSourceContract(),
            dto.getRemark() == null ? "" : dto.getRemark(),
            operator);

        postSaveIsolationCheck(scenarioId);
        log.info("边保存成功 linkId={} scenario={} type={} src={} tgt={}",
                id, scenarioId, linkType, source, target);
        return find(scenarioId, id);
    }

    /** 逻辑删除。 */
    @Transactional
    public void delete(String scenarioId, String linkId) {
        ensureScenario(scenarioId);
        String operator = currentOperator();
        int rows = dao.logicDeleteLink(operator, linkId, scenarioId);
        if (rows < 1) {
            throw new NotFoundException("LINK-404: 边不存在");
        }
    }

    // ── 私有辅助 ───────────────────────────────────────────

    private void ensureScenario(String scenarioId) {
        Integer n = dao.countScenarioForLink(scenarioId);
        if (n == null || n < 1) {
            throw new NotFoundException("LINK-404: 场景不存在: " + scenarioId);
        }
    }

    private void requireBindingInScenario(String scenarioId, String bindingId) {
        Integer n = dao.countBindingInScenario(bindingId, scenarioId);
        if (n == null || n < 1) {
            throw new BusinessException(400, "LINK-400: 绑定 " + bindingId + " 不属于场景 " + scenarioId);
        }
    }

    /**
     * 孤岛校验（§0.6.2.1）：场景同时含 DATASET（≥1）与 OBJECT_TYPE（≥1）时，
     * 必须存在 MAPPING 边（否则的对象无用 + 数据孤岛）。
     */
    private void postSaveIsolationCheck(String scenarioId) {
        List<Map<String, Object>> rows = dao.listBindingTypesOfScenario(scenarioId);
        int totalObjects = 0, totalDatasets = 0;
        for (Map<String, Object> b : rows) {
            String bt = (String) b.get("binding_type");
            if ("OBJECT_TYPE".equals(bt)) totalObjects++;
            if ("DATASET".equals(bt)) totalDatasets++;
        }
        if (totalDatasets < 1 || totalObjects < 1) return;
        Integer mappingCount = dao.countMappingLinksOfScenario(scenarioId);
        if (mappingCount == null || mappingCount < 1) {
            throw new BusinessException(400,
                "LINK-400: 孤岛场景（§0.6.2.1）—— DATASET 与 OBJECT_TYPE 无 MAPPING 边，请先建映射");
        }
    }

    private static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    private String currentOperator() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String) {
                return (String) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.debug("取操作人失败，回退 system: {}", e.getMessage());
        }
        return "system";
    }

    // ── DTO/VO（强类型出入参，禁 Map；本模块手写 getter/setter，未引入 Lombok） ──

    /** 边 save DTO — 入参契约。 */
    public static class ScenarioBindingLinkSaveDTO {
        private String sourceBindingId;
        private String targetBindingId;
        private String linkType;
        /** 跨工作台契约引用（MAPPING 必填，指向 ecos_entity_table_mapping.id） */
        private String sourceContract;
        private String remark;

        public String getSourceBindingId() { return sourceBindingId; }
        public void setSourceBindingId(String sourceBindingId) { this.sourceBindingId = sourceBindingId; }
        public String getTargetBindingId() { return targetBindingId; }
        public void setTargetBindingId(String targetBindingId) { this.targetBindingId = targetBindingId; }
        public String getLinkType() { return linkType; }
        public void setLinkType(String linkType) { this.linkType = linkType; }
        public String getSourceContract() { return sourceContract; }
        public void setSourceContract(String sourceContract) { this.sourceContract = sourceContract; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    /** 边 VO（Controller 出参强类型）。 */
    public static class ScenarioBindingLinkVO {
        private String id;
        private String scenarioId;
        private String sourceBindingId;
        private String targetBindingId;
        private String linkType;
        private String sourceContract;
        private String remark;
        private String versionNo;

        public ScenarioBindingLinkVO() {}

        public ScenarioBindingLinkVO(String id, String scenarioId, String sourceBindingId, String targetBindingId,
                                     String linkType, String sourceContract, String remark, String versionNo) {
            this.id = id;
            this.scenarioId = scenarioId;
            this.sourceBindingId = sourceBindingId;
            this.targetBindingId = targetBindingId;
            this.linkType = linkType;
            this.sourceContract = sourceContract;
            this.remark = remark;
            this.versionNo = versionNo;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getScenarioId() { return scenarioId; }
        public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
        public String getSourceBindingId() { return sourceBindingId; }
        public void setSourceBindingId(String sourceBindingId) { this.sourceBindingId = sourceBindingId; }
        public String getTargetBindingId() { return targetBindingId; }
        public void setTargetBindingId(String targetBindingId) { this.targetBindingId = targetBindingId; }
        public String getLinkType() { return linkType; }
        public void setLinkType(String linkType) { this.linkType = linkType; }
        public String getSourceContract() { return sourceContract; }
        public void setSourceContract(String sourceContract) { this.sourceContract = sourceContract; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getVersionNo() { return versionNo; }
        public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    }

    /** 节点 VO（/graph 端点出参）。 */
    public static class ScenarioGraphNodeVO {
        private String id;
        private String bindingType;
        private String targetRef;
        private String targetId;
        private String targetType;
        private boolean connected;
        /** 是否链末端（火 W / 木 C 侧终点） */
        private boolean terminal;

        public ScenarioGraphNodeVO() {}

        public ScenarioGraphNodeVO(String id, String bindingType, String targetRef, String targetId,
                                   String targetType, boolean connected, boolean terminal) {
            this.id = id;
            this.bindingType = bindingType;
            this.targetRef = targetRef;
            this.targetId = targetId;
            this.targetType = targetType;
            this.connected = connected;
            this.terminal = terminal;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getBindingType() { return bindingType; }
        public void setBindingType(String bindingType) { this.bindingType = bindingType; }
        public String getTargetRef() { return targetRef; }
        public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        public boolean isTerminal() { return terminal; }
        public void setTerminal(boolean terminal) { this.terminal = terminal; }
    }

    /** 图 VO（/graph 端点强类型出参）。 */
    public static class ScenarioGraphVO {
        private String scenarioId;
        private List<ScenarioGraphNodeVO> nodes;
        private List<ScenarioBindingLinkVO> links;
        /** 覆盖率：{d2iCoverage, k2wCoverage, totalNodes, totalLinks}（真实连边计，§0.6.2.3） */
        private Map<String, Object> coverage;

        public ScenarioGraphVO() {}

        public ScenarioGraphVO(String scenarioId, List<ScenarioGraphNodeVO> nodes,
                               List<ScenarioBindingLinkVO> links, Map<String, Object> coverage) {
            this.scenarioId = scenarioId;
            this.nodes = nodes;
            this.links = links;
            this.coverage = coverage;
        }

        public String getScenarioId() { return scenarioId; }
        public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
        public List<ScenarioGraphNodeVO> getNodes() { return nodes; }
        public void setNodes(List<ScenarioGraphNodeVO> nodes) { this.nodes = nodes; }
        public List<ScenarioBindingLinkVO> getLinks() { return links; }
        public void setLinks(List<ScenarioBindingLinkVO> links) { this.links = links; }
        public Map<String, Object> getCoverage() { return coverage; }
        public void setCoverage(Map<String, Object> coverage) { this.coverage = coverage; }
    }
}
