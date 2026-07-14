package com.chinacreator.gzcm.runtime.core.common.datamusterdefine.bean;

import java.io.Serializable;

public class DataObjectColumn implements Serializable {
    private static final long serialVersionUID = 1L;

    private String column_id;
    private String column_name;
    private String column_type;
    private boolean is_pk;

    public String getColumn_id() {
        return column_id;
    }

    public void setColumn_id(String column_id) {
        this.column_id = column_id;
    }

    public String getColumn_name() {
        return column_name;
    }

    public void setColumn_name(String column_name) {
        this.column_name = column_name;
    }

    public String getColumn_type() {
        return column_type;
    }

    public void setColumn_type(String column_type) {
        this.column_type = column_type;
    }

    public boolean isIs_pk() {
        return is_pk;
    }

    public void setIs_pk(boolean is_pk) {
        this.is_pk = is_pk;
    }
}
