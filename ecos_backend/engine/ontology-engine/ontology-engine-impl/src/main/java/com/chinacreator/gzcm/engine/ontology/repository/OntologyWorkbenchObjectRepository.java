package com.chinacreator.gzcm.engine.ontology.repository;

import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.dao.OntologyWorkbenchObjectDao;
import com.chinacreator.gzcm.engine.ontology.model.OntologyWorkbenchObject;
import org.springframework.stereotype.Repository;

/**
 * 本体工作台 5 类视图对象统一持久化（Wave B-5 T7）。
 *
 * <p>表 {@code ecos_ontology_workbench_object}（V121__ecos_ontology_workbench_view.sql），
 * 按 object_type 区分 action / interface / shared_property / function / dataset，
 * definition_json 承载各类差异字段。
 *
 * <p>风格对齐 {@link OntologyRepository}：显式列名查询（禁 SELECT *）、
 * 按需 COALESCE 更新、逻辑删除 (T17 语义: is_deleted=1 + status='ARCHIVED')。
 *
 * <p>R2/B3c：JDBC 全量下沉至 {@link OntologyWorkbenchObjectDao}，本类仅保留公开 API 透传。
 */
@Repository
public class OntologyWorkbenchObjectRepository {

    private final OntologyWorkbenchObjectDao dao;

    public OntologyWorkbenchObjectRepository(OntologyWorkbenchObjectDao dao) {
        this.dao = dao;
    }

    /** 按本体 + 视图类型列出未删除对象（type 为空/blank 时返回该本体全部类型）。 */
    public List<OntologyWorkbenchObject> findObjectsByOntology(String ontologyId, String objectType) {
        return dao.findObjectsByOntology(ontologyId, objectType);
    }

    /** 按 id + 本体列出未删除对象（fileId 保存时校验归属用语）。 */
    public Optional<OntologyWorkbenchObject> findObjectById(String ontologyId, String id) {
        return dao.findObjectById(ontologyId, id);
    }

    /** 新增视图对象（create_by/update_by 落 'system'，与 T17 一致）。 */
    public int insertObject(OntologyWorkbenchObject o) {
        return dao.insertObject(o);
    }

    /**
     * 按需更新（COALESCE 语义: null 字段不覆盖有效数据, 对齐 OntologyRepository.updateEntity）。
     * object_type 参与查询语义, 不开放变更。
     */
    public int updateObject(String ontologyId, String id, String code, String name,
                            String description, String definitionJson, String status) {
        return dao.updateObject(ontologyId, id, code, name, description, definitionJson, status);
    }

    /** 逻辑删除 (T17 语义): 按 id 单行, 限本体归属。 */
    public int deleteObject(String ontologyId, String id) {
        return dao.deleteObject(ontologyId, id);
    }
}
