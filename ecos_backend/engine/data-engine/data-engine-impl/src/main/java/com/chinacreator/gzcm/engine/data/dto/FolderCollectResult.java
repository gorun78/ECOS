package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹数据源批量采集结果（POST /unstructured/collect 返回）。
 * <p>逐文件处理、单文件失败不中断整体，失败明细记录于 {@link #items}。
 */
@Data
public class FolderCollectResult {

    /** 采集成功数 */
    private int collected;

    /** 采集失败数 */
    private int failed;

    /** 逐文件结果明细 */
    private List<CollectItem> items = new ArrayList<>();

    /** 单个文件的采集结果（status=SUCCESS / FAILED）。 */
    @Data
    public static class CollectItem {

        /** 文件名 */
        private String name;

        /** 处理状态：SUCCESS / FAILED */
        private String status;

        /** 失败原因（成功时为 null） */
        private String error;

        /** 构造单个文件结果项。 */
        public static CollectItem of(String name, String status, String error) {
            CollectItem item = new CollectItem();
            item.setName(name);
            item.setStatus(status);
            item.setError(error);
            return item;
        }
    }
}
