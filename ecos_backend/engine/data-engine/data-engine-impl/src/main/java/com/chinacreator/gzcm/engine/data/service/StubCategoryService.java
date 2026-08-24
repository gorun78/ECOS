package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.CategoryService;
import com.chinacreator.gzcm.common.data.model.DataCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CategoryService 启动兜底实现（PMO-E3）。
 * data-engine-api 的 CategoryService 接口无实现类，此 stub 让 Gateway 能启动。
 */
@Service
public class StubCategoryService implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(StubCategoryService.class);

    @Override
    public DataCategory create(DataCategory category) {
        log.warn("StubCategoryService.create: stub no-op"); return null;
    }
    @Override
    public DataCategory update(DataCategory category) {
        log.warn("StubCategoryService.update: stub no-op"); return null;
    }
    @Override
    public DataCategory getById(String categoryId) {
        log.warn("StubCategoryService.getById: stub no-op"); return null;
    }
    @Override
    public List<DataCategory> getTree() {
        log.warn("StubCategoryService.getTree: stub no-op"); return List.of();
    }
    @Override
    public List<DataCategory> getChildren(String parentId) {
        log.warn("StubCategoryService.getChildren: stub no-op"); return List.of();
    }
    @Override
    public void remove(String categoryId) {
        log.warn("StubCategoryService.remove: stub no-op");
    }
    @Override
    public List<DataCategory> getCategoryStats() {
        log.warn("StubCategoryService.getCategoryStats: stub no-op"); return List.of();
    }
}
