package com.chinacreator.gzcm.engine.ontology.glossary;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ontology.dao.GlossaryRelationDao;

/**
 * 词条关系边仓库 — 对应 ecos_glossary_term_relation 表。
 *
 * <p>B3c 数据访问收口：本仓库不再直接持有 {@code JdbcTemplate}，
 * 全部 JDBC 存取下放到 {@link GlossaryRelationDao}（继承 {@code JdbcTemplateDaoSupport}）。
 * 本类保留原公开 API 作为薄委托（facade）。
 */
@Repository
public class GlossaryRelationRepository {

    private final GlossaryRelationDao dao;

    public GlossaryRelationRepository(GlossaryRelationDao dao) {
        this.dao = dao;
    }

    /**
     * 按条件查询关系边。
     *
     * @param fromTermId   起点词条 id，可空
     * @param toTermId     终点词条 id，可空
     * @param relationType 边类型，可空
     * @param termId       任一端词条 id（起点或终点命中），可空
     */
    public List<GlossaryRelationEntity> findAll(Long fromTermId, Long toTermId,
                                                String relationType, Long termId) {
        return dao.findAll(fromTermId, toTermId, relationType, termId);
    }

    /** 按 id 查关系边。 */
    public Optional<GlossaryRelationEntity> findById(Long id) {
        return dao.findById(id);
    }

    /**
     * 查询任一端落在给定词条集合内的全部边（图谱逐层展开用）。
     *
     * @param termIds 词条 id 集合，空集合返回空列表
     */
    public List<GlossaryRelationEntity> findEdgesByTermIds(List<Long> termIds) {
        return dao.findEdgesByTermIds(termIds);
    }

    /** 判定同向同类型边是否已存在（幂等新增用）。 */
    public boolean exists(Long fromTermId, Long toTermId, String relationType) {
        return dao.exists(fromTermId, toTermId, relationType);
    }

    /**
     * 新增关系边。
     *
     * <p>以 {@code RETURNING id} 回填自增主键到入参实体（创建后回读与前端定位依赖该 id）。
     */
    public int insert(GlossaryRelationEntity entity) {
        return dao.insert(entity);
    }

    /** 删除关系边。 */
    public int deleteById(Long id) {
        return dao.deleteById(id);
    }

    /** 关系边总数。 */
    public long count() {
        return dao.count();
    }
}
