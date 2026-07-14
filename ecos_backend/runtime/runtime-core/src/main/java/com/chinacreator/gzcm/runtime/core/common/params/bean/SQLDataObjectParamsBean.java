package com.chinacreator.gzcm.runtime.core.common.params.bean;

import java.io.Serializable;

public class SQLDataObjectParamsBean implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sql;

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}
