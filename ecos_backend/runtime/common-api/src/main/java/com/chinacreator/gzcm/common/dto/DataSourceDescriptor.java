package com.chinacreator.gzcm.common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 数据源描述 — 由 Datanet 提供，Bus-Zhi 和 Dc-Cheng 消费。
 * <p>
 * 这是三个模块之间的共享契约，不包含任何模块特有的业务逻辑。
 */
public class DataSourceDescriptor implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ── 数据源类型 ──────────────────────────────────

    public enum SourceType {
        MYSQL,
        POSTGRESQL,
        ORACLE,
        SQL_SERVER,
        KAFKA,
        REST_API,
        FILE_CSV,
        FILE_EXCEL,
        FILE_JSON,
        HDFS,
        HIVE,
        OTHER
    }

    // ── 数据源状态 ──────────────────────────────────

    public enum SourceStatus {
        /** 已注册，未测试连接 */
        REGISTERED,
        /** 连接正常 */
        CONNECTED,
        /** 连接失败 */
        DISCONNECTED,
        /** 已停用 */
        DISABLED
    }

    // ── 字段 ────────────────────────────────────────

    /** 数据源唯一标识（Datanet 分配） */
    private String id;

    /** 数据源名称 */
    private String name;

    /** 数据源类型 */
    private SourceType type;

    /** 连接状态 */
    private SourceStatus status;

    /** 描述信息 */
    private String description;

    /** 连接属性（host/port/dbName 等，不含密码） */
    private Map<String, String> connectionProperties;

    /** 所属组织 */
    private String orgId;

    /** 创建时间 (ISO-8601) */
    private String createdAt;

    // ── getter/setter ───────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SourceType getType() { return type; }
    public void setType(SourceType type) { this.type = type; }

    public SourceStatus getStatus() { return status; }
    public void setStatus(SourceStatus status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getConnectionProperties() { return connectionProperties; }
    public void setConnectionProperties(Map<String, String> connectionProperties) {
        this.connectionProperties = connectionProperties;
    }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
