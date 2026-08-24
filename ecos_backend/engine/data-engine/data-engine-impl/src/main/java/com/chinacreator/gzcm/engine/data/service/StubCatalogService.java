package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.CatalogService;
import com.chinacreator.gzcm.common.data.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.common.data.model.CatalogItem;
import com.chinacreator.gzcm.common.data.model.DataResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CatalogService 启动兜底实现（PMO-E3）。
 * data-engine-api 的 CatalogService 接口无实现类，此 stub 让 Gateway 能启动。
 */
@Service
public class StubCatalogService implements CatalogService {

    private static final Logger log = LoggerFactory.getLogger(StubCatalogService.class);

    @Override
    public CatalogItem register(DataResource resource) {
        log.warn("StubCatalogService.register: stub no-op"); return null;
    }
    @Override
    public List<CatalogItem> search(CatalogQueryDTO query) {
        log.warn("StubCatalogService.search: stub no-op"); return List.of();
    }
    @Override
    public CatalogItem getById(String catalogId) {
        log.warn("StubCatalogService.getById: stub no-op"); return null;
    }
    @Override
    public CatalogItem getByResourceId(String resourceId) {
        log.warn("StubCatalogService.getByResourceId: stub no-op"); return null;
    }
    @Override
    public List<CatalogItem> listByOrg(String orgId) {
        log.warn("StubCatalogService.listByOrg: stub no-op"); return List.of();
    }
    @Override
    public long count() {
        log.warn("StubCatalogService.count: stub no-op"); return 0;
    }
    @Override
    public List<CatalogItem> searchByFieldName(String fieldName, int page, int pageSize) {
        log.warn("StubCatalogService.searchByFieldName: stub no-op"); return List.of();
    }
    @Override
    public long countByFieldName(String fieldName) {
        log.warn("StubCatalogService.countByFieldName: stub no-op"); return 0;
    }
    @Override
    public CatalogItem update(CatalogItem item) {
        log.warn("StubCatalogService.update: stub no-op"); return null;
    }
    @Override
    public void remove(String catalogId) {
        log.warn("StubCatalogService.remove: stub no-op");
    }
}
