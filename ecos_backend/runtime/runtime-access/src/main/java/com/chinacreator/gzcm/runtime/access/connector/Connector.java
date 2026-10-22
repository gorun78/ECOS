package com.chinacreator.gzcm.runtime.access.connector;

import com.chinacreator.gzcm.common.data.model.ColumnMetadata;
import com.chinacreator.gzcm.common.data.model.DataResource;

import java.util.List;
import java.util.Map;

/**
 * 数据连接器 — 连接到具体数据源并获取数据资源信息。
 *
 * @author DataBridge Datanet Team
 */
public interface Connector {

    /** 支持的连接类型（如 "JDBC", "REST"） */
    String supportedType();

    /** 测试连接是否可达 */
    boolean testConnection(String connectionConfig);

    /** 列出该数据源中所有可用的数据资源（如表、视图、端点） */
    List<DataResource> listResources(String connectionConfig, String orgId, String orgName);

    /** 预览数据 — 返回表的样本行（SELECT * LIMIT n） */
    List<Map<String, Object>> queryPreview(String connectionConfig, String tableName, int limit);

    /**
     * 列出某个数据对象（表／视图／文件）的<b>列面</b>：列名＋声明类型＋可空性＋备注。
     *
     * <p>#142/B4-3e-46（D-B4-76）新增。此前上层只能从 {@link #queryPreview} 的样本行取到列名，
     * 类型／可空／备注一律拿不到——「新建数据源」表单的列面预览因此恒读空。本方法是该面的
     * <b>唯一底座通道</b>，禁止各引擎自行 {@code getColumns} 另建一套。</p>
     *
     * <p><b>返回口径</b>：空集合＝「该对象确实没有列」；拿不到的属性在
     * {@link ColumnMetadata} 里是 {@code null}＝「本连接器不提供该属性」，两者不得混同。
     * 根本不提供列面概念的连接器（如 REST）抛 {@link UnsupportedOperationException}
     * ＝「该面不可问」，由调用方显式渲染为降级说明，不得吞成空集合。</p>
     *
     * @param connectionConfig 连接配置 JSON（与其余方法同一形态）
     * @param objectRef        对象定位（如 {@code public.orders} 或文件名）
     * @return 列面清单，顺序按底座返回顺序，不做二次排序
     */
    List<ColumnMetadata> listColumns(String connectionConfig, String objectRef);
}
