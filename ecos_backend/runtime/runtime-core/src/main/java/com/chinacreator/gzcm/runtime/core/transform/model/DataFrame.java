package com.chinacreator.gzcm.runtime.core.transform.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据帧
 * 用于在转换步骤间传递数据
 */
public class DataFrame {
    
    private List<Map<String, Object>> data;
    private List<String> columns;
    private Map<String, Object> metadata;
    
    public DataFrame() {
        this.data = new ArrayList<>();
        this.columns = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    public DataFrame(List<Map<String, Object>> data) {
        this.data = data != null ? data : new ArrayList<>();
        this.columns = new ArrayList<>();
        this.metadata = new HashMap<>();
        if (!this.data.isEmpty()) {
            this.columns.addAll(this.data.get(0).keySet());
        }
    }
    
    /**
     * 工厂方法：使用列名列表创建空的DataFrame
     * @param columns 列名列表
     * @return 新的DataFrame实例
     */
    public static DataFrame createWithColumns(List<String> columns) {
        DataFrame df = new DataFrame();
        df.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
        return df;
    }
    
    public List<Map<String, Object>> getData() {
        return data;
    }
    
    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }
    
    public List<Map<String, Object>> getRows() {
        return data;
    }
    
    public void setRows(List<Map<String, Object>> rows) {
        this.data = rows;
    }
    
    public List<String> getColumns() {
        return columns;
    }
    
    public void setColumns(List<String> columns) {
        this.columns = columns;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public boolean isEmpty() {
        return data == null || data.isEmpty();
    }
    
    public int size() {
        return data != null ? data.size() : 0;
    }
    
    /**
     * 获取行数（与size()方法相同）
     * @return 行数
     */
    public int getRowCount() {
        return size();
    }
    
    public void addRow(Map<String, Object> row) {
        if (this.data == null) {
            this.data = new ArrayList<>();
        }
        this.data.add(row);
    }
}
