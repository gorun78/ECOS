package com.chinacreator.gzcm.runtime.core.common.datamusterdefine.bean;

import java.io.Serializable;
import java.util.List;

public class DataObject implements Serializable {
    private static final long serialVersionUID = 1L;

    private String object_id;
    private String object_name;
    private List<DataObject> children;
    private List<DataObjectColumn> dataObjectColumns;

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

    public List<DataObject> getChildren() {
        return children;
    }

    public void setChildren(List<DataObject> children) {
        this.children = children;
    }

    public List<DataObjectColumn> getDataObjectColumns() {
        return dataObjectColumns;
    }

    public void setDataObjectColumns(List<DataObjectColumn> dataObjectColumns) {
        this.dataObjectColumns = dataObjectColumns;
    }
}
