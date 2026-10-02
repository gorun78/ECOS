package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-11 决策记录数据面合规（MC02 JSONB / MC01 PK / DR 基线列）— 静态 DDL 门禁。
 *
 * <p>核心验收 {@code #fiveMandatoryColumnsAreSqlQueryable}：RC-04 五必填字段
 * （assignee / deadline / approver / expectedImpact / controlMetric）在 V211 重建的
 * {@code public.ecos_scenario_decision_record} 里必须成为<b>真实独立列</b>，支持 SQL
 * WHERE 直接查询——禁止仅藏在 {@code *_json TEXT} 的 JSON blob 里。
 * 这是证伪「用 JSON 逃避建模」的最小自足验证，不需要 PG 实跑。</p>
 */
class DecisionRecordRoundTripTest {

    private static final Path MIGRATION_DIR = resolveMigrationDir();

    private static Path resolveMigrationDir() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve("gateway/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)
                    && Files.exists(candidate.resolve("V211__ecos_decision_record_v2.sql"))) {
                return candidate;
            }
            cur = cur.getParent();
        }
        throw new AssertionError(
                "未找到 DDL 单源目录，从 " + Path.of("").toAbsolutePath().normalize()
                        + " 向上递归 8 级均失败");
    }

    /** 剥注释 + 单引号字面量（防 COMMENT '...' 误匹配）。 */
    private static String bodyOnly(String fileName) throws IOException {
        String raw = Files.readString(MIGRATION_DIR.resolve(fileName), StandardCharsets.UTF_8);
        raw = raw.replaceAll("(?s)/\\*.*?\\*/", " ");
        raw = raw.replaceAll("'[^']*'", "''");
        raw = raw.replaceAll("--[^\n]*", " ");
        return raw;
    }

    @Test
    @DisplayName("F07-11 验收: 五必填（assignee/deadline/approver/expectedImpact/controlMetric）在 V211 均有真实独立列，类型可 SQL 查询")
    void fiveMandatoryColumnsAreSqlQueryable() throws IOException {
        String v211 = bodyOnly("V211__ecos_decision_record_v2.sql");

        // 五必填 → V211 列名映射（R-30①+②「FC-04 五必填以列扩展承载」）
        // 每列必须有正确的类型声明且带独立列名，不是 JSON blob 角色
        assertTrue(v211.matches("(?s).*\\bowner_user_id\\s+VARCHAR\\(36\\).*"),
                "assignee/owner_user_id 必须是 VARCHAR(36) 独立列");
        assertTrue(v211.matches("(?s).*\\bdue_date\\s+DATE.*"),
                "deadline/due_date 必须是 DATE 独立列");
        assertTrue(v211.matches("(?s).*\\bapprover_user_id\\s+VARCHAR\\(36\\).*"),
                "approver/approver_user_id 必须是 VARCHAR(36) 独立列");
        assertTrue(v211.matches("(?s).*\\bexpected_impact\\s+NUMERIC\\(18,2\\).*"),
                "expectedImpact/expected_impact 必须是 NUMERIC(18,2) 独立列（ST03-A 金额形态）");
        assertTrue(v211.matches("(?s).*\\bcontrol_metric\\s+VARCHAR\\(128\\).*"),
                "controlMetric/control_metric 必须是 VARCHAR(128) 独立列");

        // 防守证：五必填的任意一列不得只以 _json 后缀独立列形式存在（即不得「用 JSON 替代实列」）
        // 若有人把 owner_user_id 改名 owner_user_id_json 就会 FAIL
        assertFalse(v211.contains("owner_user_id_json"),    "owner_user_id 不应有并行 _json 后缀列");
        assertFalse(v211.contains("due_date_json"),          "due_date 不应有并行 _json 后缀列");
        assertFalse(v211.contains("approver_user_id_json"),  "approver_user_id 不应有并行 _json 后缀列");
        assertFalse(v211.contains("expected_impact_json"),   "expected_impact 不应有并行 _json 后缀列");
        assertFalse(v211.contains("control_metric_json"),    "control_metric 不应有并行 _json 后缀列");
    }

    @Test
    @DisplayName("F07-11 E-3 残留 JSON 三列（action_plan_json / source_refs_json / compliance_detail_json）为 TEXT，不承载五必填")
    void nonMandatoryPayloadColumnsAreTextOnly() throws IOException {
        String v211 = bodyOnly("V211__ecos_decision_record_v2.sql");

        // 三个 _json 列承载「剩余叙述」，类型统一 TEXT（MC02 零 JSONB 路径已在新表面完成）
        assertTrue(v211.matches("(?s).*\\baction_plan_json\\s+TEXT.*"),
                "action_plan_json 应为 TEXT（五必填已拆实列，此列只存剩余叙述）");
        assertTrue(v211.matches("(?s).*\\bsource_refs_json\\s+TEXT.*"),
                "source_refs_json 应为 TEXT（权威明细在 ecos_decision_source_ref 明细表）");
        assertTrue(v211.matches("(?s).*\\bcompliance_detail_json\\s+TEXT.*"),
                "compliance_detail_json 应为 TEXT（裁决权威在 security-engine，本列仅回执缓存）");
    }

    @Test
    @DisplayName("F07-11 MC01: 两新表 PK 均为 VARCHAR(36) 应用侧 UUID，零 gen_random_uuid()")
    void primaryKeyIsVarchar36NoGenRandomUuid() throws IOException {
        String v211 = bodyOnly("V211__ecos_decision_record_v2.sql");

        assertTrue(v211.matches("(?s).*\\bid\\s+VARCHAR\\(36\\)\\s+PRIMARY\\s+KEY.*"),
                "V211 建表 PK 统一 VARCHAR(36)，无 gen_random_uuid()");
        assertFalse(v211.contains("gen_random_uuid"), "禁止 DDL 侧 UUID 默认值（MC01）");
        assertFalse(v211.contains("GENERATED\\s+ALWAYS"), "禁止 BIGINT GENERATED ALWAYS PK（旧 X-43 缺陷不得重现）");
    }
}
