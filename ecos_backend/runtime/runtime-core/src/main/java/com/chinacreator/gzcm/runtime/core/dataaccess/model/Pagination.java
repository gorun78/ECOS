package com.chinacreator.gzcm.runtime.core.dataaccess.model;

/**
 * 鍒嗛〉淇℃伅妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class Pagination {
    
    /**
     * 椤电爜锛堜粠1寮€濮嬶級
     */
    private Integer pageNum = 1;
    
    /**
     * 姣忛〉澶у皬
     */
    private Integer pageSize = 20;
    
    /**
     * 鎬绘暟锛堟煡璇㈠悗濉厖锛?
     */
    private Long total;
    
    /**
     * 鎬婚〉鏁帮紙鏌ヨ鍚庡～鍏咃級
     */
    private Integer totalPages;
    
    /**
     * 鏋勯€犲嚱鏁?
     */
    public Pagination() {
    }
    
    /**
     * 鏋勯€犲嚱鏁?
     * 
     * @param pageNum 椤电爜
     * @param pageSize 姣忛〉澶у皬
     */
    public Pagination(Integer pageNum, Integer pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
    
    /**
     * 璁＄畻鎬婚〉鏁?
     */
    public void calculateTotalPages() {
        if (total != null && pageSize != null && pageSize > 0) {
            this.totalPages = (int) Math.ceil((double) total / pageSize);
        }
    }
    
    /**
     * 鑾峰彇鍋忕Щ閲?
     */
    public int getOffset() {
        if (pageNum != null && pageSize != null) {
            return (pageNum - 1) * pageSize;
        }
        return 0;
    }
    
    // Getters and Setters
    
    public Integer getPageNum() {
        return pageNum;
    }
    
    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }
    
    public Integer getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
    
    public Long getTotal() {
        return total;
    }
    
    public void setTotal(Long total) {
        this.total = total;
        calculateTotalPages();
    }
    
    public Integer getTotalPages() {
        return totalPages;
    }
    
    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }
}

