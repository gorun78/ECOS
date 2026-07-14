package com.chinacreator.gzcm.runtime.core.dataaccess.model;

/**
 * 鎻掑叆閫夐」妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class InsertOptions {
    
    /**
     * 鎵归噺澶у皬锛堟瘡娆℃彃鍏ョ殑璁板綍鏁帮級
     */
    private Integer batchSize = 1000;
    
    /**
     * 鏄惁蹇界暐閲嶅锛堝鏋滀负true锛岄亣鍒伴噸澶嶆暟鎹椂璺宠繃锛?
     */
    private boolean ignoreDuplicate = false;
    
    /**
     * 鏄惁鍚敤浜嬪姟锛堝鏋滀负true锛屾墍鏈夋彃鍏ュ湪涓€涓簨鍔′腑锛?
     */
    private boolean transactional = true;
    
    /**
     * 鏄惁鍦ㄦ彃鍏ュ墠楠岃瘉鏁版嵁
     */
    private boolean validateBeforeInsert = true;
    
    /**
     * 鏄惁鍦ㄦ彃鍏ュけ璐ユ椂缁х画锛堝鏋滀负true锛岄儴鍒嗗け璐ユ椂缁х画鎻掑叆鍏朵粬鏁版嵁锛?
     */
    private boolean continueOnError = false;
    
    // Getters and Setters
    
    public Integer getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }
    
    public boolean isIgnoreDuplicate() {
        return ignoreDuplicate;
    }
    
    public void setIgnoreDuplicate(boolean ignoreDuplicate) {
        this.ignoreDuplicate = ignoreDuplicate;
    }
    
    public boolean isTransactional() {
        return transactional;
    }
    
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }
    
    public boolean isValidateBeforeInsert() {
        return validateBeforeInsert;
    }
    
    public void setValidateBeforeInsert(boolean validateBeforeInsert) {
        this.validateBeforeInsert = validateBeforeInsert;
    }
    
    public boolean isContinueOnError() {
        return continueOnError;
    }
    
    public void setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }
}

