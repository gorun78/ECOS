package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.common.data.model.CatalogItem;
import com.chinacreator.gzcm.engine.data.CatalogService;
import com.chinacreator.gzcm.engine.data.dao.CatalogDashboardDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CatalogDashboardService {

    private static final Logger log = LoggerFactory.getLogger(CatalogDashboardService.class);

    private final CatalogService catalogService;
    private final CatalogDashboardDao dashboardDao;

    public CatalogDashboardService(CatalogService catalogService, CatalogDashboardDao dashboardDao) {
        this.catalogService = catalogService;
        this.dashboardDao = dashboardDao;
    }

    public Map<String, Object> getDashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalResources", countResources());
        stats.put("totalFields", countFields());
        stats.put("resourceTypes", countByType());
        return stats;
    }

    private long countResources() {
        try {
            Long count = dashboardDao.countResources();
            return count != null ? count : catalogService.count();
        } catch (Exception e) {
            return catalogService.count();
        }
    }

    private long countFields() {
        try {
            Long count = dashboardDao.countFields();
            return count != null ? count : countFieldsFromService();
        } catch (Exception e) {
            return countFieldsFromService();
        }
    }

    private long countFieldsFromService() {
        long total = 0;
        int page = 1;
        int pageSize = 100;
        List<CatalogItem> batch;
        do {
            CatalogQueryDTO q = new CatalogQueryDTO();
            q.setPage(page++);
            q.setPageSize(pageSize);
            batch = catalogService.search(q);
            for (CatalogItem item : batch) {
                total += item.getFieldCount() != null ? item.getFieldCount() : 0;
            }
        } while (batch.size() == pageSize);
        return total;
    }

    private Map<String, Long> countByType() {
        try {
            return dashboardDao.countByType();
        } catch (Exception e) {
            return countByTypeFromService();
        }
    }

    private Map<String, Long> countByTypeFromService() {
        Map<String, Long> typeCounts = new HashMap<>();
        int page = 1;
        int pageSize = 100;
        List<CatalogItem> batch;
        do {
            CatalogQueryDTO q = new CatalogQueryDTO();
            q.setPage(page++);
            q.setPageSize(pageSize);
            batch = catalogService.search(q);
            for (CatalogItem item : batch) {
                String type = item.getResourceType() != null ? item.getResourceType() : "OTHER";
                typeCounts.merge(type, 1L, Long::sum);
            }
        } while (batch.size() == pageSize);
        return typeCounts;
    }
}
