package com.chinacreator.gzcm.engine.ontology.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本体域 schema 单点注入（详细设计-03 E.1 / F03-01）。
 *
 * <p>所有新代码（口径 / 指标 / 提案 / Function 审计）写 SQL 一律经 {@link #t(String)}
 * 做 schema 全限定（MC06 / §四 附则1 裸表名=FAIL）。R-1 schema 归属裁决（public →
 * ecos_ontology）落地时只改配置值，SQL 文本不动。
 *
 * <p>配置键 {@code ecos.schema.ontology}，缺省 {@code ecos_ontology}（R-1 a 已批准的目标 schema，
 * 与 V167.1/V168.1/V174 等脚本建表 schema 一致）。
 */
@Component
public class OntologySchemaSupport {

    private final String schema;

    public OntologySchemaSupport(@Value("${ecos.schema.ontology:ecos_ontology}") String schema) {
        this.schema = (schema == null || schema.isBlank()) ? "ecos_ontology" : schema.trim();
    }

    /** 返回限定表名 {@code {schema}.{table}}。 */
    public String t(String table) {
        return this.schema + "." + table;
    }

    public String schema() {
        return this.schema;
    }
}
