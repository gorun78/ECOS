package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * FileDataObject - 文件数据对象Bean类
 * 用于表示文件类型的数据对象
 */
public class FileDataObject implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String object_id;
    private String object_name;
    private String file_path;
    private String file_type;
    private String file_format;
    private String url;
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    public String getObject_name() {
        return object_name;
    }
    
    public void setObject_name(String object_name) {
        this.object_name = object_name;
    }
    
    public String getFile_path() {
        return file_path;
    }
    
    public void setFile_path(String file_path) {
        this.file_path = file_path;
    }
    
    public String getFile_type() {
        return file_type;
    }
    
    public void setFile_type(String file_type) {
        this.file_type = file_type;
    }
    
    public String getFile_format() {
        return file_format;
    }
    
    public void setFile_format(String file_format) {
        this.file_format = file_format;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    private String node_id;
    
    public String getNode_id() {
        return node_id;
    }
    
    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }
}
