package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Security Center API — 数据脱敏 / 策略评估 / 解密仿真 / 审计日志。
 * <p>
 * 前端 SecurityCenter 调用这些端点进行 Rust 安全引擎沙箱演示。
 * 本 Controller 提供 Java 侧模拟实现，与 frontend mockData 行为一致。
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private static final Logger log = LoggerFactory.getLogger(SecurityController.class);

    /** 内存审计日志 */
    private final List<Map<String, Object>> auditLogs = new CopyOnWriteArrayList<>();

    public SecurityController() {
        // 种子审计日志
        seedAuditLogs();
    }

    // ═══════════════════════════════════════════════════════════
    //  1. 数据遮蔽 (Rust Mask 引擎)
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/mask")
    public ApiResponse<Map<String, Object>> mask(@RequestBody Map<String, Object> body) {
        String value = (String) body.getOrDefault("value", "");
        String maskType = (String) body.getOrDefault("maskType", "SHA256");

        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String masked;
            String engine;

            switch (maskType.toUpperCase()) {
                case "SHA256" -> {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] hash = md.digest(value.getBytes());
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hash) sb.append(String.format("%02x", b));
                    masked = sb.toString().substring(0, 16) + "...";
                    engine = "Rust sha2::Sha256 (via Java fallback)";
                }
                case "AES_GCM_SIV" -> {
                    masked = "AES_GCM:" + value.hashCode();
                    engine = "Rust aes-gcm-siv crate (via Java fallback)";
                }
                case "RSA_OAEP" -> {
                    masked = "RSA:" + Math.abs(value.hashCode() % 10000);
                    engine = "Rust rsa crate (via Java fallback)";
                }
                default -> {
                    masked = "MASKED_" + value.hashCode();
                    engine = "Rust mask-engine (via Java fallback)";
                }
            }

            result.put("masked_value", masked);
            result.put("engine", engine);
            result.put("original_length", value.length());

            auditLog("DATA_MASK", "SUCCESS",
                    "Masked value with " + maskType + ", engine=" + engine);
        } catch (Exception e) {
            result.put("masked_value", "ERROR: " + e.getMessage());
            result.put("engine", "Java-fallback-failed");
            auditLog("DATA_MASK", "ERROR", "Mask failed: " + e.getMessage());
        }

        return ApiResponse.success(result);
    }

    // ═══════════════════════════════════════════════════════════
    //  2. 策略评估 (Rhai 脚本引擎)
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/evaluate-filter")
    public ApiResponse<Map<String, Object>> evaluateFilter(@RequestBody Map<String, Object> body) {
        String expression = (String) body.getOrDefault("expression", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> rowData = (Map<String, Object>) body.getOrDefault("rowData", Map.of());
        String userRole = (String) body.getOrDefault("userRole", "viewer");

        Map<String, Object> result = new LinkedHashMap<>();
        boolean allowed;

        try {
            // 简单表达式评估模拟
            if (expression.contains("classification") || expression.contains("level")) {
                String level = String.valueOf(rowData.getOrDefault("classification",
                        rowData.getOrDefault("level", "public")));
                allowed = !"TOP_SECRET".equalsIgnoreCase(level) || "admin".equalsIgnoreCase(userRole);
            } else if (expression.contains("org") || expression.contains("department")) {
                String userOrg = String.valueOf(rowData.getOrDefault("org",
                        rowData.getOrDefault("department", "")));
                allowed = !userOrg.isEmpty() && !"blocked_dept".equalsIgnoreCase(userOrg);
            } else if (expression.contains("role")) {
                allowed = "admin".equalsIgnoreCase(userRole) || "analyst".equalsIgnoreCase(userRole);
            } else if (expression.contains("contains") || expression.contains("match")) {
                allowed = !expression.contains("blocked") && !expression.contains("deny");
            } else {
                // 默认：admin 角色通过，其他拒绝
                allowed = "admin".equalsIgnoreCase(userRole);
            }

            result.put("success", true);
            result.put("allowed", allowed);
            result.put("engine", "Rust rhai v1.x (via Java expression parser)");
            result.put("expression", expression);

            auditLog("POLICY_EVAL", allowed ? "GRANTED" : "DENIED",
                    "Expr: " + expression.substring(0, Math.min(80, expression.length())));
        } catch (Exception e) {
            result.put("success", false);
            result.put("allowed", false);
            result.put("error", "Evaluation error: " + e.getMessage());
            result.put("engine", "Java-fallback-failed");
        }

        return ApiResponse.success(result);
    }

    // ═══════════════════════════════════════════════════════════
    //  3. 解密仿真 (零信任解密网关)
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/decrypt")
    public ApiResponse<Map<String, Object>> decrypt(@RequestBody Map<String, Object> body) {
        String userId = (String) body.getOrDefault("userId", "unknown");
        String orgId = (String) body.getOrDefault("orgId", "");
        String datasetId = (String) body.getOrDefault("datasetId", "");

        Map<String, Object> result = new LinkedHashMap<>();
        List<String> traces = new ArrayList<>();

        traces.add("[1] 初始化零信任解密链路: 用户 " + userId + " → 数据集 " + datasetId);
        traces.add("[2] 校验组织归属: orgId=" + orgId);

        // 模拟判定逻辑
        boolean success;
        String verdict;

        if ("admin_guorong".equals(userId) || "superadmin".equals(userId)) {
            success = true;
            verdict = "GRANTED: 超级管理员完全解密权限";
            traces.add("[3] 超管豁免: 绕过所有 DAC/MAC 门禁");
        } else if (orgId.contains("blocked") || orgId.contains("external")) {
            success = false;
            verdict = "DENIED: 外部组织无解密权限 (物理隔离)";
            traces.add("[3] 组织级拒绝: 外部组织 blocked");
        } else if (datasetId.contains("RESTRICTED") || datasetId.contains("classified")) {
            success = false;
            verdict = "DENIED: 机密数据集需额外审批";
            traces.add("[3] 分类标记拒绝: 数据集密级不足");
        } else {
            success = true;
            verdict = "GRANTED: 行级安全门禁通过，明文段还原";
            traces.add("[3] DAC/MAC 门禁全部通过");
        }

        traces.add("[4] 解密操作完成, verdict=" + verdict);

        result.put("success", success);
        result.put("verdict", verdict);
        result.put("traces", traces);
        result.put("engine", "Zero-Trust Decryption Gateway (simulated)");

        auditLog("DECRYPT", success ? "GRANTED" : "DENIED",
                "User=" + userId + " Dataset=" + datasetId + " → " + verdict);

        return ApiResponse.success(result);
    }

    // ═══════════════════════════════════════════════════════════
    //  4. 审计日志
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/audit-logs")
    public ApiResponse<List<Map<String, Object>>> getAuditLogs() {
        return ApiResponse.success(new ArrayList<>(auditLogs));
    }

    // ═══════════════════════════════════════════════════════════
    //  5. 安全诊断报告
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/audit")
    public ApiResponse<Map<String, Object>> getDiagnosticReport() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosticReport",
                """
                ╔══════════════════════════════════════════╗
                ║   ECOS 安全中心 — 自适应安全诊断书      ║
                ╠══════════════════════════════════════════╣
                ║  扫描时间: %s          ║
                ║  引擎状态: Java Sandbox (模拟)          ║
                ║  Rust Mask:    sha2 + aes-gcm 已加载    ║
                ║  Rhai Policy:  v1.x 表达式评估器        ║
                ║  零信任网络:  TOFU 握手通过             ║
                ║                                           ║
                ║  [合规结论]                               ║
                ║  ✓ 数据遮蔽引擎正常                       ║
                ║  ✓ 策略评估链路完整                       ║
                ║  ✓ 零信任解密网关可用 (模拟模式)          ║
                ║  ⚠ 生产环境需升级至 Rust Native Engine   ║
                ║                                           ║
                ║  [风险提示]                               ║
                ║  • 当前使用 Java fallback 实现            ║
                ║  • 建议部署 Rust 原生引擎以获得            ║
                ║    <1ms 级延迟和硬件级安全隔离             ║
                ╚══════════════════════════════════════════╝
                """.formatted(Instant.now().toString()));

        return ApiResponse.success(result);
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal
    // ═══════════════════════════════════════════════════════════

    private void auditLog(String event, String status, String detail) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", UUID.randomUUID().toString().substring(0, 8));
        entry.put("timestamp", Instant.now().toString());
        entry.put("event", event);
        entry.put("status", status);
        entry.put("detail", detail);
        auditLogs.add(0, entry);

        // 最多保留 100 条
        while (auditLogs.size() > 100) {
            auditLogs.remove(auditLogs.size() - 1);
        }
    }

    private void seedAuditLogs() {
        String[][] seeds = {
            {"LOGIN", "SUCCESS", "admin_guorong logged in from 10.0.1.25"},
            {"DATA_MASK", "SUCCESS", "Masked passenger PII (SHA256)"},
            {"POLICY_EVAL", "GRANTED", "Rhai rule: admin role → grant"},
            {"DECRYPT", "GRANTED", "Dataset ds_ticket_sales → org_aviation_hq"},
            {"POLICY_EVAL", "DENIED", "Rhai rule: guest role → deny dataset ds_passenger_manifest"},
            {"DATA_MASK", "SUCCESS", "Masked financial account (AES_GCM_SIV)"},
            {"LOGIN", "FAILED", "brute-force attempt blocked from 192.168.99.1"},
            {"DECRYPT", "DENIED", "External org attempted RESTRICTED dataset access"},
        };

        for (int i = seeds.length - 1; i >= 0; i--) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", "seed-" + i);
            entry.put("timestamp", Instant.now().minusSeconds(i * 300L).toString());
            entry.put("event", seeds[i][0]);
            entry.put("status", seeds[i][1]);
            entry.put("detail", seeds[i][2]);
            auditLogs.add(entry);
        }
    }
}
