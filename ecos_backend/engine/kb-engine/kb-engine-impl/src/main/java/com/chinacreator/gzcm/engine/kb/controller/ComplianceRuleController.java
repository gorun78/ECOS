package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
import com.chinacreator.gzcm.engine.kb.service.ComplianceRuleVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge/compliance-rules")
public class ComplianceRuleController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRuleController.class);

    @Autowired
    private ComplianceRuleMapper complianceRuleMapper;

    @Autowired
    private ComplianceRuleVersionService complianceRuleVersionService;

    // ── GET / — 查询所有规则 ──────────────────────

    @GetMapping
    public ApiResponse<List<ComplianceRule>> findAll() {
        List<ComplianceRule> rules = complianceRuleMapper.findAll();
        return ApiResponse.success(rules);
    }

    // ── GET /{id} — 按ID查询 ──────────────────────

    @GetMapping("/{id}")
    public ApiResponse<ComplianceRule> findById(@PathVariable String id) {
        ComplianceRule rule = complianceRuleMapper.findById(id);
        if (rule == null) {
            return ApiResponse.notFound("Rule " + id + " not found");
        }
        return ApiResponse.success(rule);
    }

    // ── POST / — 新增规则（UUID + DRAFT） ──────────

    @PostMapping
    public ApiResponse<ComplianceRule> insert(@RequestBody ComplianceRule rule) {
        rule.setId(UUID.randomUUID().toString());
        if (rule.getStatus() == null || rule.getStatus().isEmpty()) {
            rule.setStatus("DRAFT");
        }
        if (rule.getVersion() < 1) {
            rule.setVersion(1);
        }
        long now = System.currentTimeMillis();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);

        complianceRuleMapper.insert(rule);
        log.info("Created compliance rule: id={}, name={}", rule.getId(), rule.getName());
        return ApiResponse.success(rule);
    }

    // ── PUT /{id} — 更新规则（触发版本快照） ──────

    @PutMapping("/{id}")
    public ApiResponse<ComplianceRule> update(@PathVariable String id, @RequestBody ComplianceRule incoming) {
        ComplianceRule existing = complianceRuleMapper.findById(id);
        if (existing == null) {
            return ApiResponse.notFound("Rule " + id + " not found");
        }

        // 版本快照：将当前版本写入 sys_rule_version
        int newVersion = existing.getVersion() + 1;
        long now = System.currentTimeMillis();

        complianceRuleVersionService.snapshotVersion(existing, incoming.getApprovedBy(), now, newVersion);

        // 更新规则字段
        existing.setName(incoming.getName());
        existing.setDomain(incoming.getDomain());
        existing.setRuleType(incoming.getRuleType());
        existing.setCondition(incoming.getCondition());
        existing.setAction(incoming.getAction());
        existing.setPriority(incoming.getPriority());
        existing.setEnabled(incoming.isEnabled());
        existing.setDescription(incoming.getDescription());
        existing.setStatus(incoming.getStatus());
        existing.setRequiredFactList(incoming.getRequiredFactList());
        existing.setExtractedRuleId(incoming.getExtractedRuleId());
        existing.setApprovedBy(incoming.getApprovedBy());
        existing.setEffectiveDate(incoming.getEffectiveDate());
        existing.setExpiryDate(incoming.getExpiryDate());
        existing.setVersion(newVersion);
        existing.setUpdatedAt(now);

        complianceRuleMapper.update(existing);
        log.info("Updated compliance rule: id={}, version {} → {}", id, newVersion - 1, newVersion);
        return ApiResponse.success(existing);
    }

    // ── DELETE /{id} — 删除规则 ────────────────────

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        ComplianceRule existing = complianceRuleMapper.findById(id);
        if (existing == null) {
            return ApiResponse.notFound("Rule " + id + " not found");
        }
        complianceRuleMapper.deleteById(id);
        log.info("Deleted compliance rule: id={}", id);
        return ApiResponse.success(Map.of("deleted", id));
    }

    // ── GET /{id}/versions — 查询版本历史 ──────────

    @GetMapping("/{id}/versions")
    public ApiResponse<List<Map<String, Object>>> getVersions(@PathVariable String id) {
        ComplianceRule rule = complianceRuleMapper.findById(id);
        if (rule == null) {
            return ApiResponse.notFound("Rule " + id + " not found");
        }
        List<Map<String, Object>> versions = complianceRuleVersionService.getVersions(id);
        return ApiResponse.success(versions);
    }
}
