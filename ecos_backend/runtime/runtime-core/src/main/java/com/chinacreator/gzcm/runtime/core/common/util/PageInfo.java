package com.chinacreator.gzcm.runtime.core.common.util;

import java.util.List;

/**
 * PageInfo - Pagination utility class to replace frameworkset's LegacyListInfo
 * 
 * This class provides a Spring-compatible pagination result wrapper that
 * maintains compatibility with the original LegacyListInfo interface while using
 * MyBatis PageHelper internally.
 * 
 * Usage:
 * <pre>
 * PageHelper.startPage(pageNum, pageSize);
 * List&lt;Entity&gt; list = mapper.selectList();
 * PageInfo&lt;Entity&gt; pageInfo = new PageInfo&lt;&gt;(list);
 * </pre>
 */
public class PageInfo<T> {
    
    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;
    private int pages;
    private boolean hasNextPage;
    private boolean hasPreviousPage;
    private boolean isFirstPage;
    private boolean isLastPage;
    
    /**
     * Default constructor
     */
    public PageInfo() {
    }
    
    /**
     * Constructor with list
     * 
     * @param list the list of items
     */
    public PageInfo(List<T> list) {
        this.list = list;
        // Try to detect if this is a PageHelper Page using reflection
        if (list != null && isPageHelperPage(list)) {
            try {
                // Use reflection to access PageHelper Page methods
                Class<?> pageClass = list.getClass();
                this.total = getLongValue(pageClass, list, "getTotal");
                this.pageNum = getIntValue(pageClass, list, "getPageNum");
                this.pageSize = getIntValue(pageClass, list, "getPageSize");
                this.pages = getIntValue(pageClass, list, "getPages");
                this.hasNextPage = getBooleanValue(pageClass, list, "isHasNextPage");
                this.hasPreviousPage = getBooleanValue(pageClass, list, "isHasPreviousPage");
                this.isFirstPage = getBooleanValue(pageClass, list, "isFirstPage");
                this.isLastPage = getBooleanValue(pageClass, list, "isLastPage");
            } catch (Exception e) {
                // Fallback to default values if reflection fails
                setDefaultValues(list);
            }
        } else {
            setDefaultValues(list);
        }
    }
    
    /**
     * Check if the list is a PageHelper Page using reflection
     */
    private boolean isPageHelperPage(List<T> list) {
        try {
            Class<?> clazz = list.getClass();
            // Check if it's a PageHelper Page by checking for getTotal method
            clazz.getMethod("getTotal");
            clazz.getMethod("getPageNum");
            return clazz.getName().contains("pagehelper") || 
                   clazz.getSimpleName().equals("Page");
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    /**
     * Get long value using reflection
     */
    private long getLongValue(Class<?> clazz, Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = clazz.getMethod(methodName);
            Object result = method.invoke(obj);
            return result != null ? ((Number) result).longValue() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
    
    /**
     * Get int value using reflection
     */
    private int getIntValue(Class<?> clazz, Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = clazz.getMethod(methodName);
            Object result = method.invoke(obj);
            return result != null ? ((Number) result).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Get boolean value using reflection
     */
    private boolean getBooleanValue(Class<?> clazz, Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = clazz.getMethod(methodName);
            Object result = method.invoke(obj);
            return result != null ? (Boolean) result : false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Set default values when PageHelper is not available
     */
    private void setDefaultValues(List<T> list) {
        this.total = list != null ? list.size() : 0;
        this.pageNum = 1;
        this.pageSize = list != null ? list.size() : 0;
        this.pages = 1;
        this.hasNextPage = false;
        this.hasPreviousPage = false;
        this.isFirstPage = true;
        this.isLastPage = true;
    }
    
    /**
     * Constructor with all parameters
     * 
     * @param list the list of items
     * @param total total count
     * @param pageNum current page number
     * @param pageSize page size
     */
    public PageInfo(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = (int) Math.ceil((double) total / pageSize);
        this.hasNextPage = pageNum < pages;
        this.hasPreviousPage = pageNum > 1;
        this.isFirstPage = pageNum == 1;
        this.isLastPage = pageNum == pages || pages == 0;
    }
    
    /**
     * Get the list of items
     * 
     * @return list of items
     */
    public List<T> getList() {
        return list;
    }
    
    /**
     * Set the list of items
     * 
     * @param list list of items
     */
    public void setList(List<T> list) {
        this.list = list;
    }
    
    /**
     * Get total count
     * 
     * @return total count
     */
    public long getTotal() {
        return total;
    }
    
    /**
     * Set total count
     * 
     * @param total total count
     */
    public void setTotal(long total) {
        this.total = total;
    }
    
    /**
     * Get current page number
     * 
     * @return page number
     */
    public int getPageNum() {
        return pageNum;
    }
    
    /**
     * Set current page number
     * 
     * @param pageNum page number
     */
    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }
    
    /**
     * Get page size
     * 
     * @return page size
     */
    public int getPageSize() {
        return pageSize;
    }
    
    /**
     * Set page size
     * 
     * @param pageSize page size
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    /**
     * Get total pages
     * 
     * @return total pages
     */
    public int getPages() {
        return pages;
    }
    
    /**
     * Set total pages
     * 
     * @param pages total pages
     */
    public void setPages(int pages) {
        this.pages = pages;
    }
    
    /**
     * Check if there is next page
     * 
     * @return true if has next page
     */
    public boolean isHasNextPage() {
        return hasNextPage;
    }
    
    /**
     * Set has next page
     * 
     * @param hasNextPage has next page
     */
    public void setHasNextPage(boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }
    
    /**
     * Check if there is previous page
     * 
     * @return true if has previous page
     */
    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }
    
    /**
     * Set has previous page
     * 
     * @param hasPreviousPage has previous page
     */
    public void setHasPreviousPage(boolean hasPreviousPage) {
        this.hasPreviousPage = hasPreviousPage;
    }
    
    /**
     * Check if this is the first page
     * 
     * @return true if first page
     */
    public boolean isFirstPage() {
        return isFirstPage;
    }
    
    /**
     * Set is first page
     * 
     * @param firstPage is first page
     */
    public void setFirstPage(boolean firstPage) {
        isFirstPage = firstPage;
    }
    
    /**
     * Check if this is the last page
     * 
     * @return true if last page
     */
    public boolean isLastPage() {
        return isLastPage;
    }
    
    /**
     * Set is last page
     * 
     * @param lastPage is last page
     */
    public void setLastPage(boolean lastPage) {
        isLastPage = lastPage;
    }
    
    /**
     * Get total count as int (for compatibility with LegacyListInfo)
     * 
     * @return total count as int
     */
    public int getTotalSize() {
        return (int) total;
    }
    
    /**
     * Get the size of current page list
     * 
     * @return size of list
     */
    public int getSize() {
        return list != null ? list.size() : 0;
    }
}

