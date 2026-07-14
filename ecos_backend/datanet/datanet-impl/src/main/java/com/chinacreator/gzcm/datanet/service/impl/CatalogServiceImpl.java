package com.chinacreator.gzcm.datanet.service.impl;

import com.chinacreator.gzcm.datanet.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.datanet.model.CatalogItem;
import com.chinacreator.gzcm.datanet.model.DataResource;
import com.chinacreator.gzcm.datanet.repository.CatalogItemRepository;
import com.chinacreator.gzcm.datanet.repository.DataResourceRepository;
import com.chinacreator.gzcm.datanet.service.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 目录服务实现 — 基于 MyBatis + MySQL 持久化。
 */
@Service
public class CatalogServiceImpl implements CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogServiceImpl.class);

    private final CatalogItemRepository catalogRepo;
    private final DataResourceRepository resourceRepo;

    public CatalogServiceImpl(CatalogItemRepository catalogRepo,
                               DataResourceRepository resourceRepo) {
        this.catalogRepo = catalogRepo;
        this.resourceRepo = resourceRepo;
    }

    @Override
    @Transactional
    public CatalogItem register(DataResource resource) {
        if (resource.getResourceId() == null) {
            resource.setResourceId(UUID.randomUUID().toString().replace("-", ""));
        }
        resource.setCreateTime(LocalDateTime.now());
        resource.setUpdateTime(LocalDateTime.now());
        resourceRepo.insert(resource);

        String catalogId = UUID.randomUUID().toString().replace("-", "");
        CatalogItem item = new CatalogItem();
        item.setCatalogId(catalogId);
        item.setResourceId(resource.getResourceId());
        item.setResourceName(resource.getResourceName());
        item.setResourceType(resource.getResourceType());
        item.setOrgName(resource.getOrgName());
        item.setDescription(resource.getDescription());
        item.setTags(resource.getTags());
        item.setAccessType(detectAccessType(resource));
        item.setDataFormat(detectDataFormat(resource));
        item.setFieldCount(resource.getFieldCount());
        item.setRecordCount(resource.getRecordCount());
        item.setLastUpdated(LocalDateTime.now());
        item.setStatus("ACTIVE");

        catalogRepo.insert(item);
        log.info("Registered catalog item: {} (resource={})", resource.getResourceName(), resource.getResourceId());
        return item;
    }

    @Override
    public List<CatalogItem> search(CatalogQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getPageSize();
        return catalogRepo.search(query.getKeyword(), query.getResourceType(),
                query.getCategoryPath(), offset, query.getPageSize());
    }

    @Override
    public CatalogItem getById(String catalogId) {
        return catalogRepo.findById(catalogId);
    }

    @Override
    public CatalogItem getByResourceId(String resourceId) {
        return catalogRepo.findByResourceId(resourceId);
    }

    @Override
    public List<CatalogItem> listByOrg(String orgName) {
        return catalogRepo.findByOrg(orgName);
    }

    @Override
    public long count() {
        return catalogRepo.countAll();
    }

    @Override
    public List<CatalogItem> searchByFieldName(String fieldName, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return catalogRepo.searchByFieldName(fieldName, offset, pageSize);
    }

    @Override
    public long countByFieldName(String fieldName) {
        return catalogRepo.countByFieldName(fieldName);
    }

    @Override
    @Transactional
    public CatalogItem update(CatalogItem item) {
        item.setLastUpdated(LocalDateTime.now());
        catalogRepo.update(item);
        return item;
    }

    @Override
    @Transactional
    public void remove(String catalogId) {
        CatalogItem item = catalogRepo.findById(catalogId);
        if (item != null) {
            catalogRepo.deleteById(catalogId);
            resourceRepo.deleteById(item.getResourceId());
        }
        log.info("Removed catalog item: {}", catalogId);
    }

    private String detectAccessType(DataResource resource) {
        return switch (resource.getResourceType()) {
            case "TABLE", "VIEW" -> "JDBC";
            case "API" -> "REST";
            case "FILE" -> "FILE";
            default -> "JDBC";
        };
    }

    private String detectDataFormat(DataResource resource) {
        return switch (resource.getResourceType()) {
            case "TABLE", "VIEW" -> "TABLE";
            case "API" -> "JSON";
            case "FILE" -> "FILE";
            default -> "TABLE";
        };
    }
}
