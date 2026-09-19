package com.chinacreator.gzcm.engine.data.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * DW 层实例行读取结果 VO（方案 §5.3「数据→知识 实例行读取（增量水位线）」）。
 *
 * <p>强类型返回（禁 {@code Map<String,Object>} 作响应体）；行本身为动态嵌套数据
 * （表结构由 DW 层定义），故用 {@code List<Map<String,Object>>} 逐行承载。
 */
@Data
public class DataLayerRowsVO {

    /** 分层名（合法 {@link com.chinacreator.gzcm.engine.data.model.DataLayer} 枚举） */
    private String layer;
    /** 数据资源 ID（td_data_resource.resource_id） */
    private String resourceId;
    /** 实例行（每行一个 Map：列名→值） */
    private List<Map<String, Object>> rows;
    /** 本次返回行数 */
    private int count;
    /** 本次读取的水位标识（供下次调用作为 watermark 入参；无更多数据时返回 null） */
    private String nextWatermark;
    /** 是否还有更多数据（rows 达到 limit 上限时为 true） */
    private boolean hasMore;
}