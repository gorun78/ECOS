package com.chinacreator.gzcm.engine.ontology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W77/C61 验收 — 指标定义基表合规化（`V168.1`）。
 *
 * <p>断言 V168.1 迁移脚本已落，且满足三条红线：
 * <ol>
 *   <li>给旧 {@code metric_definition} 补三字段（caliber_id / formula_version / caliber_snapshot）+ DR 列；</li>
 *   <li>新表 {@code ecos_metric_definition} 主键 {@code VARCHAR(36)}（MC01）；</li>
 *   <li>新表 DR05~DR08 全齐（is_deleted / create_time / update_time / create_by / update_by / version_no / domain）。</li>
 * </ol>
 *
 * <p>本测试遵循"仅落文件不实跑库"的 §14.4 未授权纪律，只做 DDL 静态合规校验（源码文本比对），
 * 不启 DataSource、不 touching libpg。
 */
class MetricDefinitionConformTest {

    // V200 治理后 DDL 单源目录（数据库访问规范 v1.4 §八），仓库根相对
    private static final String DDL_FILE =
            "docs/50-db_script/02-flyway-migration/gateway/V168.1__metric_definition_conform.sql";

    @Test
    void v168_1FileExists() {
        Path ddl = locateDdl();
        assertTrue(Files.exists(ddl), "V168.1 迁移脚本缺失：" + ddl.toAbsolutePath());
    }

    @Test
    void oldTableGetsThreeCaliberFields() throws IOException {
        String s = readDdl();
        // 旧表 ADD COLUMN 三字段
        assertTrue(
            s.contains("ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS caliber_id")
                || s.contains("metric_definition ADD COLUMN IF NOT EXISTS caliber_id"),
            "V168.1 未给旧 metric_definition 表补 caliber_id 列");
        assertTrue(s.contains("caliber_id") && s.contains("VARCHAR(36)")
                && s.contains("formula_version") && s.contains("VARCHAR(20)")
                && s.contains("caliber_snapshot") && s.contains("TEXT"),
            "V168.1 三字段（caliber_id/formula_version/caliber_snapshot）必带且 caliber_snapshot 必为 TEXT（MC02）");
    }

    @Test
    void newTableHasMco1PrimaryAndDr0508() throws IOException {
        String s = readDdl();
        assertTrue(s.contains("CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_metric_definition"),
            "V168.1 必建合规新表 ecos_ontology.ecos_metric_definition（唯一可写入口，R-4 a+b）");
        // MC01：新表 PK 形态 VARCHAR(36)，不能是 BIGSERIAL / gen_random_uuid()
        String body = s.substring(s.indexOf("CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_metric_definition"));
        assertTrue(body.contains("id") && body.contains("VARCHAR(36) PRIMARY KEY"),
            "新表 PK 必为 VARCHAR(36)（MC01）");
        assertNoForbiddenDdl(body, "ecs_metric_definition".replace("ecs", "ecos"));
        // DR05~DR08 全齐
        assertTrue(body.contains("is_deleted")
                && body.contains("create_time")
                && body.contains("update_time")
                && body.contains("create_by")
                && body.contains("update_by")
                && body.contains("version_no")
                && body.contains("domain"),
            "新表 DR05/06/07/08 列（is_deleted / create_time / update_time / create_by / update_by / version_no / domain）必齐");
    }

    private static void assertNoForbiddenDdl(String body, String tableNameLabel) {
        assertTrue(!body.contains("BIGSERIAL"), tableNameLabel + " 分片内含 BIGSERIAL（违 MC01）");
        assertTrue(!body.contains("gen_random_uuid()"), tableNameLabel + " 分片内含 gen_random_uuid()（违 MC01）");
        assertTrue(!body.contains("JSONB"), tableNameLabel + " 分片内含 JSONB（违 MC02）");
    }

    private static String readDdl() {
        try {
            return Files.readString(locateDdl(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("读取 V168.1 失败：" + e.getMessage());
            return null;
        }
    }

    private static Path locateDdl() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            Path candidate = cur.resolve(DDL_FILE);
            if (Files.exists(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new AssertionError("V168.1 迁移脚本未在祖先目录找到，期望相对路径：" + DDL_FILE);
    }
}
