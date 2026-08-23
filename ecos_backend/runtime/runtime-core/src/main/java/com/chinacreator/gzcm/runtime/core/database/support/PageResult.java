package com.chinacreator.gzcm.runtime.core.database.support;

import java.util.List;

public class PageResult<T> {
    private List<T> data;
    private long total;
    private int offset;
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> data, long total, int offset, int pageSize) {
        this.data = data;
        this.total = total;
        this.offset = offset;
        this.pageSize = pageSize;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTotalPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / pageSize);
    }

    public boolean hasNextPage() {
        return offset + pageSize < total;
    }

    public boolean hasPreviousPage() {
        return offset > 0;
    }
}
