package com.chinacreator.gzcm.runtime.core.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果容器 — 独立于 frameworkset 的轻量实现。
 * 用于兼容旧代码的 LegacyListInfo 引用，保持 datas + totalSize 签名不变。
 */
public class LegacyListInfo {
    private List<?> datas;
    private long totalSize;

    public LegacyListInfo() {
        this.datas = new ArrayList<>();
        this.totalSize = 0;
    }

    public List<?> getDatas() {
        if (datas == null) {
            return new ArrayList<>();
        }
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
}
