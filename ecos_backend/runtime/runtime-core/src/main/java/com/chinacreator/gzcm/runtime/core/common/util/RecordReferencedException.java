package com.chinacreator.gzcm.runtime.core.common.util;

/**
 * 记录被引用异常
 */
public class RecordReferencedException extends Exception {
    private static final long serialVersionUID = 1L;
    
    private String referenceInfo;
    
    public RecordReferencedException() {
        super("记录已被引用，无法删除");
    }
    
    public RecordReferencedException(String referenceInfo) {
        super("记录已被引用：" + (referenceInfo != null ? referenceInfo : "无法删除"));
        this.referenceInfo = referenceInfo;
    }
    
    public String getReferenceInfo() {
        return referenceInfo;
    }
}
