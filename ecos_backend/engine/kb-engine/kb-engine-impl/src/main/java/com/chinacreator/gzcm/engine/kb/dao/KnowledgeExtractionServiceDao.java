package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 数据访问收口（R2 / B2-kb / 架构铁律 §2.5 / IR06）：
 * {@code service.KnowledgeExtractionService} 的抽取草稿状态机 JDBC 落点。
 *
 * <p>SQL 原样搬运自原 {@code KnowledgeExtractionService}（表 {@code extraction_drafts}，
 * <b>无 schema 前缀</b>，verbatim 保留；状态机转移语义不变）。</p>
 */
@Repository
public class KnowledgeExtractionServiceDao extends JdbcTemplateDaoSupport {

    /** 上传后插入草稿行（status=UPLOADED）。 */
    public void insertUploadedDraft(String id, String fileName, String filePath) {
        jdbcTemplate.update(
                "INSERT INTO extraction_drafts (id, file_name, file_path, status) VALUES (?, ?, ?, 'UPLOADED')",
                id, fileName, filePath);
    }

    /** 解析成功后回写解析文本 + 推进 EXTRACTING。 */
    public void markExtracting(String id, String parsedText, String fileType,
                               int pageCount, int charCount) {
        jdbcTemplate.update(
                "UPDATE extraction_drafts SET parsed_text = ?, status = 'EXTRACTING', "
                        + "file_type = ?, page_count = ?, char_count = ? WHERE id = ?",
                parsedText, fileType, pageCount, charCount, id);
    }

    /** LLM 抽取成功后回写三类 JSON + 推进 PENDING_REVIEW。 */
    public void markPendingReview(String id, String entitiesJson, String linksJson, String rulesJson) {
        jdbcTemplate.update(
                "UPDATE extraction_drafts SET extracted_entities_json = ?, "
                        + "extracted_links_json = ?, extracted_rules_json = ?, status = 'PENDING_REVIEW' WHERE id = ?",
                entitiesJson, linksJson, rulesJson, id);
    }

    /** 读草稿全行（approve 校验 / getTask 复用）。 */
    public Map<String, Object> fetchDraft(String id) {
        return jdbcTemplate.queryForMap("SELECT * FROM extraction_drafts WHERE id = ?", id);
    }

    /** 读草稿当前 status（reject 前置校验）。 */
    public Map<String, Object> fetchDraftStatus(String id) {
        return jdbcTemplate.queryForMap("SELECT status FROM extraction_drafts WHERE id = ?", id);
    }

    /** 审核通过 → APPROVED。 */
    public void markApproved(String id) {
        jdbcTemplate.update("UPDATE extraction_drafts SET status = 'APPROVED', updated_at = NOW() WHERE id = ?", id);
    }

    /** 审核驳回 → REJECTED（带原因）。 */
    public void markRejected(String reason, String id) {
        jdbcTemplate.update("UPDATE extraction_drafts SET status = 'REJECTED', rejected_reason = ?, updated_at = NOW() WHERE id = ?",
                reason, id);
    }

    /** 列表（分页）— 文件名/状态基列。 */
    public List<Map<String, Object>> listTasks(int pageSize, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, file_name, status, created_at FROM extraction_drafts ORDER BY created_at DESC LIMIT ? OFFSET ?",
                pageSize, offset);
    }

    /** 待审核文件列表（无状态过滤）— 附带三类 JSON + 错误列。 */
    public List<Map<String, Object>> listExtractFilesNoStatus(int pageSize, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, file_name, status, extracted_entities_json, extracted_links_json, error_msg, created_at "
                        + "FROM extraction_drafts ORDER BY created_at DESC LIMIT ? OFFSET ?",
                pageSize, offset);
    }

    /** 待审核文件列表（带状态过滤）— 附带三类 JSON + 错误列。 */
    public List<Map<String, Object>> listExtractFilesByStatus(String status, int pageSize, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, file_name, status, extracted_entities_json, extracted_links_json, error_msg, created_at "
                        + "FROM extraction_drafts WHERE status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                status, pageSize, offset);
    }

    /** 候选清单的三类抽取 JSON 行（单文件）。 */
    public List<Map<String, Object>> listCandidateJsons(String fileId) {
        return jdbcTemplate.queryForList(
                "SELECT extracted_entities_json, extracted_links_json, extracted_rules_json "
                        + "FROM extraction_drafts WHERE id = ?", fileId);
    }

    /** 通用状态推进（updateStatus）。 */
    public void updateStatus(String id, String status) {
        jdbcTemplate.update("UPDATE extraction_drafts SET status = ?, updated_at = NOW() WHERE id = ?", status, id);
    }

    /** 失败兜底（handleError）→ REJECTED，累计 retry_count + 记录 error_msg。 */
    public void recordError(String status, String errorMsg, String id) {
        jdbcTemplate.update(
                "UPDATE extraction_drafts SET status = ?, error_msg = ?, retry_count = retry_count + 1, updated_at = NOW() WHERE id = ?",
                status, errorMsg, id);
    }
}
