package com.chinacreator.gzcm.engine.data.datasource.entity;

import java.sql.Timestamp;

/**
 * 数据源实体类
 * 支持数据库数据源、文件数据源、服务数据源三种类型
 * 
 * @author CDRC Runtime Team
 */
public class DataSourceEntity {
    
    private String datasourceId;
    private String datasourceName;
    private String datasourceType;
    private String orgId;
    private String nodeId;
    private String description;
    private String connectionConfig;
    private String status;
    private String isDefault;
    private Timestamp lastTestTime;
    private String lastTestResult;
    private String lastTestMessage;
    private String createBy;
    private Timestamp createTime;
    private String updateBy;
    private Timestamp updateTime;
    private String tags;
    private String remark;
    /** PMO-37 元数据获取策略配置（JSONB），如 {"strategy":"ON_SAVE","includeRowCount":true,"countMethod":"ESTIMATE"} */
    private String metadataConfig;

    /** PMO-46: 密码密文（AES 加密后 Base64），读取自 td_datasource.password_enc。 */
    private String passwordEncrypted;

    /**
     * F02-01 数据域凭证治理（W52）: 读路径是否持有可信凭证（true|false）。
     * <p>由 Service 出口（{@code maskProtected}）按 {@link #passwordEncrypted} 是否非空派生，
     * 与密文字段同步 null-out —— 读路径<b>永不回显</b>明文/密文，仅暴露该布尔标志，
     * 替代前端此前依赖 {@code password} 键（或已剥离的密文键）猜测"是否配置了密码"的隐式口径。</p>
     * <p>transient 字段随 JSON 序列化作为新增出参（只增不改，既有消费者不受影响）。</p>
     */
    private boolean credentialPresent;

    private Timestamp lastCollectTime;
    
    public String getDatasourceId() {
        return datasourceId;
    }
    
    public void setDatasourceId(String datasourceId) {
        this.datasourceId = datasourceId;
    }
    
    public String getDatasourceName() {
        return datasourceName;
    }
    
    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }
    
    public String getDatasourceType() {
        return datasourceType;
    }
    
    public void setDatasourceType(String datasourceType) {
        this.datasourceType = datasourceType;
    }
    
    public String getOrgId() {
        return orgId;
    }
    
    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getConnectionConfig() {
        return connectionConfig;
    }
    
    public void setConnectionConfig(String connectionConfig) {
        this.connectionConfig = connectionConfig;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getIsDefault() {
        return isDefault;
    }
    
    public void setIsDefault(String isDefault) {
        this.isDefault = isDefault;
    }
    
    public Timestamp getLastTestTime() {
        return lastTestTime;
    }
    
    public void setLastTestTime(Timestamp lastTestTime) {
        this.lastTestTime = lastTestTime;
    }
    
    public String getLastTestResult() {
        return lastTestResult;
    }
    
    public void setLastTestResult(String lastTestResult) {
        this.lastTestResult = lastTestResult;
    }
    
    public String getLastTestMessage() {
        return lastTestMessage;
    }
    
    public void setLastTestMessage(String lastTestMessage) {
        this.lastTestMessage = lastTestMessage;
    }
    
    public String getCreateBy() {
        return createBy;
    }
    
    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }
    
    public Timestamp getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }
    
    public String getUpdateBy() {
        return updateBy;
    }
    
    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }
    
    public Timestamp getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public String getMetadataConfig() {
        return metadataConfig;
    }
    
    public void setMetadataConfig(String metadataConfig) {
        this.metadataConfig = metadataConfig;
    }
    
    public Timestamp getLastCollectTime() {
        return lastCollectTime;
    }

    public void setLastCollectTime(Timestamp lastCollectTime) {
        this.lastCollectTime = lastCollectTime;
    }

    /** PMO-46: 密码密文 getter */
    public String getPasswordEncrypted() {
        return passwordEncrypted;
    }

    /** PMO-46: 密码密文 setter（Service 层读取 password_enc 列时填充） */
    public void setPasswordEncrypted(String passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
    }

    /** F02-01: 是否持有可信凭证（读路径出参，替代明文/密文回显） */
    public boolean isCredentialPresent() {
        return credentialPresent;
    }

    /** F02-01: 是否持有可信凭证 setter（Service maskProtected 按 passwordEncrypted 是否非空派生） */
    public void setCredentialPresent(boolean credentialPresent) {
        this.credentialPresent = credentialPresent;
    }

    public static class DataSourceType {
        public static final String DATABASE = "DATABASE";
        public static final String FILE = "FILE";
        public static final String SERVICE = "SERVICE";
    }
    
    public static class DataSourceStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String INACTIVE = "INACTIVE";
        public static final String TESTING = "TESTING";
        public static final String ERROR = "ERROR";
    }
}
