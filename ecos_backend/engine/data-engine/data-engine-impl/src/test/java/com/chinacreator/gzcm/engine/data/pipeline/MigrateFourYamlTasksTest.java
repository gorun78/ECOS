package com.chinacreator.gzcm.engine.data.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-03 / W44：V168 YAML→JSON DAG 一次性迁移脚本结构守卫（无 DB，源码断言）。
 *
 * <p>验收口径（详细设计-02 §F02-03 / C33）：迁移脚本
 * {@code db/migration/V168__migrate_yaml_pipeline_task.sql} 必须满足：
 * <ol>
 *   <li><b>报告先行</b>：Phase B 逐任务做 SQL 可判定解析分类（yaml_content 非空 ∧ 存在已解析
 *       步骤 ∧ node_id/node_type 无缺失 ∧ 同任务 node_id 不重复），写
 *       {@code ecos_data.ecos_pipeline_yaml_migration_report}（PENDING_MIGRATE | REJECTED）。</li>
 *   <li><b>整批失败即拒，禁静默跳过</b>：Phase C 入口门命中任一 REJECTED 即
 *       {@code RAISE EXCEPTION}（事务回滚），不得跳过失败任务继续迁移。</li>
 *   <li><b>无损可追溯产物</b>：definition（DRAFT）+ node（step→node）+ edge（相邻 step_order→edge）
 *       三段 INSERT 齐全，id 前缀 ytm_/ytn_/yte_ 确定性键。</li>
 *   <li><b>幂等</b>：全部 INSERT 走 {@code NOT EXISTS}（重跑 0 行新增）。</li>
 *   <li><b>非静默</b>：脚本不放行"0 任务"空转 —— 报告表按源表 {@code public.ecos_pipeline_task}
 *       全量登记（4 行 YAML task 一次性迁移口径 = 源表既有 4 行）。</li>
 * </ol>
 *
 * <p>本测试不连 DB；对脚本做结构/正则断言，防止后续误删整批拒逻辑或幂等键。
 */
@DisplayName("F02-03 V168 YAML→JSON 迁移脚本结构：解析分类 + 整批拒 + 幂等")
class MigrateFourYamlTasksTest {

    @Test
    @DisplayName("V168 脚本存在且声明迁移报告表生态（ecos_data.ecos_pipeline_yaml_migration_report）")
    void migrationScriptExists_andReportTableDefined() throws IOException {
        Path sql = migrationSql();
        assertTrue(Files.isRegularFile(sql), "缺 V168 迁移脚本: " + sql);
        String s = Files.readString(sql, StandardCharsets.UTF_8);
        assertTrue(s.contains("ecos_data.ecos_pipeline_yaml_migration_report"),
                "报告表 ecos_data.ecos_pipeline_yaml_migration_report 未建/未引用");
        assertTrue(s.contains("migration_batch") && s.contains("DEFAULT 'V168'"),
                "报告表须以 migration_batch 携带 'V168' 批次标识（多批次迁移可追加，禁覆盖旧批次）");
        assertTrue(s.contains("outcome"), "报告表须有 outcome 列（PENDING_MIGRATE|REJECTED|MIGRATED）");
        assertTrue(s.contains("reason"), "报告表须有 reason 列（REJECTED 行必填，禁静默）");
    }

    @Test
    @DisplayName("Phase B 分类判据四段全在：yaml 非空 ∧ 有步骤 ∧ node_id 不空 ∧ node_id 不重复")
    void phaseB_parsabilityCriteria() throws IOException {
        String s = Files.readString(migrationSql(), StandardCharsets.UTF_8);
        // ① yaml_content 非空
        assertTrue(s.contains("yaml_content IS NOT NULL") && s.contains("TRIM(t.yaml_content)"),
                "判据①：yaml_content 非空 缺失");
        // ② 存在已解析步骤行
        assertTrue(s.contains("EXISTS (SELECT 1 FROM public.ecos_pipeline_step"),
                "判据②：存在已解析步骤行（ecos_pipeline_step）缺失");
        // ③ node_id / node_type 两列均须非空
        assertTrue(s.contains("s.node_id IS NULL") && s.contains("s.node_type IS NULL"),
                "判据③：node_id/node_type 非空校验缺失（DAG 派生前置）");
        // ④ 同任务 node_id 不自重复（self join by s2.node_id = s1.node_id AND s2.id <> s1.id）
        assertTrue(s.contains("s2.node_id = s1.node_id") && s.contains("s2.id <> s1.id"),
                "判据④：同任务 node_id 去重校验缺失");
        // 分类输出两态必须都在
        assertTrue(s.contains("'PENDING_MIGRATE'"), "分类结果 PENDING_MIGRATE 缺失");
        assertTrue(s.contains("'REJECTED'"), "分类结果 REJECTED 缺失");
    }

