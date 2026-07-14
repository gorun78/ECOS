package com.chinacreator.gzcm.runtime.core.common.datamusterdefine.bean;

import java.io.Serializable;

/**
 * DataMetaBean - 数据元Bean类
 * 用于数据集定义中的数据元信息
 */
public class DataMetaBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String dfd_id;
    private String dfd_name;
    private String dfd_code;
    private String dfd_define;
    private String dfd_unit;
    private Integer mfd_length;
    private Integer mfd_precision;
    private String dfd_minimum;
    private String dfd_maximum;
    private String dfd_keyword;
    private String dfd_version;
    private Integer dfd_valid;
    private Integer dfd_value_type;
    private String dfd_value;
    private String ttd_id;
    private String sortStr;
    private String dict_id;
    private Integer dfd_primary;
    private String dict_name;
    private String extAttributes;
    private Integer query_type;
    
    // Getters and setters
    public String getDfd_id() {
        return dfd_id;
    }
    
    public void setDfd_id(String dfd_id) {
        this.dfd_id = dfd_id;
    }
    
    public String getDfd_name() {
        return dfd_name;
    }
    
    public void setDfd_name(String dfd_name) {
        this.dfd_name = dfd_name;
    }
    
    public String getDfd_code() {
        return dfd_code;
    }
    
    public void setDfd_code(String dfd_code) {
        this.dfd_code = dfd_code;
    }
    
    public String getDfd_define() {
        return dfd_define;
    }
    
    public void setDfd_define(String dfd_define) {
        this.dfd_define = dfd_define;
    }
    
    public String getDfd_unit() {
        return dfd_unit;
    }
    
    public void setDfd_unit(String dfd_unit) {
        this.dfd_unit = dfd_unit;
    }
    
    public Integer getMfd_length() {
        return mfd_length;
    }
    
    public void setMfd_length(Integer mfd_length) {
        this.mfd_length = mfd_length;
    }
    
    public Integer getMfd_precision() {
        return mfd_precision;
    }
    
    public void setMfd_precision(Integer mfd_precision) {
        this.mfd_precision = mfd_precision;
    }
    
    public String getDfd_minimum() {
        return dfd_minimum;
    }
    
    public void setDfd_minimum(String dfd_minimum) {
        this.dfd_minimum = dfd_minimum;
    }
    
    public String getDfd_maximum() {
        return dfd_maximum;
    }
    
    public void setDfd_maximum(String dfd_maximum) {
        this.dfd_maximum = dfd_maximum;
    }
    
    public String getDfd_keyword() {
        return dfd_keyword;
    }
    
    public void setDfd_keyword(String dfd_keyword) {
        this.dfd_keyword = dfd_keyword;
    }
    
    public String getDfd_version() {
        return dfd_version;
    }
    
    public void setDfd_version(String dfd_version) {
        this.dfd_version = dfd_version;
    }
    
    public Integer getDfd_valid() {
        return dfd_valid;
    }
    
    public void setDfd_valid(Integer dfd_valid) {
        this.dfd_valid = dfd_valid;
    }
    
    public Integer getDfd_value_type() {
        return dfd_value_type;
    }
    
    public void setDfd_value_type(Integer dfd_value_type) {
        this.dfd_value_type = dfd_value_type;
    }
    
    public String getDfd_value() {
        return dfd_value;
    }
    
    public void setDfd_value(String dfd_value) {
        this.dfd_value = dfd_value;
    }
    
    public String getTtd_id() {
        return ttd_id;
    }
    
    public void setTtd_id(String ttd_id) {
        this.ttd_id = ttd_id;
    }
    
    public String getSortStr() {
        return sortStr;
    }
    
    public void setSortStr(String sortStr) {
        this.sortStr = sortStr;
    }
    
    public String getDict_id() {
        return dict_id;
    }
    
    public void setDict_id(String dict_id) {
        this.dict_id = dict_id;
    }
    
    public Integer getDfd_primary() {
        return dfd_primary;
    }
    
    public void setDfd_primary(Integer dfd_primary) {
        this.dfd_primary = dfd_primary;
    }
    
    public String getDict_name() {
        return dict_name;
    }
    
    public void setDict_name(String dict_name) {
        this.dict_name = dict_name;
    }
    
    public String getExtAttributes() {
        return extAttributes;
    }
    
    public void setExtAttributes(String extAttributes) {
        this.extAttributes = extAttributes;
    }
    
    public Integer getQuery_type() {
        return query_type;
    }
    
    public void setQuery_type(Integer query_type) {
        this.query_type = query_type;
    }
    
    private String dset_id;
    
    public String getDset_id() {
        return dset_id;
    }
    
    public void setDset_id(String dset_id) {
        this.dset_id = dset_id;
    }
}

