package com.chinacreator.gzcm.runtime.core.metadata.model;

/**
 * 杩愯鏃朵娇鐢ㄧ殑绠€鍖栨妧鏈厓鏁版嵁妯″瀷锛屽崰浣嶇敤浜庣紪璇戙€?
 * 鍚庣画鍙笌 Cheng 妯″潡鐨勫畬鏁存ā鍨嬪榻愭垨閫氳繃 DTO 閫傞厤銆?
 */
public class TechnicalMetadata {
    private String id;
    private String name;
    private String description;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}


