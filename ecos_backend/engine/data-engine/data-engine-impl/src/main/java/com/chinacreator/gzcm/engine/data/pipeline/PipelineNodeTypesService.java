package com.chinacreator.gzcm.engine.data.pipeline;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点类型目录服务 — 返回 Pipeline 可用节点类型元数据清单。
 * <p>
 * 目录与 {@code PipelineExecutionService.executeNode} 的 switch 分支同源
 * （架构铁律 §4.8.2：前端节点面板、后端执行器、测试数据三方对齐）。
 * 每个类型的属性 schema 与必填字段对照 P2-01 规范 §4.2。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineNodeTypesService {

    /** schema 版本（SemVer，与 P2-01 规范联动） */
    private static final String SCHEMA_VERSION = "2.1.0";

    /**
     * 构建全部节点类型目录。
     *
     * @return 节点类型目录 VO
     */
    public PipelineNodeTypesVO listNodeTypes() {
        PipelineNodeTypesVO vo = new PipelineNodeTypesVO();
        vo.setVersion(SCHEMA_VERSION);
        vo.setItems(List.of(
                entry("SOURCE_JDBC", "Source JDBC", "JdbcConnector", "全版本", "Database",
                        props("datasourceId", "string", "sql", "string", "fetchSize", "number",
                                "incrementalColumn", "string", "lastSyncValue", "string"),
                        List.of("datasourceId", "sql")),
                entry("SOURCE_CSV", "Source CSV", "CsvConnector", "全版本", "FileText",
                        props("filePath", "string", "delimiter", "string", "header", "boolean",
                                "encoding", "string", "fetchSize", "number"),
                        List.of("filePath")),
                entry("SOURCE_REST", "Source REST", "RestApiConnector", "全版本", "Globe",
                        props("url", "string", "method", "string", "headers", "object",
                                "body", "string", "pagination", "string"),
                        List.of("url")),
                entry("SOURCE_CDC", "Source CDC", "未实现(仅旗舰版)", "仅flagship", "Radio",
                        props("datasourceId", "string", "topic", "string"),
                        List.of()),
                // B6-1：近源层（MinIO）读取节点。必填字段随 zone 分支（STRUCTURED 需 table；
                // UNSTRUCTURED 需 docId + originalFileName；显式 objectName 时均免），由执行器强制校验，
                // 故目录 requiredFields 为空集，避免误伤显式对象名用法。
                entry("SOURCE_MINIO", "Source MinIO", "MinioStorageService(数据湖近源层)", "全版本", "Database",
                        props("zone", "string", "source", "string", "table", "string",
                                "docId", "string", "originalFileName", "string", "objectName", "string",
                                "dt", "string", "format", "string", "delimiter", "string",
                                "header", "boolean", "encoding", "string"),
                        List.of()),
                entry("TRANSFORM_SQL", "Transform SQL", "系统JdbcTemplate", "全版本", "Settings",
                        props("sql", "string", "timeout", "number"),
                        List.of("sql")),
                entry("TRANSFORM_UDF", "Transform UDF", "UdfSandbox", "全版本", "Braces",
                        props("udfId", "string", "udfName", "string", "params", "object"),
                        List.of("udfId")),
                // B6-2：非结构化文档解析节点（A1 目标态）。近源层对象 → 解析 → 分块 → 落 DW 层 doc/doc_chunk，
                // 解析/切分复用 runtime-access 公共能力。chunkSize 须 ∈ {256,512,1024,2048}（与前端一致），
                // chunkOverlap < chunkSize，由执行器强制校验；对象名未显式指定时按 source/docId 组前缀取对象。
                entry("TRANSFORM_DOC_PARSE", "Transform Doc Parse", "runtime-access DocumentParseService", "全版本", "FileText",
                        props("source", "string", "docId", "string", "chunkSize", "number",
                                "chunkOverlap", "number", "originalFileName", "string", "objectName", "string"),
                        List.of("docId")),
                entry("JOIN", "Join", "内存合并(按joinKeys)", "全版本", "GitMerge",
                        props("joinType", "string", "joinKeys", "array", "inlineData", "object", "datasourceId", "string"),
                        List.of()),
                entry("SINK", "Sink", "ConnectorFactory(JdbcConnector)", "全版本", "Upload",
                        props("datasourceId", "string", "table", "string", "mode", "string",
                                "batchSize", "number", "inlineData", "object"),
                        List.of("table")),
                entry("SINK_MINIO", "Sink MinIO", "MinioStorageService(数据湖近源库)", "全版本", "Database",
                        props("bucket", "string", "objectName", "string", "table", "string",
                                "format", "string", "columns", "array", "inlineData", "object"),
                        List.of("table")),
                entry("OUTPUT_OBJECT", "Output Object", "系统JdbcTemplate", "全版本", "HardDrive",
                        props("targetTable", "string", "mode", "string", "batchSize", "number", "rows", "array"),
                        List.of("targetTable"))
        ));
        return vo;
    }

    private PipelineNodeTypesVO.EntryVO entry(String type, String name, String executor,
                                               String version, String icon,
                                               Map<String, String> properties,
                                               List<String> requiredFields) {
        PipelineNodeTypesVO.EntryVO entry = new PipelineNodeTypesVO.EntryVO();
        entry.setType(type);
        entry.setCode(type.toLowerCase());
        entry.setName(name);
        entry.setIcon(icon);
        entry.setCategory(categoryOf(type));
        entry.setExecutor(executor);
        entry.setVersion(version);
        boolean enabled = !"SOURCE_CDC".equals(type);
        entry.setEnabled(enabled);
        if (!enabled) {
            entry.setDisabledReasonKey("dw.pipeline.node.cdcFlagshipOnly");
        }
        PipelineNodeTypeMetaVO meta = new PipelineNodeTypeMetaVO();
        meta.setIcon(icon);
        meta.setCategory(categoryOf(type));
        meta.setProperties(properties);
        meta.setRequiredFields(requiredFields);
        entry.setMeta(meta);
        return entry;
    }

    /**
     * 节点类别归类（源/转换/关联/写入/输出）。
     */
    private String categoryOf(String type) {
        if (type.startsWith("SOURCE_")) {
            return "SOURCE";
        }
        if ("JOIN".equals(type)) {
            return "JOIN";
        }
        if ("SINK".equals(type) || "OUTPUT_OBJECT".equals(type) || "SINK_MINIO".equals(type)) {
            return "SINK";
        }
        return "TRANSFORM";
    }

    /**
     * 构造属性 schema map（保持字段顺序）。
     */
    private Map<String, String> props(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
