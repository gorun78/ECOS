package com.chinacreator.gzcm.engine.ontology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W78/C62 验收 — 指标定义 <b>唯一可写入口</b>（R-4 a）：
 *
 * <p>断言：
 * <ol>
 *   <li>{@code CaliberService} 是本体域唯一承载 {@code ecos_metric_definition} 写路径的 service（R2-B3 收口后
 *       其 JDBC 落点为 {@code CaliberDao}）；</li>
 *   <li>该写路径为<b>schema 限定</b>（DR01/§四附则1：不允许裸表名）；</li>
 *   <li>至少暴露一条 {@code UPDATE ecos_metric_definition} 的 bind 语句（bindMetricCaliber / updateMetricCaliberSnapshot）。</li>
 * </ol>
 *
 * <p>只静态比对源码（§14.4 未授权不启 DataSource），依赖 CaliberService 已经就位的
 * F03-01 W73/W76 交付（metric 三字段写入 + PUBLISHED 无口径 → 拒绝）。
 * R2-B3（§2.5 #1）：SQL 文本随收口下沉 CaliberDao，Service 侧只断言方法面 + 委托。
 */
class MetricDefinitionWritePathTest {

    private static final String CALIBER_SERVICE_REL =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/service/CaliberService.java";

    private static final String CALIBER_DAO_REL =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/CaliberDao.java";

    @Test
    void caliberServiceIsSingleMetricWritePathAndUsesQualifiedTableName() throws IOException {
        Path src = locateRoot().resolve(CALIBER_SERVICE_REL);
        assertTrue(Files.exists(src), "CaliberService 源缺失：" + src.toAbsolutePath());
        String s = Files.readString(src, StandardCharsets.UTF_8);

        // 写路径存在（两条 Writer 方法都在，且委托 CaliberDao）
        assertTrue(s.contains("bindMetricCaliber"),
            "CaliberService 应有 bindMetricCaliber 方法（F03-01 定义侧唯一写入口）");
        assertTrue(s.contains("updateMetricCaliberSnapshot"),
            "CaliberService 应有 updateMetricCaliberSnapshot 方法（发布时刻一次性冻结快照）");
        assertTrue(s.contains("caliberDao.bindMetricCaliber") || s.contains("caliberDao.updateMetricCaliberSnapshot"),
            "CaliberService 的 metric 写路径应委托 CaliberDao（§2.5 #1）");

        // schema 限定（禁裸表名，DR01 + 附则1）：SQL 字符串内必须是 "schema + \".\" + \"ecos_metric_definition"
        Path dao = locateRoot().resolve(CALIBER_DAO_REL);
        assertTrue(Files.exists(dao), "CaliberDao 源缺失：" + dao.toAbsolutePath());
        String daoSrc = Files.readString(dao, StandardCharsets.UTF_8);
        assertTrue(daoSrc.contains("+ schema + \".ecos_metric_definition\"")
                || daoSrc.contains("+ schema + \".ecos_metric_definition "),
            "CaliberDao 必须用 schema 限定表名访问 ecos_metric_definition，禁裸表名");

        // 至少一条 UPDATE 走 PUBLISHED → 快照只读守卫的其他绑定路径（PO stage binding，非 PUBLISHED）
        assertTrue(daoSrc.contains("UPDATE") && daoSrc.contains("ecos_metric_definition"),
            "CaliberDao 应有对 ecos_metric_definition 的 UPDATE 语句（口径升版重绑）");
    }

    @Test
    void metricCaliberBindingBlocksPublishWithoutCaliber() throws IOException {
        Path src = locateRoot().resolve(CALIBER_SERVICE_REL);
        String s = Files.readString(src, StandardCharsets.UTF_8);
        // PUBLISHED 无 caliber → CaliberMissingException
        assertTrue(s.contains("assertMetricCaliberApprovedForPublish"),
            "CaliberService 应有 assertMetricCaliberApprovedForPublish 门禁（W76 规则 4）");
        assertTrue(s.contains("CaliberMissingException") || s.contains("CaliberMissing"),
            "PUBLISHED 无 caliber 时应抛 CaliberMissingException → 400 ECOS-ONTO-040");
    }

    private static Path locateRoot() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            if (Files.exists(cur.resolve("ecos_backend")) && Files.exists(cur.resolve("ecos_frontend"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        fail("无法定位仓库根目录以扫描 CaliberService 源码");
        return null;
    }
}
