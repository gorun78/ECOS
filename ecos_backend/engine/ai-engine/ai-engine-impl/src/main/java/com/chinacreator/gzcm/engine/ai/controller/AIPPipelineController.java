package com.chinacreator.gzcm.engine.ai.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.dao.AipPipelineDao;
import com.chinacreator.gzcm.engine.ai.dataobject.AipPipelineDO;
import com.chinacreator.gzcm.engine.ai.dto.AipDeleteResultVO;
import com.chinacreator.gzcm.engine.ai.dto.AipPipelineExecResultVO;
import com.chinacreator.gzcm.engine.ai.dto.AipPipelineSummaryVO;
import com.chinacreator.gzcm.engine.ai.dto.AipPipelineUpsertRequest;
import com.chinacreator.gzcm.engine.ai.dto.AipPipelineVO;
import com.chinacreator.gzcm.engine.ai.dto.AipTemplateInstantiateRequest;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.template.AipPipelineTemplateService;
import com.chinacreator.gzcm.engine.ai.template.DefinitionJsonCodec;

/**
 * AIP Pipeline CRUD REST API — AI 工作台逻辑编排定义的创建、编辑、执行管理。
 *
 * <h3>端点（路径与响应键序自 DI-2 #147EC 起冻结，本批只换持久化介质）：</h3>
 * <ul>
 *   <li>GET    /api/v1/aip/pipelines          — 列出所有 Pipeline</li>
 *   <li>POST   /api/v1/aip/pipelines          — 创建 Pipeline</li>
 *   <li>GET    /api/v1/aip/pipelines/{id}     — Pipeline 详情</li>
 *   <li>PUT    /api/v1/aip/pipelines/{id}     — 更新 Pipeline</li>
 *   <li>DELETE /api/v1/aip/pipelines/{id}     — 删除 Pipeline</li>
 *   <li>POST   /api/v1/aip/pipelines/{id}/execute — 执行 Pipeline</li>
 * </ul>
 *
 * <p><b>持久化（V329）</b>：经 {@link AipPipelineDao} 落 {@code ecos_ai.ecos_aip_pipeline}
 * （原 {@code ConcurrentHashMap} 内存 store 刷新即丢 = P0 缺陷修复）；{@code definition_json}
 * 保存画布 nodes/edges/steps 完整定义。</p>
 *
 * <p>身份：tenant/operator 取自 {@link ToolSubjectResolver}（token 上下文单源，禁请求体自报；
 * 与 wagent 域 {@code WAgentIdentityResolver} 同通道）。租户缺失时以 {@code default} 兜底，
 * 保持既有单租户前端行为不变。</p>
 */
@RestController
@RequestMapping("/api/v1/aip/pipelines")
public class AIPPipelineController {

    private static final Logger log = LoggerFactory.getLogger(AIPPipelineController.class);

    private static final String DEFAULT_TENANT = "default";

    private final AipPipelineDao pipelineDao;
    private final ToolSubjectResolver subjectResolver;
    private final AipPipelineTemplateService templateService;

    public AIPPipelineController(ObjectProvider<AipPipelineDao> pipelineDaoProvider,
                                 ObjectProvider<ToolSubjectResolver> subjectResolverProvider,
                                 ObjectProvider<AipPipelineTemplateService> templateServiceProvider) {
        this.pipelineDao = pipelineDaoProvider.getIfAvailable();
        this.subjectResolver = subjectResolverProvider.getIfAvailable();
        this.templateService = templateServiceProvider.getIfAvailable();
    }

    // ═══════════════ 列表 ═══════════════

