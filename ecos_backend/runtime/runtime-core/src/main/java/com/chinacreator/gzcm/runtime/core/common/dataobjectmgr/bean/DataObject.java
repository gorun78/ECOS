package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/**
 * DataObject bean class
 * TODO: Add proper implementation based on actual requirements
 */
public class DataObject implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String object_id;
    private String object_name;
    private String ds_id;
    private String param_table_name;
    private String db_schema;
    private String node_id;
    
    // Getters and setters
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
    
    public String getDs_id() {
        return ds_id;
    }
    
    public void setDs_id(String ds_id) {
        this.ds_id = ds_id;
    }
    
    public String getParam_table_name() {
        return param_table_name;
    }
    
    public void setParam_table_name(String param_table_name) {
        this.param_table_name = param_table_name;
    }
    
    public String getDb_schema() {
        return db_schema;
    }
    
    public void setDb_schema(String db_schema) {
        this.db_schema = db_schema;
    }
    
    public String getNode_id() {
        return node_id;
    }
    
    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }
    
    private String error_table_name;
    
    public String getError_table_name() {
        return error_table_name;
    }
    
    public void setError_table_name(String error_table_name) {
        this.error_table_name = error_table_name;
    }
    
    private String object_type;
    
    public String getObject_type() {
        return object_type;
    }
    
    public void setObject_type(String object_type) {
        this.object_type = object_type;
    }
    
    // HBase parameter (stored as Object for flexibility)
    private Object hbaseParam;
    
    public Object getHbaseParam() {
        return hbaseParam;
    }
    
    public void setHbaseParam(Object hbaseParam) {
        this.hbaseParam = hbaseParam;
    }
    
    private String ds_name;
    
    public String getDs_name() {
        return ds_name;
    }
    
    public void setDs_name(String ds_name) {
        this.ds_name = ds_name;
    }
    
    private String error_ds_id;
    
    public String getError_ds_id() {
        return error_ds_id;
    }
    
    public void setError_ds_id(String error_ds_id) {
        this.error_ds_id = error_ds_id;
    }
    
    private String username;
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    // HBase mapping name provider method
    public String provideHBaseMappingName() {
        // Placeholder implementation
        return null;
    }
    
    private String db_type;
    
    public String getDb_type() {
        return db_type;
    }
    
    public void setDb_type(String db_type) {
        this.db_type = db_type;
    }
    
    private String dset_id;
    
    public String getDset_id() {
        return dset_id;
    }
    
    public void setDset_id(String dset_id) {
        this.dset_id = dset_id;
    }
    
    private String ds_type;
    
    public String getDs_type() {
        return ds_type;
    }
    
    public void setDs_type(String ds_type) {
        this.ds_type = ds_type;
    }
    
    private String status;
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    private Timestamp create_time;
    
    public Timestamp getCreate_time() {
        return create_time;
    }
    
    public void setCreate_time(Timestamp create_time) {
        this.create_time = create_time;
    }
    
    private Timestamp modify_time;
    
    public Timestamp getModify_time() {
        return modify_time;
    }
    
    public void setModify_time(Timestamp modify_time) {
        this.modify_time = modify_time;
    }
    
    private List<DataObject> children;

    public List<DataObject> getChildren() {
        return children;
    }

    public void setChildren(List<DataObject> children) {
        this.children = children;
    }

    private List<DataObjectColumn> dataObjectColumns;

    public List<DataObjectColumn> getDataObjectColumns() {
        return dataObjectColumns;
    }

    public void setDataObjectColumns(List<DataObjectColumn> dataObjectColumns) {
        this.dataObjectColumns = dataObjectColumns;
    }
    
    private String create_by_dx_type;
    private String source_org;
    private String def_initial_source_provider;
    private String is_struct_data;
    
    public String getCreate_by_dx_type() {
        return create_by_dx_type;
    }
    
    public void setCreate_by_dx_type(String create_by_dx_type) {
        this.create_by_dx_type = create_by_dx_type;
    }
    
    public String getSource_org() {
        return source_org;
    }
    
    public void setSource_org(String source_org) {
        this.source_org = source_org;
    }
    
    public String getDef_initial_source_provider() {
        return def_initial_source_provider;
    }
    
    public void setDef_initial_source_provider(String def_initial_source_provider) {
        this.def_initial_source_provider = def_initial_source_provider;
    }
    
    public String getIs_struct_data() {
        return is_struct_data;
    }
    
    public void setIs_struct_data(String is_struct_data) {
        this.is_struct_data = is_struct_data;
    }
    
    private String org_id;
    
    public String getOrg_id() {
        return org_id;
    }
    
    public void setOrg_id(String org_id) {
        this.org_id = org_id;
    }
    
    private String remark;
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    private String org_name;
    
    public String getOrg_name() {
        return org_name;
    }
    
    public void setOrg_name(String org_name) {
        this.org_name = org_name;
    }
}

