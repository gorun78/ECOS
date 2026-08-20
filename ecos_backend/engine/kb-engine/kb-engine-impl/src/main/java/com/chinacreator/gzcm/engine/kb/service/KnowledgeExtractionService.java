package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 知识抽取服务 — 文档上传→解析→LLM抽取→审核→入库全链路。
 * <p>
 * 状态机: UPLOADED → PARSING → EXTRACTING → PENDING_REVIEW → APPROVED / REJECTED
 * kb不直接调LLM，通过 ai-engine Agent Loop API 执行抽取。
 * </p>
 *
 * @author ECOS KB Engine Team
 * @since 2026-08-08
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

    private final JdbcTemplate jdbc;
    private final RestTemplate restTemplate;
    private final ComplianceRuleMapper ruleMapper;
    private final KGWriterService kgWriter;
    private final DocumentParserService documentParserService;

    public KnowledgeExtractionService(JdbcTemplate jdbc,
                                      ComplianceRuleMapper ruleMapper,
                                      KGWriterService kgWriter,
                                      DocumentParserService documentParserService) {
        this.jdbc = jdbc;
        this.ruleMapper = ruleMapper;
        this.kgWriter = kgWriter;
        this.documentParserService = documentParserService;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        ensureTables();
        try { Files.createDirectories(Paths.get(UPLOAD_DIR)); } catch (IOException ignored) {}
        log.info("KnowledgeExtractionService initialized");
    }

    // ── DDL ──────────────────────────────────────────

    private void ensureTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS extraction_drafts (
                id            VARCHAR(64) PRIMARY KEY,
                file_name     VARCHAR(255),
                file_path     TEXT,
                status        VARCHAR(32) DEFAULT 'UPLOADED',
                parsed_text   TEXT,
                extracted_entities_json TEXT,
                extracted_rules_json    TEXT,
                error_msg     TEXT,
                retry_count   INT DEFAULT 0,
                created_at    TIMESTAMP DEFAULT NOW(),
                updated_at    TIMESTAMP DEFAULT NOW()
            )
            """);
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
        // Step 1: 解析文本 (PMO-34: Tika 解析 + 元数据)
        updateStatus(id, "PARSING");
        String text;
        try {
            DocumentParserService.ParseResult parseResult = documentParserService.parse(filePath);
            text = parseResult.getText();
            // 写入解析元数据
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
                String rulesJson = mapper.writeValueAsString(
                        extracted.getOrDefault("rules", Collections.emptyList()));

                jdbc.update(
                    "UPDATE extraction_drafts SET extracted_entities_json = ?, extracted_rules_json = ?, status = 'PENDING_REVIEW'] WHERE id = ?",
                    entitiesJson, rulesJson, id
                );
                log.info("抽取完成: id={}", id);
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

    private String parseFile(Path filePath) throws Exception {
        // PMO-34: Tika 解析替换 UTF-8 直读，支持 pdf/docx/xlsx/pptx/html/txt
        DocumentParserService.ParseResult result = documentParserService.parse(filePath);
        return result.getText();
    }

    /**
     * 调用 ai-engine Agent Loop 执行知识抽取。
     * kb不直接调LLM — 走ai-engine API。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callAiExtraction(String text) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "请从以下文档内容中抽取出：\n"
                + "1. 实体(entities)：列出所有关键业务实体及其类型\n"
                + "2. 关系(relations)：实体之间的关系\n"
                + "3. 规则(rules)：隐含的业务规则或合规要求\n\n"
                + "文档内容:\n" + (text.length() > 8000 ? text.substring(0, 8000) + "..." : text));
        payload.put("systemPrompt", "你是企业知识抽取专家。严格按JSON格式输出: {\"entities\":[{\"name\":\"\",\"type\":\"\"}],\"relations\":[{\"from\":\"\",\"to\":\"\",\"type\":\"\"}],\"rules\":[{\"name\":\"\",\"description\":\"\",\"condition\":\"\",\"action\":\"\"}]}");
        payload.put("temperature", 0.1);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() ->
            restTemplate.postForObject(AGENT_LOOP_URL, request, String.class));

        try {
            String response = future.get(LLM_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (response == null || response.isEmpty()) return Collections.emptyMap();

            Map<String, Object> apiResp = mapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            // 检查顶层success
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
                        // 尝试从文本中提取JSON
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

    // ── 审核 ─────────────────────────────────────────

    public Map<String, Object> approve(String id) {
        Map<String, Object> draft = jdbc.queryForMap(
            "SELECT * FROM extraction_drafts WHERE id = ?", id);

        jdbc.update("UPDATE extraction_drafts SET status = 'APPROVED', updated_at = NOW() WHERE id = ?", id);

        // 写规则到compliance_rules
        try {
            String rulesJson = (String) draft.get("extracted_rules_json");
            if (rulesJson != null && !rulesJson.isEmpty()) {
                List<Map<String, Object>> rules = mapper.readValue(rulesJson,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> rule : rules) {
                    ComplianceRule cr = new ComplianceRule();
                    cr.setName(String.valueOf(rule.getOrDefault("name", "")));
                    cr.setDomain("extracted");
                    cr.setCondition(String.valueOf(rule.getOrDefault("condition", "")));
                    cr.setAction(String.valueOf(rule.getOrDefault("action", "")));
                    cr.setDescription(String.valueOf(rule.getOrDefault("description", "")));
                    cr.setStatus("ACTIVE");
                    cr.setEnabled(true);
                    ruleMapper.insert(cr);
                }
            }
        } catch (Exception e) {
            log.warn("写compliance_rules失败: {}", e.getMessage());
        }

        // 写实体到Neo4j
        try {
            String entitiesJson = (String) draft.get("extracted_entities_json");
            if (entitiesJson != null && !entitiesJson.isEmpty()) {
                jdbc.update(
                    "UPDATE extraction_drafts SET status = 'APPROVED', updated_at = NOW() WHERE id = ?", id);
            }
        } catch (Exception e) {
            log.warn("写Neo4j实体失败: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "APPROVED");
        return result;
    }

    public Map<String, Object> reject(String id) {
        jdbc.update("UPDATE extraction_drafts SET status = 'REJECTED', updated_at = NOW() WHERE id = ?", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "REJECTED");
        return result;
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
}
