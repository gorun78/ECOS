package com.chinacreator.gzcm.runtime.core.common.fileupload.bean;

import java.io.Serializable;
import java.util.Date;

/**
 * 文件上传历史记录Bean
 */
public class FileUploadHistory implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String file_up_id;
    private String data_object_id;
    private String file_name;
    private String file_path;
    private Long file_size;
    private Date upload_time;
    private String file_up_user_id;
    private String file_up_org;
    private String file_up_org_name;
    private String file_up_user_name;
    
    public String getFile_up_id() {
        return file_up_id;
    }
    
    public void setFile_up_id(String file_up_id) {
        this.file_up_id = file_up_id;
    }
    
    public String getData_object_id() {
        return data_object_id;
    }
    
    public void setData_object_id(String data_object_id) {
        this.data_object_id = data_object_id;
    }
    
    public String getFile_name() {
        return file_name;
    }
    
    public void setFile_name(String file_name) {
        this.file_name = file_name;
    }
    
    public String getFile_path() {
        return file_path;
    }
    
    public void setFile_path(String file_path) {
        this.file_path = file_path;
    }
    
    public Long getFile_size() {
        return file_size;
    }
    
    public void setFile_size(Long file_size) {
        this.file_size = file_size;
    }
    
    public Date getUpload_time() {
        return upload_time;
    }
    
    public void setUpload_time(Date upload_time) {
        this.upload_time = upload_time;
    }
    
    public String getFile_up_user_id() {
        return file_up_user_id;
    }
    
    public void setFile_up_user_id(String file_up_user_id) {
        this.file_up_user_id = file_up_user_id;
    }
    
    public String getFile_up_org() {
        return file_up_org;
    }
    
    public void setFile_up_org(String file_up_org) {
        this.file_up_org = file_up_org;
    }
    
    public String getFile_up_org_name() {
        return file_up_org_name;
    }
    
    public void setFile_up_org_name(String file_up_org_name) {
        this.file_up_org_name = file_up_org_name;
    }
    
    public String getFile_up_user_name() {
        return file_up_user_name;
    }
    
    public void setFile_up_user_name(String file_up_user_name) {
        this.file_up_user_name = file_up_user_name;
    }
    
    private String old_file_name;
    
    public String getOld_file_name() {
        return old_file_name;
    }
    
    public void setOld_file_name(String old_file_name) {
        this.old_file_name = old_file_name;
    }
}
