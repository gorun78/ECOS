package com.chinacreator.gzcm.engine.ontology.glossary;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ontology.dao.GlossaryDao;

/**
 * 词条（Wiki）仓库 — 对应 ecos_glossary_term 表。
 *
 * <p>B3c 数据访问收口：本仓库不再直接持有 {@code JdbcTemplate}，
 * 全部 JDBC 存取下放到 {@link GlossaryDao}（继承 {@code JdbcTemplateDaoSupport}）。
 * 本类保留原公开 API 作为薄委托（facade），保证服务层与既有测试兼容。
 *
 * <p>多租户 RLS（PMO-30 P1-1）逻辑随 SQL 一并搬入 {@link GlossaryDao}：
 * SELECT/UPDATE/DELETE 加 {@code (tenant_id = ? OR tenant_id IS NULL)}，
 * INSERT 从 {@code TenantContextHolder} 打 tenantId 标签（NULL 兼容旧数据）。
 */
@Repository
public class GlossaryRepository {

    private final GlossaryDao dao;

    public GlossaryRepository(GlossaryDao dao) {
        this.dao = dao;
    }

    /**
     * 按条件查询词条列表。
     *
     * @param domain       领域字典 code，可空
     * @param status       状态，可空
     * @param termType     词条分类，可空
     * @param objectTypeId 关联本体实体主键，可空
     * @param keyword      名称 / 编码 / 别名 模糊匹配关键字，可空
     */
    public List<GlossaryEntity> findAll(String domain, String status,
                                        String termType, String objectTypeId, String keyword) {
        return dao.findAll(domain, status, termType, objectTypeId, keyword);
    }

    /** 按主键查词条。 */
    public Optional<GlossaryEntity> findById(Long id) {
        return dao.findById(id);
    }

    /** 按编码查词条（编码为演示 / 导入场景的稳定业务键）。 */
    public Optional<GlossaryEntity> findByCode(String code) {
        return dao.findByCode(code);
    }

    /** 按主键集合批量查词条（图谱组装用）。 */
    public List<GlossaryEntity> findAllByIds(List<Long> ids) {
        return dao.findAllByIds(ids);
    }

    /** 新增词条（写入时打当前租户标签，无上下文则 NULL；RETURNING id 回填主键）。 */
    public int insert(GlossaryEntity entity) {
        return dao.insert(entity);
    }

    /** 更新词条（各字段 null 表不动，数组字段亦然；不修改 tenant_id）。 */
    public int update(GlossaryEntity entity) {
        return dao.update(entity);
    }

    /** 物理删除词条（限本租户行或共享行；关联关系边由外键 ON DELETE CASCADE 一并清理）。 */
    public int deleteById(Long id) {
        return dao.deleteById(id);
    }

    /**
     * 更新词条 ↔ 本体实体 绑定（T1 本体消费词条）。
     *
     * <p>{@code objectTypeId} 为 null 即解绑（同时撤下主术语标记）。
     */
    public int updateBinding(Long id, String objectTypeId, boolean primary) {
        return dao.updateBinding(id, objectTypeId, primary);
    }

    /**
     * 撤下该实体下（除 {@code exceptId} 外）的主术语标记。
     *
     * <p>刻意不加租户条件：{@code uniq_glossary_term_primary} 是全局部分唯一索引，
     * 主术语归属属语义层事实（跨租户共享本体）。
     */
    public int clearPrimaryForEntity(String objectTypeId, Long exceptId) {
        return dao.clearPrimaryForEntity(objectTypeId, exceptId);
    }

    /** 词条总数。 */
    public long count() {
        return dao.count();
    }
}
