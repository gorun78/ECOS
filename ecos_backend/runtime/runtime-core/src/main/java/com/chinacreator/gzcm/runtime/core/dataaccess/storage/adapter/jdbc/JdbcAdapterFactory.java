package com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.jdbc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.dataaccess.storage.IStorageAdapter;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.IStorageAdapterFactory;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.enums.StorageType;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;

public class JdbcAdapterFactory implements IStorageAdapterFactory {

    private final Map<StorageType, Class<? extends IStorageAdapter>> registry = new HashMap<>();

    public JdbcAdapterFactory() {
        registerAdapter(StorageType.MYSQL, MysqlAdapter.class);
        registerAdapter(StorageType.POSTGRESQL, PostgresqlAdapter.class);
        registerAdapter(StorageType.ORACLE, OracleAdapter.class);
        registerAdapter(StorageType.SQLSERVER, SqlServerAdapter.class);
        registerAdapter(StorageType.CLICKHOUSE, com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.olap.ClickHouseAdapter.class);
        registerAdapter(StorageType.DORIS, com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.olap.DorisAdapter.class);
        registerAdapter(StorageType.STARROCKS, com.chinacreator.gzcm.runtime.core.dataaccess.storage.adapter.olap.StarRocksAdapter.class);
    }

    @Override
    public IStorageAdapter createAdapter(StorageType storageType, StorageConfig config) throws Exception {
        Class<? extends IStorageAdapter> clazz = registry.get(storageType);
        if (clazz == null) {
            throw new IllegalArgumentException("Unsupported storage type: " + storageType);
        }
        IStorageAdapter adapter = clazz.getDeclaredConstructor().newInstance();
        adapter.connect(config);
        return adapter;
    }

    @Override
    public IStorageAdapter createAdapter(String storageType, String storageConfig) throws Exception {
        StorageType type = StorageType.fromString(storageType);
        StorageConfig config = new StorageConfig();
        config.setConnectionString(storageConfig);
        return createAdapter(type, config);
    }

    @Override
    public IStorageAdapter getAdapter(StorageType storageType, StorageConfig config) throws Exception {
        return createAdapter(storageType, config);
    }

    @Override
    public IStorageAdapter getAdapter(String storageType, String storageConfig) throws Exception {
        return createAdapter(storageType, storageConfig);
    }

    @Override
    public List<StorageType> getSupportedTypes() {
        return new ArrayList<>(registry.keySet());
    }

    @Override
    public boolean supports(StorageType storageType) {
        return registry.containsKey(storageType);
    }

    @Override
    public void registerAdapter(StorageType storageType, Class<? extends IStorageAdapter> adapterClass) {
        registry.put(storageType, adapterClass);
    }
}

