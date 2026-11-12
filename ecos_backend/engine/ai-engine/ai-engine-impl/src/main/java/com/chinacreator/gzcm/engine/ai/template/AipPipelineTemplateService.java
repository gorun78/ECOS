package com.chinacreator.gzcm.engine.ai.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ai.dao.AipPipelineDao;
import com.chinacreator.gzcm.engine.ai.dataobject.AipPipelineDO;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentMissionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.PlaybookContentSource;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.PlaybookRenderer;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wagent Playbook 模板服务（AI 工作台模板融合批 2）。
 *
 * <p><b>两条腿</b>：
 * <ol>
 *   <li>{@link #listTemplates} —— 模板目录：本租户有在用 Playbook 引用的 mission 行
 *       （去重后按 ref+version 首见），叠加已发布 candidate（批 3 来源扩面）。</li>
 *   <li>{@link #instantiate} —— 实例化：读 Playbook 正文（{@link PlaybookContentSource}，
 *       Git 归档单源）→ {@link PlaybookRenderer} 确定性填槽 → {@link WAgentPlan.PlanCodec}
 *       解析 → {@link PlaybookToGraphConverter} 转画布 → 落 {@code ecos_aip_pipeline}。</li>
 * </ol>
 *
 * <p><b>内容源缺席即如实空目录</b>：{@code PlaybookContentSource} 未装配（#131 前常态）时
 * 目录照常列出（引用元数据在 DB 里），实例化返回 422 并说明「Playbook 内容源未装配」——
 * 禁伪造模板正文，也禁把缺席读成「模板不存在」。</p>
 */
@Service
public class AipPipelineTemplateService {

    private static final Logger log = LoggerFactory.getLogger(AipPipelineTemplateService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WAgentMissionDao missionDao;
    private final AipPipelineDao pipelineDao;
    private final PlaybookRenderer playbookRenderer;
    private final PlaybookToGraphConverter converter;
    private final ObjectProvider<PlaybookContentSource> contentSourceProvider;

    public AipPipelineTemplateService(WAgentMissionDao missionDao,
                                      AipPipelineDao pipelineDao,
                                      PlaybookRenderer playbookRenderer,
                                      PlaybookToGraphConverter converter,
                                      ObjectProvider<PlaybookContentSource> contentSourceProvider) {
        this.missionDao = missionDao;
        this.pipelineDao = pipelineDao;
        this.playbookRenderer = playbookRenderer;
        this.converter = converter;
        this.contentSourceProvider = contentSourceProvider;
    }

    /** 模板目录项（ref 即 instantiate 的路径参数）。 */
    public record TemplateItem(String ref, String name, String version, String summary,
                               String domain, String source) {
    }

    /** 实例化回执。 */
    public record InstantiateResult(String pipelineId, int mapped, List<String> unmapped) {
    }

    /** 业务异常（由 AiErrorAdvice 按类型渲染：404 走 NotFound，其余走 IllegalArgumentException）。 */
    public static class TemplateException extends RuntimeException {
        private final int status;
        public TemplateException(int status, String message) {
            super(message);
            this.status = status;
        }
        public int status() { return status; }
    }

    /** 模板引用不存在 → 404 语义（AiErrorAdvice.onNotFound 的入参类型）。 */
    public static class TemplateNotFoundException extends com.chinacreator.gzcm.common.exception.NotFoundException {
        public TemplateNotFoundException(String message) {
            super(message);
        }
    }

    /** 参数/状态不可满足（内容源缺席、正文非法、计划结构非法）→ 400/422 语义。 */
    public static class TemplateValidationException extends IllegalArgumentException {
        public TemplateValidationException(String message) {
            super(message);
        }
    }

    /** 模板目录：mission 有 Playbook 引用者去重（ref+version 首见），保持最近更新在前。 */
    public List<TemplateItem> listTemplates(String tenantId) {
        Set<String> seen = new LinkedHashSet<>();
        List<TemplateItem> items = new ArrayList<>();
        for (WAgentMissionDO mission : missionDao.findWithPlaybookRef(tenantId)) {
            String ref = mission.getPlaybookGitRef();
            String version = mission.getPlaybookVersion();
            String key = ref + "@" + version;
            if (!seen.add(key)) continue;
            items.add(new TemplateItem(ref, mission.getName(), version,
                    "mission: " + mission.getId(), mission.getDomain(), "PLAYBOOK"));
        }
        for (TemplateItem candidate : listPublishedCandidates(tenantId)) {
            if (seen.add(candidate.ref() + "@" + candidate.version())) {
                items.add(candidate);
            }
        }
        return items;
    }

    /**
     * 已发布候选来源（批 3）：candidate 若在 payload 里声明了 playbook 引用
     * （{@code playbookGitRef}/{@code playbook_git_ref}），作为模板来源之一暴露；
     * 未声明的候选不伪造引用、不进目录。
     */
    private List<TemplateItem> listPublishedCandidates(String tenantId) {
        List<TemplateItem> out = new ArrayList<>();
        try {
            for (Map<String, Object> row : queryPublishedCandidates(tenantId)) {
                String payload = str(row.get("payload_json"));
                if (payload == null) continue;
                Map<?, ?> payloadMap = MAPPER.readValue(payload, Map.class);
                String ref = firstNonBlank(str(payloadMap.get("playbookGitRef")),
                        str(payloadMap.get("playbook_git_ref")));
                String version = firstNonBlank(str(payloadMap.get("playbookVersion")),
                        str(payloadMap.get("playbook_version")));
                if (ref == null || version == null) continue;
                out.add(new TemplateItem(ref, str(row.get("name")) == null ? ref : str(row.get("name")),
                        version, "candidate: " + str(row.get("id")), null, "CANDIDATE"));
            }
        } catch (Exception e) {
            // 候选来源是加性面：查询/解析失败不影响 Playbook 主目录
            log.warn("已发布候选模板来源读取失败（Playbook 目录不受影响）: {}", e.getMessage());
        }
        return out;
    }

    private List<Map<String, Object>> queryPublishedCandidates(String tenantId) {
        return pipelineDao.queryCandidateTemplates(tenantId);
    }

    /**
     * 实例化：Playbook → 画布骨架 → 落库。
     *
     * @throws TemplateNotFoundException  引用不存在或不可见
     * @throws TemplateValidationException 内容源缺席 / 正文非法 / 计划结构非法 / 转换结果为空
     */
    public InstantiateResult instantiate(String tenantId, String operator, String ref, String name,
                                         Map<String, String> slots) {
        Optional<WAgentMissionDO> mission = missionDao.findWithPlaybookRef(tenantId).stream()
                .filter(m -> ref.equals(m.getPlaybookGitRef()))
                .findFirst();
        if (mission.isEmpty()) {
            throw new TemplateNotFoundException("模板不存在或引用不可见: " + ref);
        }
        String version = mission.get().getPlaybookVersion();

        PlaybookContentSource source = contentSourceProvider.getIfAvailable();
        if (source == null) {
            throw new TemplateValidationException(
                    "Playbook 内容源未装配（Git 归档通道未接），无法实例化模板 " + ref);
        }
        String template = source.loadActiveTemplate(tenantId, ref, version).orElse(null);
        if (template == null) {
            throw new TemplateValidationException("Playbook 正文在归档中不可得: " + ref + "@" + version);
        }

        String filled = playbookRenderer.fill(template, slots == null ? Map.of() : slots);
        WAgentPlan.Plan plan;
        try {
            plan = WAgentPlan.PlanCodec.fromJson(filled);
        } catch (RuntimeException e) {
            throw new TemplateValidationException("Playbook 正文不是合法计划: " + e.getMessage());
        }

        PlaybookToGraphConverter.ConvertedGraph graph;
        try {
            graph = converter.convert(plan);
        } catch (IllegalArgumentException e) {
            throw new TemplateValidationException(e.getMessage());
        }
        if (graph.nodes().isEmpty()) {
            throw new TemplateValidationException("转换结果为空（无可映射步骤），拒绝创建空编排");
        }

        String id = UUID.randomUUID().toString();
        AipPipelineDO row = new AipPipelineDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setName(name == null || name.isBlank() ? mission.get().getName() : name.trim());
        row.setDescription("由 Wagent Playbook 模板生成: " + ref + "@" + version);
        row.setDefinitionJson(writeDefinition(graph));
        row.setStatus("draft");
        row.setTemplateRef(ref);
        row.setVersionNo("1");
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        pipelineDao.insert(row);
        log.info("AIP Pipeline 由模板实例化: id={} ref={} mapped={} unmapped={}",
                id, ref, graph.nodes().size(), graph.unmapped().size());
        return new InstantiateResult(id, graph.nodes().size(), graph.unmapped());
    }

    /** definition_json 形状与手工保存一致：{ blocks: [], graph: { nodes, edges } }。 */
    private static String writeDefinition(PlaybookToGraphConverter.ConvertedGraph graph) {
        Map<String, Object> graphNode = new LinkedHashMap<>();
        graphNode.put("nodes", graph.nodes());
        graphNode.put("edges", graph.edges());
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("blocks", List.of());
        definition.put("graph", graphNode);
        try {
            return MAPPER.writeValueAsString(definition);
        } catch (Exception e) {
            throw new TemplateException(500, "模板定义序列化失败");
        }
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null ? a : b;
    }
}
