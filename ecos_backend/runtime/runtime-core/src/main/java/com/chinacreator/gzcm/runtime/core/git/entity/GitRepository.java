package com.chinacreator.gzcm.runtime.core.git.entity;

import java.util.Date;

/**
 * Git 娴犳挸绨辩€圭偘缍嬮敍鍦ntime 閸忣剙鍙″Ο鈥崇€烽敍?
 * <p>
 * 娴ｆ粈璐熼崗顒€鍙￠弨顖涙嫼閼宠棄濮忛敍灞肩返 AIMod-Ming閵嗕竻us-Zhi閵嗕笍c-Cheng 缁涘膩閸?
 * 闁俺绻?Runtime 閻?Git 閻楀牊婀伴幒褍鍩楅懗钘夊鏉╂稖顢戝鏇犳暏閵?
 */
public class GitRepository {

    /**
     * 娴犳挸绨盜D閿涘牓鈧槒绶稉濠氭暛閿涘奔绶电化鑽ょ埠閸愬懘鍎村鏇犳暏閿?
     */
    private String repositoryId;

    /**
     * 娴犳挸绨遍崥宥囆為敍鍫㈡暏娴滃骸鐫嶇粈鐚寸礆
     */
    private String name;

    /**
     * 閺堫剙婀寸捄顖氱窞閿涘牆浼愭担婊冨閺堫剚澧嶉崷銊ф窗瑜版洩绱?
     */
    private String localPath;

    /**
     * 鏉╂粎鈻兼禒鎾崇氨閸︽澘娼冮敍鍫濐洤閿涙ttps:// 閹?ssh://閿?
     */
    private String remoteUrl;

    /**
     * 姒涙顓婚崚鍡樻暜閸氬秶袨閿涘牆顩ч敍姝產in閵嗕沟aster閿?
     */
    private String defaultBranch;

    /**
     * 閺勵垰鎯侀崥顖滄暏
     */
    private boolean enabled;

    /**
     * 閸掓稑缂撻弮鍫曟？
     */
    private Date createdTime;

    /**
     * 閸掓稑缂撴禍?
     */
    private String createdBy;

    /**
     * 閺堚偓鏉╂垶娲块弬鐗堟闂?
     */
    private Date updatedTime;

    /**
     * 閺堚偓鏉╂垶娲块弬棰佹眽
     */
    private String updatedBy;

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Date updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}


