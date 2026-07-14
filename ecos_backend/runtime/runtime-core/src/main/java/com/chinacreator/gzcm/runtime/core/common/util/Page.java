package com.chinacreator.gzcm.runtime.core.common.util;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.util.LegacyListInfo;

/**
 * Page - 鍒嗛〉缁撴灉绫伙紙鍏煎鏃т唬鐮侊級
 * 鐢ㄤ簬鏇夸唬 com.chinacreator.gzcm.core.openapi.bean.Page
 */
public class Page {
    
    private List<?> datas;
    private long totalSize;
    private int pageNum;
    private int pageSize;
    
    /**
     * 浠嶭istInfo鏋勯€燩age
     * @param listInfo ListInfo瀵硅薄
     */
    public Page(LegacyListInfo listInfo) {
        if (listInfo != null) {
            this.datas = listInfo.getDatas();
            this.totalSize = listInfo.getTotalSize();
        } else {
            this.datas = new java.util.ArrayList<>();
            this.totalSize = 0;
        }
        this.pageNum = 1;
        this.pageSize = datas != null ? datas.size() : 0;
    }
    
    /**
     * 浠嶭ist鍜屾€绘暟鏋勯€燩age
     * @param datas 鏁版嵁鍒楄〃
     * @param totalSize 鎬绘暟
     */
    public Page(List<?> datas, long totalSize) {
        this.datas = datas != null ? datas : new java.util.ArrayList<>();
        this.totalSize = totalSize;
        this.pageNum = 1;
        this.pageSize = this.datas.size();
    }
    
    /**
     * 浠嶭istInfo鍜屽垎椤靛弬鏁版瀯閫燩age
     * @param listInfo ListInfo瀵硅薄
     * @param pageNum 椤电爜
     * @param pageSize 姣忛〉澶у皬
     */
    public Page(LegacyListInfo listInfo, int pageNum, int pageSize) {
        if (listInfo != null) {
            this.datas = listInfo.getDatas();
            this.totalSize = listInfo.getTotalSize();
        } else {
            this.datas = new java.util.ArrayList<>();
            this.totalSize = 0;
        }
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
    
    public List<?> getDatas() {
        return datas;
    }
    
    public void setDatas(List<?> datas) {
        this.datas = datas;
    }
    
    public long getTotalSize() {
        return totalSize;
    }
    
    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }
    
    public int getPageNum() {
        return pageNum;
    }
    
    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}

