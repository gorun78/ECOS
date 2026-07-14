package com.chinacreator.gzcm.runtime.core.datasource.dao.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;

/**
 * Simple in-memory DAO used only for compilation and basic testing.
 */
public class DataSourceDaoImpl {

    private final Map<String, DataSourceEntity> store = new HashMap<>();

    public String save(DataSourceEntity entity) {
        String id = entity.getDatasourceId();
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            entity.setDatasourceId(id);
        }
        store.put(id, entity);
        return id;
    }

    public void update(DataSourceEntity entity) {
        store.put(entity.getDatasourceId(), entity);
    }

    public void delete(String id) {
        store.remove(id);
    }

    public DataSourceEntity findById(String id) {
        return store.get(id);
    }

    public List<DataSourceEntity> findAll() {
        return new ArrayList<>(store.values());
    }
}

