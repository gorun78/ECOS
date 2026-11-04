package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeArticleMapper {

    /**
     * 列清单单源（#147H：七分类 + 规则语义根引用）。
     *
     * <p><b>时间列的类型对齐（#147U 实测修复）</b>：实体 {@code createdAt/updatedAt} 是
     * {@code long}（epoch 毫秒），而 DDL 两列是 {@code timestamp without time zone}。
     * 原写法直接把 timestamp 读进 long ⇒ 存量行读取 500（{@code 不良的类型值 long : 2026-10-17 …}），
     * 写入则以 bigint 打 timestamp 列 ⇒ {@code createArticle} 500。此处统一按 epoch 毫秒转换读，
     * 写入侧改用 {@code CURRENT_TIMESTAMP}（见 {@code insert}）。</p>
     */
    String COLS = "id, title, content, domain, category, knowledge_category as knowledgeCategory, "
            + "rule_ref as ruleRef, source as sourceType, status, "
            + "CAST(extract(epoch from created_at) * 1000 AS BIGINT) as createdAt, "
            + "CAST(extract(epoch from updated_at) * 1000 AS BIGINT) as updatedAt";

    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article WHERE id = #{id}")
    KnowledgeArticle findById(@Param("id") String id);

    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article WHERE domain = #{domain}")
    List<KnowledgeArticle> findByDomain(@Param("domain") String domain);

    /**
     * 按七分类过滤（#147U）：分类闭集由 {@code KnowledgeCategory} 在服务层校验，
     * SQL 只做等值匹配；{@code limit} 已由调用方夹紧，避免无界列表。
     *
     * <p><b>同刻并列的次序（#147BA/#147BP）</b>：排序键必须是
     * {@code (date_trunc('milliseconds', created_at + interval '500 microseconds'), id)} 全序，且与游标页的
     * 排序<b>逐字一致</b>——列上是微秒精度、游标只有毫秒（四舍五入）；首页若按原始微秒排、游标页按
     * 毫秒排，两页次序会错位（活体实测：游标与行差 1ms，末行再次入选，分页原地打转）。
     * {@code + 500µs} 是「四舍五入到毫秒」的等价写法，与种子游标的取整方向一致。</p>
     */
    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article "
            + "WHERE knowledge_category = #{category} "
            + "ORDER BY date_trunc('milliseconds', created_at + interval '500 microseconds') DESC, id DESC LIMIT #{limit}")
    List<KnowledgeArticle> findByCategory(@Param("category") String category, @Param("limit") int limit);

    /**
     * 游标翻页（#147BA）：建在 {@code (date_trunc('milliseconds', created_at), id)} 全序上做 keyset，
     * 不用 {@code OFFSET}（深翻页 O(n) 且并发插入会漂移）。
     *
     * <p><b>精度必须对齐（#147BP 活体修复）</b>：列 {@code created_at} 是微秒精度
     * （如 {@code 19:00:22.510866}），而对外 {@code createdAt} / 游标只承载毫秒，且换算用的是
     * {@code CAST(extract(epoch)*1000 AS BIGINT)}——PG 会<b>四舍五入</b>（.510866 → .511）。
     * 若左侧用纯 {@code date_trunc('milliseconds', created_at)}（<b>向下取整</b> → .510），
     * 两侧就差 1ms，末行判定为「小于游标」而<b>再次入选</b>：第 2 页原地返回第 1 页同一行
     * （活体实测两轮：先撞 timestamptz 时区折算，再撞此取整方向）。故左侧先
     * {@code + interval '500 microseconds'} 再截断＝四舍五入，与游标同口径。</p>
     *
     * <p><b>类型/时区必须与列同域（#147BP 活体修复第三轮）</b>：{@code to_timestamp(epoch)} 返回
     * {@code timestamptz}；即使再 {@code CAST(... AS TIMESTAMP)} 也会按<b>会话时区</b>渲染
     * （dev JDBC 会话为 +08，psql 为 UTC——两边算出的「墙钟」差 8 小时，游标比较因此翻向，实测仍在原地）。
     * 故改用与时区无关的写法：{@code TIMESTAMP 'epoch' + ?/1000 * INTERVAL '1 second'}，
     * 左值列同为 {@code timestamp}，不再经过任何时区换算（已在 +08 与 UTC 两种会话下对拍一致）。</p>
     */
    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article "
            + "WHERE knowledge_category = #{category} "
            + "AND (date_trunc('milliseconds', created_at + interval '500 microseconds'), id) < "
            + "((TIMESTAMP 'epoch' + CAST(#{cursorTs} AS DOUBLE PRECISION) / 1000 * INTERVAL '1 second'), #{cursorId}) "
            + "ORDER BY date_trunc('milliseconds', created_at + interval '500 microseconds') DESC, id DESC LIMIT #{limit}")
    List<KnowledgeArticle> findByCategoryAfter(@Param("category") String category,
                                               @Param("cursorTs") long cursorTsMillis,
                                               @Param("cursorId") String cursorId,
                                               @Param("limit") int limit);

    /**
     * 全文式检索（#147AM 实测修复）：模糊匹配用 ANSI {@code '%' || #{query} || '%'}，
     * <b>不得</b>写 {@code CONCAT('%', #{query}, '%')}。{@code CONCAT} 是 {@code VARIADIC "any"}
     * 函数，PG 无法为其中未声明类型的占位符推断类型 ⇒ 预编译期即
     * {@code ERROR: could not determine data type of parameter $1}（该错误在扩展查询协议下必然复现，
     * 与是否命中数据无关；同类写法只因走简单协议而侥幸通过）。
     */
    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article "
            + "WHERE title ILIKE '%' || #{query} || '%' OR content ILIKE '%' || #{query} || '%' "
            + "LIMIT #{limit}")
    List<KnowledgeArticle> search(@Param("query") String query, @Param("limit") int limit);

    @Select("SELECT " + COLS + " FROM ecos_knowledge.knowledge_article")
    List<KnowledgeArticle> findAll();

    /**
     * 改分类（#147U 编辑腿）：分类与 {@code rule_ref} 同批写——非 Rule 类必须写 NULL，
     * 否则会留下降级前的影子规则引用。返回值 0 表示条目不存在（由服务层转 404）。
     */
    @Update("UPDATE ecos_knowledge.knowledge_article SET knowledge_category = #{category}, "
            + "rule_ref = #{ruleRef}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateCategory(@Param("id") String id, @Param("category") String category,
                       @Param("ruleRef") String ruleRef);

    /**
     * 插入条目。时间列用 {@code CURRENT_TIMESTAMP} 落库（实体侧 epoch 毫秒仅用于出参，
     * 不参与写入），避免 {@code bigint} 打 {@code timestamp} 列的类型错误（#147U 实测修复）。
     */
    @Insert("INSERT INTO ecos_knowledge.knowledge_article "
            + "(id, title, content, domain, category, knowledge_category, rule_ref, source, status, created_at, updated_at) "
            + "VALUES (#{id}, #{title}, #{content}, #{domain, jdbcType=VARCHAR}, #{category, jdbcType=VARCHAR}, "
            + "#{knowledgeCategory, jdbcType=VARCHAR}, #{ruleRef, jdbcType=VARCHAR}, "
            + "#{sourceType, jdbcType=VARCHAR}, #{status}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int insert(KnowledgeArticle article);

    @Select("SELECT COUNT(*) FROM ecos_knowledge.knowledge_article")
    long count();
}
