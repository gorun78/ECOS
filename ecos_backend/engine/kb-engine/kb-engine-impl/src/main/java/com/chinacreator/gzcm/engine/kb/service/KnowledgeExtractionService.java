package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedEntity;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRelation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 知识抽取服务 — 文档上传→解析→LLM抽取→审核→入库全链路。
 *
 * <p>状态机: UPLOADED → PARSING → EXTRACTING → PENDING_REVIEW → APPROVED / REJECTED</p>
 *
 * <p>Wave-2C 增量 (PMO-24 审批闭环):
 * <ul>
 *   <li>approve: 规则去重 (name+domain 唯一) + 实体写 KGWriterService + 结构化 ApprovalOutcome</li>
 *   <li>reject: 增加 rejectedReason 字段 (05 文档 §六)</li>
 *   <li>系统 Prompt 升级: 3 类抽取 (entity/link/rule) 对齐 05 文档 §四</li>
 * </ul>
 *
 * <p>kb 不直接调 LLM，通过 ai-engine Agent Loop API 执行抽取。</p>
 *
 * @author ECOS KB Engine Team
 * @since 2026-08-08, 2026-09-02 (Wave-2C 审批闭环)
 */
@Service
public class KnowledgeExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/ecos-extractions";
    private static final String AGENT_LOOP_URL = "http://localhost:8080/api/v1/agent-loop/chat";
    private static final int PARSE_TIMEOUT_SEC = 120;
    private static final int LLM_TIMEOUT_SEC = 60;
    private static final int MAX_RETRY = 1;

    /** 05 文档 §四: 3 类抽取 systemPrompt (entity/link/rule) */
    private static final String EXTRACTION_SYSTEM_PROMPT =
        "你是企业知识抽取专家。严格按 JSON 格式输出：\n" +
        "{\n" +
        "  \"entities\": [{\"name\": \"string\", \"type\": \"string\", \"evidence_text\": \"string\", \"confidence\": 0.0-1.0}],\n" +
        "  \"links\": [{\"from_entity\": \"string\", \"target_entity\": \"string\", " +
        "\"type\": \"CAUSES|DEPENDS|PART_OF|OWNED_BY|MAPPED_TO\", \"evidence_text\": \"string\", \"direction\": \"FORWARD|BIDIR\"}],\n" +
        "  \"rules\": [{\"name\": \"string\", \"description\": \"string\", \"condition\": \"string\", \"action\": \"string\", \"severity\": \"WARN|ERROR\"}]\n" +
        "}";

    private final JdbcTemplate jdbc;
    private final RestTemplate restTemplate;
    private final ComplianceRuleMapper ruleMapper;
    private final KGWriterService kgWriter;
    private final DocumentParserService documentParserService;
    private final EntityLinkerService entityLinkerService;

    public KnowledgeExtractionService(JdbcTemplate jdbc,
                                      ComplianceRuleMapper ruleMapper,
                                      KGWriterService kgWriter,
                                      DocumentParserService documentParserService,
                                      EntityLinkerService entityLinkerService) {
        this.jdbc = jdbc;
        this.ruleMapper = ruleMapper;
        this.kgWriter = kgWriter;
        this.documentParserService = documentParserService;
        this.entityLinkerService = entityLinkerService;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        ensureColumns();
        try { Files.createDirectories(Paths.get(UPLOAD_DIR)); } catch (IOException ignored) {}
        log.info("KnowledgeExtractionService initialized");
    }

    // ── DDL 补列 (只增不删) ──────────────────────────

    private void ensureColumns() {
        // extracted_links_json 列 (05 文档 §四: 3 类抽取)
        jdbc.execute("ALTER TABLE extraction_drafts ADD COLUMN IF NOT EXISTS extracted_links_json TEXT");
        // rejected_reason 列 (05 文档 §六: 拒绝原因)
        jdbc.execute("ALTER TABLE extraction_drafts ADD COLUMN IF NOT EXISTS rejected_reason TEXT");
    }

    // ── 上传 ─────────────────────────────────────────

    public Map<String, Object> upload(MultipartFile file) throws IOException {
        String id = UUID.randomUUID().toString();
        Path target = Paths.get(UPLOAD_DIR, id + "_" + file.getOriginalFilename());
        file.transferTo(target.toFile());

        jdbc.update(
            "INSERT INTO extraction_drafts (id, file_name, file_path, status) VALUES (?, ?, ?, 'UPLOADED')",
            id, file.getOriginalFilename(), target.toString()
        );

        // 异步启动解析
        Executors.newSingleThreadExecutor().submit(() -> parseAndExtract(id, target));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extractionId", id);
        result.put("status", "UPLOADED");
        result.put("fileName", file.getOriginalFilename());
        return result;
    }

    // ── 解析 + 抽取 ──────────────────────────────────

    private void parseAndExtract(String id, Path filePath) {
        // Step 1: 解析文本 (PMO-34: Tika + Wave-2C MinerU 路由)
        updateStatus(id, "PARSING");
        String text;
        try {
            DocumentParserService.ParseResult parseResult = documentParserService.parse(filePath);
            text = parseResult.getText();
            jdbc.update(
                "UPDATE extraction_drafts SET parsed_text = ?, status = 'EXTRACTING', " +
                "file_type = ?, page_count = ?, char_count = ? WHERE id = ?",
                text, parseResult.getFileType(), parseResult.getPageCount(),
                parseResult.getCharCount(), id
            );
        } catch (Exception e) {
            handleError(id, "解析失败: " + e.getMessage(), "PARSING");
            return;
        }

        // Step 2: LLM抽取 (带重试)
        int retry = 0;
        while (retry <= MAX_RETRY) {
            try {
                Map<String, Object> extracted = callAiExtraction(text);
                String entitiesJson = mapper.writeValueAsString(
                        extracted.getOrDefault("entities", Collections.emptyList()));
                // Wave-2C: 3 类抽取 — links
                String linksJson = mapper.writeValueAsString(
                        extracted.getOrDefault("links",
                            extracted.getOrDefault("relations", Collections.emptyList())));
                String rulesJson = mapper.writeValueAsString(
                        extracted.getOrDefault("rules", Collections.emptyList()));

                jdbc.update(
                    "UPDATE extraction_drafts SET extracted_entities_json = ?, " +
                    "extracted_links_json = ?, extracted_rules_json = ?, status = 'PENDING_REVIEW' WHERE id = ?",
                    entitiesJson, linksJson, rulesJson, id
                );
                // 安全计数（非 List 类型退化为 0）
                int entCount = extracted.get("entities") instanceof java.util.List ?
                    ((java.util.List<?>) extracted.get("entities")).size() : 0;
                int linkCount = (extracted.get("links") instanceof java.util.List) ?
                    ((java.util.List<?>) extracted.get("links")).size() : 0;
                int ruleCount = (extracted.get("rules") instanceof java.util.List) ?
                    ((java.util.List<?>) extracted.get("rules")).size() : 0;
                log.info("抽取完成: id={}, entities={}, links={}, rules={}", id, entCount, linkCount, ruleCount);
                return;
            } catch (Exception e) {
                retry++;
                log.warn("LLM抽取失败 (retry {}/{}): id={}, {}", retry, MAX_RETRY, id, e.getMessage());
                if (retry > MAX_RETRY) {
                    handleError(id, "LLM抽取失败(已重试" + MAX_RETRY + "次): " + e.getMessage(), "EXTRACTING");
                } else {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    /**
     * 调用 ai-engine Agent Loop 执行知识抽取。
     * kb不直接调LLM — 走ai-engine API。
     * Wave-2C: systemPrompt 升级为 3 类 (entity/link/rule)。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callAiExtraction(String text) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "请从以下文档内容中抽取出：\n"
                + "1. 实体(entities)：关键业务实体及其类型+置信度\n"
                + "2. 关系(links)：实体之间的关系(CAUSES/DEPENDS/PART_OF/OWNED_BY/MAPPED_TO)\n"
                + "3. 规则(rules)：隐含的业务规则或合规要求(含condition/action/severity)\n\n"
                + "文档内容:\n" + (text.length() > 8000 ? text.substring(0, 8000) + "..." : text));
        payload.put("systemPrompt", EXTRACTION_SYSTEM_PROMPT);
        payload.put("temperature", 0.1);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() ->
            restTemplate.postForObject(AGENT_LOOP_URL, request, String.class));

        try {
            String response = future.get(LLM_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (response == null || response.isEmpty()) return Collections.emptyMap();

            Map<String, Object> apiResp = mapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            Boolean topSuccess = (Boolean) apiResp.get("success");
            if (topSuccess != null && !topSuccess) {
                String msg = (String) apiResp.getOrDefault("message", "Agent调用失败");
                throw new RuntimeException("Agent调用失败: " + msg);
            }
            Object data = apiResp.get("data");
            if (data instanceof Map) {
                Map<?, ?> dataMap = (Map<?, ?>) data;
                Boolean dataSuccess = (Boolean) dataMap.get("success");
                if (dataSuccess != null && !dataSuccess) {
                    Object errObj = dataMap.get("errorMsg");
                    throw new RuntimeException("Agent推理失败: " + (errObj != null ? errObj : "未知"));
                }
                Object content = dataMap.get("content");
                if (content != null) {
                    try {
                        return mapper.readValue(content.toString(), new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        String jsonStr = content.toString();
                        int start = jsonStr.indexOf('{');
                        int end = jsonStr.lastIndexOf('}');
                        if (start >= 0 && end > start) {
                            return mapper.readValue(jsonStr.substring(start, end + 1),
                                    new TypeReference<Map<String, Object>>() {});
                        }
                    }
                }
            }
            return Collections.emptyMap();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("LLM抽取超时(" + LLM_TIMEOUT_SEC + "s)");
        } finally {
            executor.shutdownNow();
        }
    }

    // ── 审核闭环 (Wave-2C PMO-24 补全) ───────────────

    /**
     * 审核通过 — Wave-2C 完整闭环 (05 文档 §六)。
     *
     * <p>流程:
     * <ol>
     *   <li>校验 status == PENDING_REVIEW</li>
     *   <li>规则去重: compliance_rules 上 name+domain 唯一</li>
     *   <li>实体: KGWriterService.writeBatch 写入 Neo4j/PG</li>
     *   <li>规则: ruleMapper.insert (已有)</li>
     *   <li>审计日志 (异步)</li>
     * </ol>
     *
     * @param id 抽取任务 ID
     * @return ApprovalOutcome { status, counts: {rules, entities, links}, rejectedReasons }
     */
    public Map<String, Object> approve(String id) {
        // 1. 校验 status
        Map<String, Object> draft = jdbc.queryForMap(
            "SELECT * FROM extraction_drafts WHERE id = ?", id);
        String currentStatus = (String) draft.get("status");
        if (!"PENDING_REVIEW".equals(currentStatus)) {
            throw new IllegalStateException("Cannot approve: status is " + currentStatus + ", expected PENDING_REVIEW");
        }

        List<String> rejectedReasons = new ArrayList<>();
        int rulesWritten = 0;
        int entitiesWritten = 0;
        int linksWritten = 0;

        // 2. 规则去重 + 入库
        try {
            String rulesJson = (String) draft.get("extracted_rules_json");
            if (rulesJson != null && !rulesJson.isEmpty()) {
                List<Map<String, Object>> rules = mapper.readValue(rulesJson,
                        new TypeReference<List<Map<String, Object>>>() {});
                // 去重: 查 compliance_rules 已有 name+domain
                List<ComplianceRule> existingRules = ruleMapper.findAll();
                Set<String> existingKeys = new HashSet<>();
                for (ComplianceRule er : existingRules) {
                    existingKeys.add(er.getName() + "::" + er.getDomain());
                }

                for (Map<String, Object> rule : rules) {
                    String ruleName = String.valueOf(rule.getOrDefault("name", ""));
                    String ruleDomain = String.valueOf(rule.getOrDefault("domain", "extracted"));
                    String dedupKey = ruleName + "::" + ruleDomain;

                    if (existingKeys.contains(dedupKey)) {
                        rejectedReasons.add("duplicate_rule:" + ruleName + " in domain " + ruleDomain);
                        log.warn("规则重复跳过: {} / {}", ruleName, ruleDomain);
                        continue;
                    }

                    ComplianceRule cr = new ComplianceRule();
                    cr.setId(UUID.randomUUID().toString());
                    cr.setName(ruleName);
                    cr.setDomain(ruleDomain);
                    cr.setCondition(String.valueOf(rule.getOrDefault("condition", "")));
                    cr.setAction(String.valueOf(rule.getOrDefault("action", "")));
                    cr.setDescription(String.valueOf(rule.getOrDefault("description", "")));
                    cr.setStatus("ACTIVE");
                    cr.setEnabled(true);
                    cr.setPriority(5);
                    cr.setRuleType(String.valueOf(rule.getOrDefault("severity", "WARN")));
                    cr.setCreatedAt(System.currentTimeMillis());
                    cr.setUpdatedAt(System.currentTimeMillis());
                    ruleMapper.insert(cr);
                    rulesWritten++;
                }
            }
        } catch (Exception e) {
            log.error("写compliance_rules失败: {}", e.getMessage(), e);
            rejectedReasons.add("rule_write_error:" + e.getMessage());
        }

        // 3. 实体 → KGWriterService (05 文档 §六: approve 必须写 Neo4j)
        try {
            String entitiesJson = (String) draft.get("extracted_entities_json");
            String linksJson = (String) draft.get("extracted_links_json");
            List<ExtractedEntity> entities = new ArrayList<>();
            List<ExtractedRelation> relations = new ArrayList<>();
            List<String> entityNames = new ArrayList<>();

            if (entitiesJson != null && !entitiesJson.isEmpty()) {
                List<Map<String, Object>> entityList = mapper.readValue(entitiesJson,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> e : entityList) {
                    ExtractedEntity ee = new ExtractedEntity();
                    ee.setName(String.valueOf(e.getOrDefault("name", "")));
                    ee.setType(String.valueOf(e.getOrDefault("type", "UNKNOWN")));
                    Object conf = e.get("confidence");
                    ee.setConfidence(conf instanceof Number ? ((Number) conf).doubleValue() : 0.8);
                    entities.add(ee);
                    entityNames.add(ee.getName());
                }
            }

            if (linksJson != null && !linksJson.isEmpty()) {
                List<Map<String, Object>> linkList = mapper.readValue(linksJson,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> l : linkList) {
                    ExtractedRelation er = new ExtractedRelation();
                    er.setSourceEntity(String.valueOf(l.getOrDefault("from_entity", l.getOrDefault("from", ""))));
                    er.setTargetEntity(String.valueOf(l.getOrDefault("target_entity", l.getOrDefault("to", ""))));
                    er.setRelationType(String.valueOf(l.getOrDefault("type", "RELATED_TO")));
                    Object conf = l.get("confidence");
                    er.setConfidence(conf instanceof Number ? ((Number) conf).doubleValue() : 0.8);
                    relations.add(er);
                }
            }

            if (!entities.isEmpty() || !relations.isEmpty()) {
                KGWriterService.BatchWriteResult writeResult =
                    kgWriter.writeBatch(entities, relations);
                entitiesWritten = writeResult.totalEntities();
                linksWritten = writeResult.totalRelations();

                // 实体链接到本体 (05 文档 §五)
                try {
                    List<Map<String, String>> entityMaps = entities.stream()
                        .map(ee -> {
                            Map<String, String> m = new LinkedHashMap<>();
                            m.put("name", ee.getName());
                            m.put("type", ee.getType() != null ? ee.getType() : "unknown");
                            return m;
                        })
                        .collect(Collectors.toList());
                    entityLinkerService.linkEntities(entityMaps);
                } catch (Exception linkEx) {
                    log.warn("实体链接触发失败(不影响approve): {}", linkEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("写实体/关系失败: {}", e.getMessage(), e);
            rejectedReasons.add("entity_write_error:" + e.getMessage());
        }

        // 4. 更新状态
        jdbc.update("UPDATE extraction_drafts SET status = 'APPROVED', updated_at = NOW() WHERE id = ?", id);

        // 5. 审计日志 (异步，不阻塞主流程)
        auditAsync(id, "APPROVED", rulesWritten, entitiesWritten, linksWritten);

        // 构建 ApprovalOutcome
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("rules", rulesWritten);
        counts.put("entities", entitiesWritten);
        counts.put("links", linksWritten);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "APPROVED");
        result.put("counts", counts);
        result.put("rejectedReasons", rejectedReasons);

        log.info("approve 完成: id={}, rules={}, entities={}, links={}, rejected={}",
                id, rulesWritten, entitiesWritten, linksWritten, rejectedReasons.size());
        return result;
    }

    /**
     * 审核驳回 — 增加 rejectedReason (05 文档 §六)。
     *
     * @param id     抽取任务 ID
     * @param reason 驳回原因 (非空)
     * @return { id, status, rejectedReason }
     */
    public Map<String, Object> reject(String id, String reason) {
        String currentStatus;
        try {
            Map<String, Object> draft = jdbc.queryForMap(
                "SELECT status FROM extraction_drafts WHERE id = ?", id);
            currentStatus = (String) draft.get("status");
        } catch (Exception e) {
            throw new IllegalArgumentException("Draft not found: " + id);
        }
        if (!"PENDING_REVIEW".equals(currentStatus) && !"EXTRACTING".equals(currentStatus)) {
            throw new IllegalStateException("Cannot reject: status is " + currentStatus);
        }

        jdbc.update("UPDATE extraction_drafts SET status = 'REJECTED', rejected_reason = ?, updated_at = NOW() WHERE id = ?",
                reason, id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "REJECTED");
        result.put("rejectedReason", reason);
        return result;
    }

    /** 兼容旧签名 (reject without reason) */
    public Map<String, Object> reject(String id) {
        return reject(id, "no reason provided");
    }

    // ── 查询 ─────────────────────────────────────────

    public List<Map<String, Object>> listTasks(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbc.queryForList(
            "SELECT id, file_name, status, created_at FROM extraction_drafts ORDER BY created_at DESC LIMIT ? OFFSET ?",
            pageSize, offset);
    }

    public Map<String, Object> getTask(String id) {
        return jdbc.queryForMap("SELECT * FROM extraction_drafts WHERE id = ?", id);
    }

    // ── 工具方法 ─────────────────────────────────────

    private void updateStatus(String id, String status) {
        jdbc.update("UPDATE extraction_drafts SET status = ?, updated_at = NOW() WHERE id = ?", status, id);
    }

    private void handleError(String id, String errorMsg, String lastStatus) {
        jdbc.update(
            "UPDATE extraction_drafts SET status = ?, error_msg = ?, retry_count = retry_count + 1, updated_at = NOW() WHERE id = ?",
            "REJECTED", lastStatus + ": " + errorMsg, id);
        log.error("抽取失败 id={}: {}", id, errorMsg);
    }

    /** 异步审计日志 (05 文档 §六: 不阻塞主流程) */
    private void auditAsync(String extractionId, String action, int rules, int entities, int links) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> audit = new LinkedHashMap<>();
                audit.put("entityType", "extraction");
                audit.put("sourceType", "KB");
                audit.put("activity", action);
                audit.put("entityId", extractionId);
                audit.put("rules", rules);
                audit.put("entities", entities);
                audit.put("links", links);
                audit.put("timestamp", LocalDateTime.now().format(DT_FMT));
                // 实际场景: POST /api/v1/security/audit/log
                log.info("[AUDIT] extraction {}.{}: rules={} entities={} links={}", action, extractionId, rules, entities, links);
            } catch (Exception e) {
                log.warn("审计日志写入失败(不影响主流程): {}", e.getMessage());
            }
        });
    }
}
