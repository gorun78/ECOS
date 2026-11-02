package com.chinacreator.gzcm.engine.kb.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;

/**
 * 知识条目时间列的类型对齐门（#147U live 实测修复）。
 *
 * <p><b>缺陷实况</b>：实体 {@code createdAt/updatedAt} 是 {@code long}（epoch 毫秒），
 * 而 {@code ecos_knowledge.knowledge_article.created_at/updated_at} 是
 * {@code timestamp without time zone}。原 SQL 两侧直传 ⇒ 读存量行 500
 * （{@code 不良的类型值 long : 2026-10-17 …}）、{@code createArticle} 500
 * （{@code column "created_at" is of type timestamp … but expression is of type bigint}）。
 * 修复：读侧按 epoch 毫秒转换，写侧用 {@code CURRENT_TIMESTAMP}。</p>
 *
 * <p>本门直接读接口常量与 {@code @Insert} 注解文本，任何一侧回退成裸列/裸参数即变红。</p>
 *
 * @author ECOS KB #147U
 */
class KnowledgeArticleTimeTypeGateTest {

    @Test
    @DisplayName("读侧：时间列必须按 epoch 毫秒转换后再映射进 long")
    void selectConvertsTimestampToEpochMillis() {
        String cols = KnowledgeArticleMapper.COLS;
        assertTrue(cols.contains("extract(epoch from created_at)"), "created_at 未做 epoch 转换：" + cols);
        assertTrue(cols.contains("extract(epoch from updated_at)"), "updated_at 未做 epoch 转换：" + cols);
        assertTrue(cols.contains("as createdAt") && cols.contains("as updatedAt"), "别名缺失，实体映射会断");
    }

    @Test
    @DisplayName("写侧：时间列必须由数据库时钟写入，禁把 epoch 毫秒直接打进 timestamp 列")
    void insertUsesDatabaseClock() throws Exception {
        String sql = String.join(" ", KnowledgeArticleMapper.class
                .getMethod("insert", KnowledgeArticle.class)
                .getAnnotation(Insert.class).value());
        assertTrue(sql.contains("CURRENT_TIMESTAMP"), "insert 未使用 CURRENT_TIMESTAMP：" + sql);
        assertTrue(!sql.contains("#{createdAt}"), "insert 仍在传 createdAt（bigint 打 timestamp 必 500）：" + sql);
        assertTrue(!sql.contains("#{updatedAt}"), "insert 仍在传 updatedAt：" + sql);
    }
}
