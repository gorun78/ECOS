package com.chinacreator.gzcm.engine.ontology.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.dao.OntologyVersionDao;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库（门面）— 版本 CRUD（ecos_ontology_version 表）。
 *
 * <p>B3c 数据访问收口：JDBC 实现下沉到 {@link OntologyVersionDao}，
 * 本仓库保留 API 委托，供 Service 层 &amp; 既有代码（Wave31 / OntologyVersionGitArchive /
 * ProposalLifecycle 等）transparent 使用。</p>
 * <p>方法命名保留 {@code findByOntology} / {@code findById} / {@code findPreviousVersion}，
 * 与 HistoryNotInDbTest 的静态断言对齐。</p>
 */
@Repository
public class OntologyVersionRepository {

    private final OntologyVersionDao dao;

    public OntologyVersionRepository(OntologyVersionDao dao) {
        this.dao = dao;
    }

    public List<OntologyVersion> findByOntology(String ontologyId) {
        return dao.findByOntology(ontologyId);
    }

    public Optional<OntologyVersion> findById(String id) {
        return dao.findById(id);
    }

    public Optional<OntologyVersion> findByOntologyAndVersion(String ontologyId, String versionNo) {
        return dao.findByOntologyAndVersion(ontologyId, versionNo);
    }

    public Optional<OntologyVersion> findLatestPublished(String ontologyId) {
        return dao.findLatestPublished(ontologyId);
    }

    public int insert(OntologyVersion version) {
        return dao.insert(version);
    }

    public int updateStatus(String id, String status) {
        return dao.updateStatus(id, status);
    }

    /** F03-15 (W83)：Git 归档成功/入补偿失败时回填 git_ref（V170.1 additive 补列）。 */
    public int setGitRef(String id, String gitRef) {
        return dao.setGitRef(id, gitRef);
    }

    /** 逻辑删除 version (T17)：原 DELETE → UPDATE SET is_deleted=1, status='ARCHIVED'。 */
    public int delete(String id) {
        return dao.delete(id);
    }

    public List<OntologyVersion> findAll() {
        return dao.findAll();
    }

    /**
     * 生成下一个版本主键（{@code ver} + 数字后缀）。
     * 最大数字后缀自增 vs 库内既有 {@code ver5001/ver5002}，避免进程重启撞主键。
     */
    public String nextId() {
        return dao.nextId();
    }

    public Optional<OntologyVersion> findPreviousVersion(String ontologyId, LocalDateTime beforeTime) {
        return dao.findPreviousVersion(ontologyId, beforeTime);
    }
}
