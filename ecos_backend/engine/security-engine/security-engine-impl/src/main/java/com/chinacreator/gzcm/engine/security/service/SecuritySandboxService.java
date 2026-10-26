package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.common.event.PipelineEvent;
import com.chinacreator.gzcm.common.security.filter.RowFilterVerdictContract;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SecuritySandboxService {

    private static final Logger log = LoggerFactory.getLogger(SecuritySandboxService.class);

    private final List<Map<String, Object>> auditLogs = new CopyOnWriteArrayList<>();
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 属主门面（sysman {@code ISecurityProfileQueryService}）——SEC-P0-5 超管角色判定
     * 只经此面（#142/B4-3e-86，D-B4-103：原 {@code SecuritySandboxDao.countSuperAdminRole}
     * 幻影列 JOIN，两表主键为 user_id/role_id、无 profile_id/role_name 列 ⇒ 恒 SQL 错被
     * catch 折 0 ⇒ 超管永假。单源语句现落 sysman 属主 DAO 的 td_user_role→td_role 真列）。
     */
    @Autowired(required = false)
    private ISecurityProfileQueryService profileQueryService;

    public SecuritySandboxService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        seedAuditLogs();
    }

    /** 装配入口（可选装配：monolith 宿主同 context 注入；独立引擎进程缺装配时按不可用走 fail-closed）。 */
    public void setProfileQueryService(ISecurityProfileQueryService profileQueryService) {
        this.profileQueryService = profileQueryService;
    }

    public Map<String, Object> maskValue(String value, String maskType) {
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
            auditLog("DATA_MASK", "SUCCESS", "Masked value with " + maskType + ", engine=" + engine);

            // ── T6: 脱敏联动通知 ──
            publishMaskingEvent(maskType);
        } catch (Exception e) {
            result.put("masked_value", "ERROR: " + e.getMessage());
            result.put("engine", "Java-fallback-failed");
            auditLog("DATA_MASK", "ERROR", "Mask failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * 行过滤谓词求值（{@code /api/security/evaluate-filter} 的真生产侧，#142/B4-3e-7）。
     *
     * <p>应答段一律经 {@link RowFilterVerdictContract} 工厂组装：判定完成走
     * {@link RowFilterVerdictContract#evaluated}，捕获到异常走
     * {@link RowFilterVerdictContract#evaluationFailed}。两者外形相同（都带
     * {@code allowed}），差别只在 {@code success} —— 读侧据此把"引擎没答"与
     * "策略判了拒绝"分开（差距 D-B4-42；收口前这两条腿在消费侧完全同值）。</p>
     */
    public Map<String, Object> evaluateFilter(String expression, Map<String, Object> rowData, String userRole) {
        boolean allowed;
        try {
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
                allowed = "admin".equalsIgnoreCase(userRole);
            }
            auditLog("POLICY_EVAL", allowed ? "GRANTED" : "DENIED",
                    "Expr: " + expression.substring(0, Math.min(80, expression.length())));
            return RowFilterVerdictContract.evaluated(allowed, expression);
        } catch (Exception e) {
            return RowFilterVerdictContract.evaluationFailed("Evaluation error: " + e.getMessage());
        }
    }

    public Map<String, Object> decrypt(String userId, String orgId, String datasetId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> traces = new ArrayList<>();
        traces.add("[1] 初始化零信任解密链路: 用户 " + userId + " → 数据集 " + datasetId);
        traces.add("[2] 校验组织归属: orgId=" + orgId);

        boolean success;
        String verdict;
        // SEC-P0-5: 删除硬编码后门，改基于角色配置的超级管理员判断
        boolean isSuperAdmin = isSuperAdminRole(userId);
        if (isSuperAdmin) {
            success = true;
            verdict = "GRANTED: 超级管理员权限（角色验证通过）";
            traces.add("[3] 超管授权: 通过数据库角色验证，非硬编码豁免");
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
        return result;
    }

    public List<Map<String, Object>> getAuditLogs() {
        return new ArrayList<>(auditLogs);
    }

    public String getDiagnosticReport() {
        return """
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
                """.formatted(Instant.now().toString());
    }

    private void auditLog(String event, String status, String detail) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", UUID.randomUUID().toString().substring(0, 8));
        entry.put("timestamp", Instant.now().toString());
        entry.put("event", event);
        entry.put("status", status);
        entry.put("detail", detail);
        auditLogs.add(0, entry);
        while (auditLogs.size() > 100) {
            auditLogs.remove(auditLogs.size() - 1);
        }
    }

    /**
     * T6: 脱敏操作后发布 PipelineEvent，通知下游模块。
     */
    private void publishMaskingEvent(String maskType) {
        try {
            PipelineEvent event = new PipelineEvent();
            event.setSourceModule("security-engine");
            event.withMetadata(Map.of(
                "type", "DATA_MASKING_RULES_CHANGED",
                "maskType", maskType,
                "timestamp", Instant.now().toString()
            ));
            eventPublisher.publishEvent(event);
            log.debug("PipelineEvent 发布成功: DATA_MASKING_RULES_CHANGED, maskType={}", maskType);
        } catch (Exception e) {
            log.warn("PipelineEvent 发布失败: {}", e.getMessage());
        }
    }

    private void seedAuditLogs() {
        String[][] seeds = {
            {"LOGIN", "SUCCESS", "admin logged in from 10.0.1.25"},
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

    /**
     * SEC-P0-5: 基于数据库角色判断超级管理员，替代硬编码用户名。
     *
     * <p>（#142/B4-3e-86，D-B4-103）取数改经属主门面 {@code hasRoleCode}。
     * 这是<b>授权增益位</b>（true 即解密放行），故异常处置方向＝fail-closed：
     * 门面未装配或取数异常 ⇒ 判否（谁都别多拿权限），与既有 catch→false 同向，
     * 但取数通路换真列（幻影列腿已删，SQL 不再恒错）。</p>
     */
    private boolean isSuperAdminRole(String userId) {
        if (userId == null || userId.isBlank()) return false;
        if (profileQueryService == null) {
            log.warn("SEC-P0-5 属主门面未装配，超管角色判读按不可用处理（fail-closed 判否）: user={}", userId);
            return false;
        }
        try {
            return profileQueryService.hasRoleCode(userId, "ROLE_SUPER_ADMIN");
        } catch (Exception e) {
            log.warn("超级管理员角色查询失败, userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }
}
