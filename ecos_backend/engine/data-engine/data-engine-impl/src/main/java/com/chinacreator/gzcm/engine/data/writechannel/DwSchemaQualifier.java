package com.chinacreator.gzcm.engine.data.writechannel;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * F02-05 写入通道 · MC06 schema 限定名解析 + 数据域分类。
 *
 * <p>MC06：DDL/Mapper/yml 禁硬编码 schema 前缀，schema 一律配置化注入（本类的
 * {@code businessSchema} 由 {@code @Value} 从 {@code dw.business-schema} 注入，
 * 默认 {@code ecos_dw}）。
 *
 * <p>数据域二分（架构铁律 §3.5 / 数据库访问规范 §〇）：
 * <ul>
 *   <li><b>业务域</b>：外部接入数据及加工产物，落 {@code ecos_dw}（业务 schema，可切 OLAP/MinIO/向量）。</li>
 *   <li><b>控制域</b>：平台运行数据，落 5 引擎 schema + 主控制 schema，<b>禁入 OLAP/湖载体</b>。</li>
 * </ul>
 * 本类据 carrierRef 解析出 {@code schema.table} 限定名并判定其归属域（业务/控制/OLAP 载体），
 * 交由 {@link OlapEngineResolver} 做 MC04 守卫。
 */
@Component
public final class DwSchemaQualifier {

    /** 业务域 DW schema 默认值（MC06 由配置覆盖，见 {@code dw.business-schema}）。 */
    public static final String DEFAULT_BUSINESS_SCHEMA = "ecos_dw";

    /**
     * 控制域 schema（ST07 权威 5 引擎 + 主控制，含已知 legacy 引擎 schema）。
     * 业务数据禁入这些 schema（ST08）；控制数据禁入 OLAP（MC04）。
     */
    public static final Set<String> CONTROL_SCHEMAS = Set.of(
            "public", "ecos_control",
            "ecos_data", "ecos_ontology", "ecos_knowledge", "ecos_ai", "ecos_cognitive",
            // knownLegacy（R9 只停写不迁）：仍属控制域，禁入 OLAP
            "ecos_dq", "ecos_sysman", "ecos_buszhi", "ecos_dccheng", "ecos_aiming", "ecos_security");

    public enum Domain { BUSINESS, CONTROL, OLAP_CARRIER }

    public static final class Qualified {
        private final String schema;
        private final String table;
        private final String qualifiedName;

        public Qualified(String schema, String table) {
            this.schema = schema;
            this.table = table;
            this.qualifiedName = table.contains(".") ? table : schema + "." + table;
        }

        public String getSchema() { return schema; }
        public String getTable() { return table; }
        public String getQualifiedName() { return qualifiedName; }
    }

    private final String businessSchema;

    public DwSchemaQualifier(@Value("${dw.business-schema:}") String businessSchema) {
        this.businessSchema = (businessSchema == null || businessSchema.isBlank())
                ? DEFAULT_BUSINESS_SCHEMA : businessSchema.trim();
    }

    public String getBusinessSchema() { return businessSchema; }

    /**
     * 解析 carrierRef 为 schema 限定名。
     * <ul>
     *   <li>已含 {@code .}（如 {@code ecos_dw.t} 或 {@code schema.table}）→ 原样拆分；</li>
     *   <li>仅表名（如 {@code my_table}）→ 落业务域 DW schema 前缀（{@code businessSchema.my_table}）。</li>
     * </ul>
     *
     * @param carrierRef 载体标识（必填，非空已由上层校验）
     * @return 限定名 + schema + 表名
     */
    public Qualified resolve(String carrierRef) {
        String ref = carrierRef.trim();
        if (ref.contains(".")) {
            int dot = ref.indexOf('.');
            String schema = ref.substring(0, dot);
            String table = ref.substring(dot + 1);
            if (schema.isEmpty() || table.isEmpty()) {
                throw new IllegalArgumentException(DataDomainErrorCode.ECOS_DATA_061
                        + ": 非法 carrierRef（限定名残缺）: " + carrierRef);
            }
            return new Qualified(schema, table);
        }
        return new Qualified(businessSchema, ref);
    }

    /**
     * 判定某 schema 的归属域（就 PG 载体而言）。OLAP 载体（Doris/ClickHouse）单独归类，
     * 由调用方结合 storageKind 走 MC04 守卫。
     */
    public Domain classifyDomain(String schema) {
        if (schema == null) return Domain.BUSINESS;
        String s = schema.trim().toLowerCase();
        if (CONTROL_SCHEMAS.contains(s)) return Domain.CONTROL;
        return Domain.BUSINESS;
    }
}
