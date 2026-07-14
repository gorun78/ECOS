package com.chinacreator.gzcm.runtime.core.dataaccess.model;

import java.util.List;
import java.util.Map;

/**
 * 鏌ヨ璇锋眰妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class QueryRequest {
    
    /**
     * 鏁版嵁浜у搧ID
     */
    private String dataProductId;
    
    /**
     * 鏌ヨ鍒楀垪琛紙濡傛灉涓虹┖鍒欐煡璇㈡墍鏈夊垪锛?
     */
    private List<String> columns;
    
    /**
     * 杩囨护鏉′欢
     */
    private FilterCondition filter;
    
    /**
     * 鎺掑簭鏉′欢鍒楄〃
     */
    private List<SortCondition> sort;
    
    /**
     * 鍒嗛〉淇℃伅
     */
    private Pagination pagination;
    
    /**
     * 涓婁笅鏂囦俊鎭紙鐢ㄦ埛銆佺鎴风瓑锛?
     */
    private Map<String, Object> context;
    
    /**
     * 鏄惁杩斿洖鎬绘暟
     */
    private boolean includeTotal = true;
    
    // Getters and Setters
    
    public String getDataProductId() {
        return dataProductId;
    }
    
    public void setDataProductId(String dataProductId) {
        this.dataProductId = dataProductId;
    }
    
    public List<String> getColumns() {
        return columns;
    }
    
    public void setColumns(List<String> columns) {
        this.columns = columns;
    }
    
    public FilterCondition getFilter() {
        return filter;
    }
    
    public void setFilter(FilterCondition filter) {
        this.filter = filter;
    }
    
    public List<SortCondition> getSort() {
        return sort;
    }
    
    public void setSort(List<SortCondition> sort) {
        this.sort = sort;
    }
    
    public Pagination getPagination() {
        return pagination;
    }
    
    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    public boolean isIncludeTotal() {
        return includeTotal;
    }
    
    public void setIncludeTotal(boolean includeTotal) {
        this.includeTotal = includeTotal;
    }
}

