package com.chinacreator.gzcm.runtime.core.task.model;

import java.util.Date;
import java.util.Map;

/**
 * 鏁版嵁澶勭悊浠诲姟鎻忚堪
 * 鐢ㄤ簬鎻忚堪涓€涓暟鎹鐞嗕换鍔＄殑鍩烘湰淇℃伅銆佺被鍨嬨€佸弬鏁扮瓑
 * 
 * @author CDRC Runtime Team
 */
public class TaskDescription {

    /**
     * 浠诲姟ID锛堝敮涓€鏍囪瘑锛?
     */
    private String taskId;

    /**
     * 浠诲姟鍚嶇О
     */
    private String taskName;

    /**
     * 浠诲姟绫诲瀷锛堝锛欵TL銆佹暟鎹竻娲椼€佹暟鎹浆鎹€佹暟鎹悓姝ョ瓑锛?
     */
    private String taskType;

    /**
     * 浠诲姟鎻忚堪
     */
    private String description;

    /**
     * 浠诲姟浼樺厛绾э紙鏁板瓧瓒婂ぇ浼樺厛绾ц秺楂橈紝榛樿0锛?
     */
    private Integer priority;

    /**
     * 浠诲姟鍙傛暟锛圝SON鏍煎紡鎴朚ap鏍煎紡锛?
     * 鍖呭惈浠诲姟鎵ц鎵€闇€鐨勫悇绉嶉厤缃弬鏁?
     */
    private Map<String, Object> parameters;

    /**
     * 浠诲姟閰嶇疆锛圝SON鏍煎紡锛?
     * 鍖呭惈浠诲姟鎵ц鐨勫叿浣撻厤缃紝濡傛暟鎹簮銆佺洰鏍囥€佽浆鎹㈣鍒欑瓑
     */
    private String taskConfig;

    /**
     * 浠诲姟鍒涘缓鏃堕棿
     */
    private Date createTime;

    /**
     * 浠诲姟鍒涘缓鑰?
     */
    private String createdBy;

    /**
     * 浠诲姟鎵€灞炵鎴稩D
     */
    private String tenantId;

    /**
     * 浠诲姟瓒呮椂鏃堕棿锛堟绉掞級锛?琛ㄧず涓嶈秴鏃?
     */
    private Long timeout;

    /**
     * 閲嶈瘯娆℃暟锛堥粯璁?锛屼笉閲嶈瘯锛?
     */
    private Integer retryCount;

    /**
     * 鏄惁寮傛鎵ц锛堥粯璁alse锛屽悓姝ユ墽琛岋級
     */
    private Boolean async;

    /**
     * 渚濊禆鐨勪换鍔D鍒楄〃锛堣繖浜涗换鍔″畬鎴愬悗鎵嶈兘鎵ц褰撳墠浠诲姟锛?
     */
    private java.util.List<String> dependencies;

    /**
     * 浠诲姟鏍囩锛堢敤浜庡垎绫诲拰杩囨护锛?
     */
    private java.util.List<String> tags;

    /**
     * 鎵╁睍灞炴€?
     */
    private Map<String, Object> extensions;

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPriority() {
        return priority != null ? priority : 0;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getTaskConfig() {
        return taskConfig;
    }

    public void setTaskConfig(String taskConfig) {
        this.taskConfig = taskConfig;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTimeout() {
        return timeout != null ? timeout : 0L;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Integer getRetryCount() {
        return retryCount != null ? retryCount : 0;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Boolean getAsync() {
        return async != null ? async : false;
    }

    public void setAsync(Boolean async) {
        this.async = async;
    }

    public java.util.List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(java.util.List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public java.util.List<String> getTags() {
        return tags;
    }

    public void setTags(java.util.List<String> tags) {
        this.tags = tags;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
}

