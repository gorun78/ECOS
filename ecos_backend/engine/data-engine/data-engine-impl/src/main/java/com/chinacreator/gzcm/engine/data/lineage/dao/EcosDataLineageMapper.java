package com.chinacreator.gzcm.engine.data.lineage.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * F02-11 血缘自动派生 — 血缘 node/edge 持久化契约（MyBatis 单通道）。
 *
 * <p>表：{@code ecos_data.ecos_data_lineage_node / ecos_data.ecos_data_lineage_edge}
 * （控制域，V58 建表 / V155 补 pipeline_task_id / V248 补增量水位线列）。
 * 全部 SQL 走 {@code #{} } 参数化（IR05），表名一事不用字符串拼接。</p>
 */
@Mapper
public interface EcosDataLineageMapper {

    int insertNode(@Param("id") String id,
                   @Param("nodeType") String nodeType,
                   @Param("name") String name,
                   @Param("schemaName") String schemaName,
                   @Param("tableName") String tableName,
                   @Param("datasourceId") String datasourceId,
                   @Param("layer") String layer,
                   @Param("properties") String properties,
                   @Param("pipelineTaskId") String pipelineTaskId);

    int insertNodes(@Param("rows") List<Map<String, Object>> rows);

    int insertEdge(@Param("id") String id,
                   @Param("sourceNodeId") String sourceNodeId,
                   @Param("targetNodeId") String targetNodeId,
                   @Param("edgeType") String edgeType,
                   @Param("pipelineTaskId") String pipelineTaskId,
                   @Param("transformation") String transformation,
                   @Param("properties") String properties);

    int insertEdges(@Param("rows") List<Map<String, Object>> rows);

    List<Map<String, Object>> findNodesByPipelineId(@Param("pipelineTaskId") String pipelineTaskId);

    List<Map<String, Object>> findEdgesByPipelineId(@Param("pipelineTaskId") String pipelineTaskId);

    List<Map<String, Object>> findNodesByTableName(@Param("tableName") String tableName);

    int deleteNodesByPipelineId(@Param("pipelineTaskId") String pipelineTaskId);

    int deleteEdgesByPipelineId(@Param("pipelineTaskId") String pipelineTaskId);

    /** 增量重建水位线：MAX(updated_at)；无派生记录时返回 null。 */
    LocalDateTime maxWatermark();

    int touchWatermark(@Param("ts") LocalDateTime ts);

    /** 已派生过的 pipeline 集合（分页，供增量 rebuild 判定"已同步"）。 */
    List<String> listSyncedPipelineIds(@Param("limit") int limit, @Param("offset") int offset);

    int countNodesByPipelineId(@Param("pipelineTaskId") String pipelineTaskId);
}
