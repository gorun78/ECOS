package com.chinacreator.gzcm.runtime.core.dataaccess.model;

import java.util.List;

/**
 * 鏌ヨ缁撴灉妯″瀷
 * 
 * @param <T> 鏁版嵁绫诲瀷
 * @author CDRC Runtime Team
 */
public class QueryResult<T> {
    
    /**
     * 鏁版嵁鍒楄〃
     */
    private List<T> data;
    
    /**
     * 鎬绘暟
     */
    private Long total;
    
    /**
     * 鍒嗛〉淇℃伅
     */
    private Pagination pagination;
    
    /**
     * 鏌ヨ鑰楁椂锛堟绉掞級
     */
    private Long duration;
    
    /**
     * 鏋勯€犲嚱鏁?
     */
    public QueryResult() {
    }
    
    /**
     * 鏋勯€犲嚱鏁?
     * 
     * @param data 鏁版嵁鍒楄〃
     * @param total 鎬绘暟
     */
    public QueryResult(List<T> data, Long total) {
        this.data = data;
        this.total = total;
    }
    
    // Getters and Setters
    
    public List<T> getData() {
        return data;
    }
    
    public void setData(List<T> data) {
        this.data = data;
    }
    
    public Long getTotal() {
        return total;
    }
    
    public void setTotal(Long total) {
        this.total = total;
    }
    
    public Pagination getPagination() {
        return pagination;
    }
    
    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }
    
    public Long getDuration() {
        return duration;
    }
    
    public void setDuration(Long duration) {
        this.duration = duration;
    }
}

