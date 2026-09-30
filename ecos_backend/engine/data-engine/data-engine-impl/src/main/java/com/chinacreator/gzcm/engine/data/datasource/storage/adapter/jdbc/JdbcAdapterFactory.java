package com.chinacreator.gzcm.engine.data.datasource.storage.adapter.jdbc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.datasource.storage.IStorageAdapter;
import com.chinacreator.gzcm.engine.data.datasource.storage.IStorageAdapterFactory;
import com.chinacreator.gzcm.engine.data.datasource.storage.enums.StorageType;
import com.chinacreator.gzcm.engine.data.datasource.storage.model.StorageConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JDBC 存储适配器工厂。
 *
 * <p>F02-13（详细设计-02）：适配器建连不再自持 {@code DriverManager}，
 * 而是经 Spring 侧注入的<b>建连桥</b>（runtime-access {@code JdbcConnector}）完成。
 * 桥缺失时适配器仍可构造、创建成功但 {@code connect} 直接拒绝（fail-fast，无隐式回退）。</p>
 */
public class JdbcAdapterFactory implements IStorageAdapterFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<StorageType, Class<? extends IStorageAdapter>> registry = new HashMap<>();

    /** F02-13 建连桥（runtime-access JdbcConnector 提供；Spring 侧注入）。 */
    private AdapterConnectionBridge connectionBridge;

    public JdbcAdapterFactory() {
        registerAdapter(StorageType.MYSQL, MysqlAdapter.class);
        registerAdapter(StorageType.POSTGRESQL, PostgresqlAdapter.class);
        registerAdapter(StorageType.ORACLE, OracleAdapter.class);
        registerAdapter(StorageType.SQLSERVER, SqlServerAdapter.class);
        registerAdapter(StorageType.CLICKHOUSE, com.chinacreator.gzcm.engine.data.datasource.storage.adapter.olap.ClickHouseAdapter.class);
        registerAdapter(StorageType.DORIS, com.chinacreator.gzcm.engine.data.datasource.storage.adapter.olap.DorisAdapter.class);
        registerAdapter(StorageType.STARROCKS, com.chinacreator.gzcm.engine.data.datasource.storage.adapter.olap.StarRocksAdapter.class);
    }

    /** 注入建连桥（DataSourceServiceImpl 从 Spring 管理的 JdbcConnector 绑定）。 */
    public void setConnectionBridge(AdapterConnectionBridge bridge) {
        this.connectionBridge = bridge;
    }

    public boolean hasConnectionBridge() {
        return connectionBridge != null;
    }

    @Override
    public IStorageAdapter createAdapter(StorageType storageType, StorageConfig config) throws Exception {
        Class<? extends IStorageAdapter> clazz = registry.get(storageType);
        if (clazz == null) {
            throw new IllegalArgumentException("Unsupported storage type: " + storageType);
        }
        IStorageAdapter adapter = clazz.getDeclaredConstructor().newInstance();
        if (adapter instanceof BaseJdbcAdapter base) {
            base.setConnectionBridge(connectionBridge);
        }
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


