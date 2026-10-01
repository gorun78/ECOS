package com.chinacreator.gzcm.engine.ontology.repository;

import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.dao.OntologyDomainDao;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import org.springframework.stereotype.Repository;

/**
 * 领域 CRUD 仓库（ecos_domain 表）。
 *
 * <p>PMO-30 P1-1 多租户 RLS：查询带 tenant_id 过滤（兼容 NULL 行共享可见），
 * 写操作自动打 tenantId 标签。
 *
 * <p>租户隔离策略（在 DAO 内实现）：
 * <ul>
 *   <li>SELECT 自动加 {@code AND (tenant_id = ? OR tenant_id IS NULL)} — NULL 行共享可见</li>
 *   <li>INSERT 从 {@code TenantContextHolder} 打 tenantId 标签（NULL 兼容旧数据)</li>
 *   <li>UPDATE 不修改 tenant_id（数据归属不漂移）</li>
 *   <li>DELETE 仅删除当前租户的行（NULL 行不删）</li>
 * </ul>
 *
 * <p>R2/B3c：JDBC 全量下沉至 {@link OntologyDomainDao}，本类仅保留公开 API 透传。
 */
@Repository
public class OntologyDomainRepository {

    private final OntologyDomainDao dao;

    public OntologyDomainRepository(OntologyDomainDao dao) {
        this.dao = dao;
    }

    public List<OntologyDomain> findAll() {
        return dao.findAll();
    }

    public Optional<OntologyDomain> findByCode(String code) {
        return dao.findByCode(code);
    }

    public Optional<OntologyDomain> findById(String id) {
        return dao.findById(id);
    }

    public boolean existsByCode(String code) {
        return dao.existsByCode(code);
    }

    public int insert(OntologyDomain domain) {
        return dao.insert(domain);
    }

    public int update(String id, String code, String name, String owner, String description, String status) {
        return dao.update(id, code, name, owner, description, status);
    }

    public int delete(String id) {
        return dao.delete(id);
    }

    public int countEntitiesByDomain(String domainCode) {
        return dao.countEntitiesByDomain(domainCode);
    }

    /**
     * 搜索域（按 keyword 模糊匹配 name/code/description）。
     * <p>PMO E8 端点 {@code GET /api/v1/ecos/domains/search} 后端支撑。</p>
     *
     * @param keyword 搜索关键字（可为空，返回当前租户全部域）
     * @param limit   最大返回条数（默认 50）
     * @return 匹配的 Domain 列表
     */
    public List<OntologyDomain> searchDomains(String keyword, int limit) {
        return dao.searchDomains(keyword, limit);
    }
}
