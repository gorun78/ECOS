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
 * <p><b>三档来源</b>（顺序即目录顺序）：
 * <ol>
 *   <li>{@code BUILTIN} —— {@link BuiltinPipelineTemplateCatalog} 内置典型模板：八个
 *       GoalIntent 各一枚确定性骨架（{@code human_gate → tool×n → aggregate}），不依赖
 *       Git 内容源与 DB 引用，永远可用；</li>
 *   <li>{@code PLAYBOOK} —— 本租户有在用 Playbook 引用的 mission 行（按 ref+version 去重）；</li>
 *   <li>{@code CANDIDATE} —— 已发布 candidate（其 payload 声明 playbook 引用者，批 3）。</li>
 * </ol>
 *
 * <p>{@link #instantiate} 双路径：内置 ref 直接取骨架步骤集（无内容源依赖）；
 * 其余 ref 读 Playbook 正文（{@link PlaybookContentSource}，Git 归档单源）→
 * {@link PlaybookRenderer} 确定性填槽 → {@link WAgentPlan.PlanCodec} 解析 →
 * 共同经 {@link PlaybookToGraphConverter} 转画布 → 落 {@code ecos_aip_pipeline}。</p>
 *
 * <p><b>内容源缺席不伪装</b>：Playbook/candidate 来源的 ref 在 {@code PlaybookContentSource}
 * 未装配（#131 前常态）时返回 422 并说明「Playbook 内容源未装配」——禁伪造模板正文，
 * 也禁把缺席读成「模板不存在」。内置来源不受此影响。</p>
 */
@Service
public class AipPipelineTemplateService {

    private static final Logger log = LoggerFactory.getLogger(AipPipelineTemplateService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 来源标记（目录项 source 字段的冻结三值；禁散落字面量）。 */
    public static final String SOURCE_BUILTIN = "BUILTIN";
    public static final String SOURCE_PLAYBOOK = "PLAYBOOK";
    public static final String SOURCE_CANDIDATE = "CANDIDATE";

    private final WAgentMissionDao missionDao;
    private final AipPipelineDao pipelineDao;
    private final PlaybookRenderer playbookRenderer;
    private final PlaybookToGraphConverter converter;
    private final ObjectProvider<PlaybookContentSource> contentSourceProvider;
    private final BuiltinPipelineTemplateCatalog builtinCatalog;

    public AipPipelineTemplateService(WAgentMissionDao missionDao,
                                      AipPipelineDao pipelineDao,
                                      PlaybookRenderer playbookRenderer,
                                      PlaybookToGraphConverter converter,
                                      ObjectProvider<PlaybookContentSource> contentSourceProvider,
                                      BuiltinPipelineTemplateCatalog builtinCatalog) {
        this.missionDao = missionDao;
        this.pipelineDao = pipelineDao;
        this.playbookRenderer = playbookRenderer;
        this.converter = converter;
        this.contentSourceProvider = contentSourceProvider;
        this.builtinCatalog = builtinCatalog;
    }

    /** 模板目录项（ref 即 instantiate 的路径参数）。 */
    public record TemplateItem(String ref, String name, String version, String summary,
                               String domain, String source) {
    }

    /** 实例化回执。 */
    public record InstantiateResult(String pipelineId, int mapped, List<String> unmapped) {
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

    /** 模板目录：内置典型模板在前，随后 mission 的 Playbook 引用去重（ref+version 首见）。 */
    public List<TemplateItem> listTemplates(String tenantId) {
        Set<String> seen = new LinkedHashSet<>();
        List<TemplateItem> items = new ArrayList<>();
        for (TemplateItem builtin : builtinCatalog.list()) {
            if (seen.add(builtin.ref() + "@" + builtin.version())) {
                items.add(builtin);
            }
        }
        for (WAgentMissionDO mission : missionDao.findWithPlaybookRef(tenantId)) {
            String ref = mission.getPlaybookGitRef();
            String version = mission.getPlaybookVersion();
            String key = ref + "@" + version;
            if (!seen.add(key)) continue;
            items.add(new TemplateItem(ref, mission.getName(), version,
                    "mission: " + mission.getId(), mission.getDomain(), SOURCE_PLAYBOOK));
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
                        version, "candidate: " + str(row.get("id")), null, SOURCE_CANDIDATE));
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
     * 实例化：模板 → 画布骨架 → 落库。
     *
     * @throws TemplateNotFoundException  引用不存在或不可见
     * @throws TemplateValidationException 内容源缺席 / 正文非法 / 计划结构非法 / 转换结果为空
     */
    public InstantiateResult instantiate(String tenantId, String operator, String ref, String name,
                                         Map<String, String> slots) {
        String requestedName = name == null ? null : name.trim();
        if (builtinCatalog.isBuiltinRef(ref)) {
            return instantiateBuiltin(tenantId, operator, ref, requestedName);
        }
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
        String finalName = requestedName == null || requestedName.isBlank()
                ? mission.get().getName() : requestedName;
        return persist(tenantId, operator, ref, finalName, version, convert(plan));
    }

    /**
     * 内置模板实例化：骨架步骤集直接来自 {@link BuiltinPipelineTemplateCatalog}（确定性，
     * 无 Git 内容源依赖，也不消费槽位——骨架里没有 {@code {{slot}}} 占位）。
     *
     * <p>{@code PlanSource} 是冻结两值枚举（PLAYBOOK/EXPLORED），无 BUILTIN 值；此处传
     * PLAYBOOK 仅作 {@link PlaybookToGraphConverter} 的承载，转换器只读 {@code steps()}，
     * 该值不进画布也不落库，来源真相由 {@code template_ref} 的 {@code builtin/} 前缀承载。</p>
     */
    private InstantiateResult instantiateBuiltin(String tenantId, String operator, String ref, String name) {
        com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent intent = builtinCatalog.intentOf(ref);
        if (intent == null) {
            throw new TemplateNotFoundException("内置模板不存在: " + ref);
        }
        WAgentPlan.Plan plan = new WAgentPlan.Plan(
                com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource.PLAYBOOK,
                null, null, builtinCatalog.stepsOf(intent));
        String finalName = name == null || name.isBlank() ? builtinCatalog.displayNameOf(intent) : name;
        return persist(tenantId, operator, ref, finalName, "builtin", convert(plan));
    }

    private PlaybookToGraphConverter.ConvertedGraph convert(WAgentPlan.Plan plan) {
        PlaybookToGraphConverter.ConvertedGraph graph;
        try {
            graph = converter.convert(plan);
        } catch (IllegalArgumentException e) {
            throw new TemplateValidationException(e.getMessage());
        }
        if (graph.nodes().isEmpty()) {
            throw new TemplateValidationException("转换结果为空（无可映射步骤），拒绝创建空编排");
        }
        return graph;
    }

    private InstantiateResult persist(String tenantId, String operator, String ref, String name,
                                      String versionLabel, PlaybookToGraphConverter.ConvertedGraph graph) {
        String id = UUID.randomUUID().toString();
        AipPipelineDO row = new AipPipelineDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setName(name);
        row.setDescription("由模板生成: " + ref + "@" + versionLabel);
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

    /**
     * definition_json 形状与手工保存路径逐键一致：{@code { steps: { blocks: [], graph: { nodes, edges } } }}。
     *
     * <p>注意外层必须有 {@code steps} 键——端点出参经 {@link DefinitionJsonCodec#stepsOf} 只读该键，
     * 少了它实例化出来的编排在画布上是空白（实测踩过：落库成功但画布无节点）。</p>
     */
    private static String writeDefinition(PlaybookToGraphConverter.ConvertedGraph graph) {
        Map<String, Object> graphNode = new LinkedHashMap<>();
        graphNode.put("nodes", graph.nodes());
        graphNode.put("edges", graph.edges());
        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("blocks", List.of());
        steps.put("graph", graphNode);
        return DefinitionJsonCodec.build(steps, null, null);
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
