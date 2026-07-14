package com.chinacreator.gzcm.runtime.core.common.rpccaller.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RowFieldInfoAndData implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 新实现使用的通用数据结构
     */
    private List<Map<String, Object>> data;

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    /**
     * 兼容旧代码的访问方式，返回 Object[] 列表
     */
    public List<Object[]> getDatas() {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object[]> rows = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            rows.add(row != null ? row.values().toArray() : new Object[0]);
        }
        return rows;
    }
}