    @GetMapping
    public ApiResponse<List<AipPipelineSummaryVO>> listPipelines() {
        List<AipPipelineSummaryVO> result = pipelineDao.findAll(currentTenant()).stream()
                .map(AIPPipelineController::toSummary)
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════

    @GetMapping("/{id}")
    public ApiResponse<AipPipelineVO> getPipeline(@PathVariable String id) {
        AipPipelineDO row = pipelineDao.findById(currentTenant(), id).orElse(null);
        if (row == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");
        return ApiResponse.success(toVO(row));
    }

    // ═══════════════ 创建 ═══════════════

    @PostMapping
    public ApiResponse<AipPipelineVO> createPipeline(@RequestBody AipPipelineUpsertRequest body) {
        String name = body.name() == null ? "" : body.name().trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }
        String id = UUID.randomUUID().toString();
        String description = body.description() == null ? "" : body.description();
        Object steps = body.steps() == null ? List.of() : body.steps();

        AipPipelineDO row = new AipPipelineDO();
        row.setId(id);
        row.setTenantId(currentTenant());
        row.setName(name);
        row.setDescription(description);
        row.setDefinitionJson(DefinitionJsonCodec.build(body.steps(), body.nodes(), body.edges()));
        row.setStatus("draft");
        row.setTemplateRef(body.templateRef());
        row.setVersionNo("1");
        row.setCreateBy(currentOperator());
        row.setUpdateBy(currentOperator());
        pipelineDao.insert(row);
        log.info("AIP Pipeline created: {} [{}]", id, name);
        AipPipelineDO saved = pipelineDao.findById(currentTenant(), id).orElse(row);
        return ApiResponse.success(toVO(saved));
    }

    // ═══════════════ 更新 ═══════════════

    @PutMapping("/{id}")
    public ApiResponse<AipPipelineVO> updatePipeline(
            @PathVariable String id,
            @RequestBody AipPipelineUpsertRequest body) {
        AipPipelineDO existing = pipelineDao.findById(currentTenant(), id).orElse(null);
        if (existing == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");
        if (body.name() != null) existing.setName(body.name());
        if (body.description() != null) existing.setDescription(body.description());
        if (body.status() != null) existing.setStatus(body.status());
        if (body.steps() != null || body.nodes() != null || body.edges() != null) {
            existing.setDefinitionJson(DefinitionJsonCodec.merge(
                    existing.getDefinitionJson(), body.steps(), body.nodes(), body.edges()));
        }
        existing.setUpdateBy(currentOperator());
        pipelineDao.update(existing);
        log.info("AIP Pipeline updated: {}", id);
        return ApiResponse.success(toVO(pipelineDao.findById(currentTenant(), id).orElse(existing)));
    }

    // ═══════════════ 删除 ═══════════════

    @DeleteMapping("/{id}")
    public ApiResponse<AipDeleteResultVO> deletePipeline(@PathVariable String id) {
        int affected = pipelineDao.softDelete(currentTenant(), id, currentOperator());
        if (affected > 0) {
            log.info("AIP Pipeline deleted: {}", id);
            return ApiResponse.success(new AipDeleteResultVO(true, id));
        }
        return ApiResponse.notFound("Pipeline " + id + " 不存在");
    }

    // ═══════════════ 执行 ═══════════════

    @PostMapping("/{id}/execute")
    public ApiResponse<AipPipelineExecResultVO> executePipeline(@PathVariable String id) {
        AipPipelineDO existing = pipelineDao.findById(currentTenant(), id).orElse(null);
        if (existing == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");
        String executedAt = Instant.now().toString();
        pipelineDao.markExecuted(currentTenant(), id, "completed", currentOperator());
        log.info("AIP Pipeline executed: {}", id);
        return ApiResponse.success(new AipPipelineExecResultVO(
                id, "executed", "Pipeline executed successfully", executedAt));
    }

    // ═══════════════ 模板（Wagent Playbook → 逻辑流骨架，批 2） ═══════════════

    /**
     * 模板目录 — GET /api/v1/aip/pipelines/template-sources。
     *
     * <p>段名刻意不用单数 {@code /templates} 之外的歧义形态：{@code /{id}} 通配会先吃到
     * 字面段（实测 404「Pipeline templates 不存在」），故用复合段避开与 {@code /{id}} 竞争。</p>
     * 空目录是合法结果（本租户无 Playbook 引用 / 内容源未装配），不报错。
     */
    @GetMapping("/template-sources")
    public ApiResponse<List<AipPipelineTemplateService.TemplateItem>> listTemplates() {
        if (templateService == null) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(templateService.listTemplates(currentTenant()));
    }

    /**
     * 模板实例化 — POST /api/v1/aip/pipelines/template-sources/instantiate。
     *
     * <p><b>ref 走请求体而非路径段</b>：模板引用天然含 {@code /}（git ref
     * {@code repo/pb1}、内置 {@code builtin/intent/FORECAST}），编码成 {@code %2F} 会被
     * Tomcat 以 400 拒绝（实测：{@code HTTP Status 400 – Bad Request}）。路径保持固定字面段，
     * 同时避开 {@code /{id}} 通配竞争。</p>
     */
    @PostMapping("/template-sources/instantiate")
    public ApiResponse<AipTemplateInstantiateRequest.Result> instantiateTemplate(
            @RequestBody(required = false) AipTemplateInstantiateRequest body) {
        if (templateService == null) {
            return ApiResponse.error(503, "AIP-T01: 模板服务未装配");
        }
        if (body == null || body.ref() == null || body.ref().isBlank()) {
            return ApiResponse.error(400, "AIP-T02: 缺少模板引用 ref");
        }
        String ref = body.ref().trim();
        String name = body.name() == null ? null : body.name().trim();
        // 失败一律上浮（AiErrorAdvice 渲染），控制器禁自 catch + 禁回显异常原文（F06-15 / X-49）
        AipPipelineTemplateService.InstantiateResult result = templateService.instantiate(
                currentTenant(), currentOperator(), ref, name,
                body.slots() == null ? Map.of() : body.slots());
        return ApiResponse.success(new AipTemplateInstantiateRequest.Result(
                result.pipelineId(),
                new AipTemplateInstantiateRequest.Report(result.mapped(), result.unmapped())));
    }

    // ═══════════════ 转换 ═══════════════

    private static AipPipelineSummaryVO toSummary(AipPipelineDO row) {
        return new AipPipelineSummaryVO(row.getId(), row.getName(), row.getDescription(), row.getStatus(),
                toInstantText(row.getCreateTime()), toInstantText(row.getUpdateTime()));
    }

    private static AipPipelineVO toVO(AipPipelineDO row) {
        Object steps = DefinitionJsonCodec.stepsOf(row.getDefinitionJson());
        String lastExecutedAt = row.getLastExecutedAt() == null
                ? null : toInstantText(row.getLastExecutedAt());
        return new AipPipelineVO(row.getId(), row.getName(), row.getDescription(), steps, row.getStatus(),
                toInstantText(row.getCreateTime()), toInstantText(row.getUpdateTime()), lastExecutedAt);
    }

    private static String toInstantText(LocalDateTime time) {
        return time == null ? Instant.now().toString() : time.toInstant(ZoneOffset.UTC).toString();
    }

    private String currentTenant() {
        String tenantId = subjectTenant();
        return tenantId == null ? DEFAULT_TENANT : tenantId;
    }

    private String currentOperator() {
        String userId = subjectUserId();
        return userId == null ? "system" : userId;
    }

    private String subjectTenant() {
        if (subjectResolver == null) return null;
        var subject = subjectResolver.resolve();
        if (subject == null || subject.tenantId() == null || subject.tenantId().isBlank()) return null;
        return subject.tenantId().trim();
    }

    private String subjectUserId() {
        if (subjectResolver == null) return null;
        var subject = subjectResolver.resolve();
        if (subject == null || subject.userId() == null || subject.userId().isBlank()) return null;
        return subject.userId().trim();
    }
}
