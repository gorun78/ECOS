package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.common.data.model.CatalogItem;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.CatalogService;
import com.chinacreator.gzcm.engine.data.dao.CatalogDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * CatalogService JdbcTemplate 实现（PMO-E3: 替换启动兜底 stub）。
 * 表: td_catalog_item, td_data_resource
 * 原始 SQL 由 {@link CatalogDao} 执行。
 */
@Service
public class CatalogServiceImpl implements CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogServiceImpl.class);

    private final CatalogDao catalogDao;

    public CatalogServiceImpl(CatalogDao catalogDao) {
        this.catalogDao = catalogDao;
    }

    @Override
    public CatalogItem register(DataResource resource) {
        String catalogId = UUID.randomUUID().toString().replace("-", "");
        catalogDao.register(resource, catalogId);
        log.info("Registered catalog item: id={}, resource={}", catalogId, resource.getResourceName());
        return catalogDao.getById(catalogId);
    }

    @Override
    public List<CatalogItem> search(CatalogQueryDTO query) {
        return catalogDao.search(query);
    }

    @Override
    public CatalogItem getById(String catalogId) {
        return catalogDao.getById(catalogId);
    }

    @Override
    public CatalogItem getByResourceId(String resourceId) {
        return catalogDao.getByResourceId(resourceId);
    }

    @Override
    public List<CatalogItem> listByOrg(String orgId) {
        return catalogDao.listByOrg(orgId);
    }

    @Override
    public long count() {
        return catalogDao.count();
    }

    @Override
    public List<CatalogItem> searchByFieldName(String fieldName, int page, int pageSize) {
        return catalogDao.searchByFieldName(fieldName, page, pageSize);
    }

    @Override
    public long countByFieldName(String fieldName) {
        return catalogDao.countByFieldName(fieldName);
    }

    @Override
    public CatalogItem update(CatalogItem item) {
        catalogDao.update(item);
        return catalogDao.getById(item.getCatalogId());
    }

    @Override
    public void remove(String catalogId) {
        catalogDao.remove(catalogId);
        log.info("Removed catalog item: {}", catalogId);
    }
}