    @Test
    @DisplayName("Phase C 整批拒门：任一 REJECTED → RAISE EXCEPTION（禁静默跳过）")
    void phaseC_failWholeBatchOnRejected() throws IOException {
        String s = Files.readString(migrationSql(), StandardCharsets.UTF_8);
        // 入口门：SELECT COUNT(*) ... outcome = 'REJECTED' → IF n_rejected > 0 → RAISE EXCEPTION
        assertTrue(s.contains("outcome = 'REJECTED'"),
                "入口门须统计 REJECTED 行");
        assertTrue(s.contains("RAISE EXCEPTION"),
                "Phase C 须 RAISE EXCEPTION（整批拒，禁静默）");
        assertTrue(s.contains("n_rejected"),
                "整批拒门须以 n_rejected 计数（IF n_rejected > 0 THEN RAISE EXCEPTION）");
        // 报告留痕先提交（Phase B 独立事务）—— 拒批后报告仍在
        assertTrue(s.contains("COMMIT;"), "Phase B/Phase C 须显式 COMMIT（报告先提交、迁移事务随后）");
    }

    @Test
    @DisplayName("迁移产物三段齐全：definition(DRAFT) + node + edge，且均 NOT EXISTS 幂等")
    void phaseC_producesDefinitionNodeEdge_allIdempotent() throws IOException {
        String s = Files.readString(migrationSql(), StandardCharsets.UTF_8);
        // C1 definition（DRAFT 待人工签核）
        assertTrue(s.contains("ecos_data.ecos_pipeline_definition"), "产 definition 表缺失");
        assertTrue(s.contains("'DRAFT'"), "迁入 definition 须 status='DRAFT' 待签核（同 V166 精神）");
        // C2 step → node
        assertTrue(s.contains("ecos_data.ecos_pipeline_node"), "产 node 表缺失");
        assertTrue(s.contains("ytn_"), "node id 前缀 ytn_ 缺失（确定性键）");
        // C3 相邻 step → edge（step_order 链式依赖）
        assertTrue(s.contains("ecos_data.ecos_pipeline_edge"), "产 edge 表缺失");
        assertTrue(s.contains("yte_"), "edge id 前缀 yte_ 缺失（确定性键）");
        assertTrue(s.contains("b.step_order = a.step_order + 1"),
                "edge 派生须按相邻 step_order 建立链式依赖");
        // 幂等：NOT EXISTS 防重（至少 3 处：definition / node / edge）
        int notExists = countOccurrences(s, "NOT EXISTS");
        assertTrue(notExists >= 3, "定义/node/edge 三段 INSERT 均须 NOT EXISTS 幂等，NOT EXISTS 至少 3 处，实际 " + notExists);
        // 不得删源表 / 不得改源表（IR03）
        assertFalse(s.toUpperCase().contains("DELETE FROM PUBLIC.ECOS_PIPELINE_TASK"),
                "V168 禁 DELETE 源表（IR03 只加不删；YAML 侧转只读归档，删除待授权）");
        assertFalse(s.toUpperCase().contains("DROP TABLE"), "V168 禁 DROP（IR03）");
    }

    @Test
    @DisplayName("报告覆盖源任务全集 + 收口 MIGRATED（PENDING_MIGRATE → MIGRATED update）")
    void phaseC_finalizesReport() throws IOException {
        String s = Files.readString(migrationSql(), StandardCharsets.UTF_8);
        assertTrue(s.contains("FROM public.ecos_pipeline_task"),
                "报告登记须对源表 public.ecos_pipeline_task 全量枚举（4 行 YAML task 全覆盖）");
        assertTrue(s.contains("SET outcome = 'MIGRATED'"),
                "迁移成功后须把 PENDING_MIGRATE 收口为 MIGRATED");
        assertTrue(s.contains("'MIGRATED'"), "MIGRATED 终态枚举缺失");
    }

    private static Path migrationSql() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path cur = dir;
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve(".git")) || Files.isDirectory(cur.resolve(".git"))) {
                // V200 治理后 DDL 单源目录（数据库访问规范 v1.4 §八）
                Path canonical = cur.resolve("docs/50-db_script/02-flyway-migration/gateway/V168__migrate_yaml_pipeline_task.sql");
                if (Files.isRegularFile(canonical)) return canonical;
                return cur.resolve("ecos_backend/gateway/src/main/resources/db/migration/V168__migrate_yaml_pipeline_task.sql");
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位仓库根（缺 .git）");
    }

    private static int countOccurrences(String hay, String needle) {
        int c = 0;
        int i = 0;
        while ((i = hay.indexOf(needle, i)) != -1) {
            c++;
            i += needle.length();
        }
        return c;
    }
}
