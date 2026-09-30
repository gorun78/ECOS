package com.chinacreator.gzcm.engine.data.writechannel;

import java.util.Set;

/**
 * F02-05 第 3 条 / MC02 · 列类型白名单校验。
 *
 * <p>MC02 类型白名单（v1.2 扩列）：{@code VARCHAR / TIMESTAMP / SMALLINT / INTEGER / BIGINT /
 * TEXT / NUMERIC(p,s) / JSON}。其中 <b>{@code JSON} 仅限不参与 WHERE/JOIN/索引/唯一约束的
 * 描述性字段</b>，一旦需要检索或约束必须投影为独立列。
 *
 * <p>本守卫针对「写通道 WHERE 列」做两重判定：
 * <ol>
 *   <li><b>类型判</b>：列类型 ∈ {JSON, JSONB}（或已投出的 JSON 语义列）→ 拒（裸 JSON 列参与 WHERE）。
 *       列类型经 {@link ColumnTypeResolver} 提供（MC06 事务内经 runtime-access 取元数据），
 *       缺元数据时对 JSON 语义列名保守拒绝。</li>
 *   <li><b>命名判</b>：列名以 {@code _json} 结尾（DR04 JSON 语义列必带后缀）→ 拒
 *       （这类列按 DR04/MC02 本就不得参与 WHERE）。</li>
 * </ol>
 *
 * <p>纯裁决类（无 Driver、无 Spring 状态），可脱离容器单测。
 */
public final class ColumnTypeWhitelistGuard {

    /** JSON 语义列（MC02 白名单内但禁参与 WHERE 的类型集合）。 */
    private static final Set<String> JSON_TYPES = Set.of("JSON", "JSONB");

    /**
     * 列类型供给函数：给定限定表名 + 列名，返回列类型（大写）。
     * 运行时由写通道 Service 经 runtime-access 元数据实现；测试用固定映射实现。
     * 无法解析类型时返回 {@code null}。
     */
    @FunctionalInterface
    public interface ColumnTypeResolver {
        String typeOf(String qualifiedTable, String column);
    }

    private final ColumnTypeResolver resolver;

    public ColumnTypeWhitelistGuard(ColumnTypeResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 校验 WHERE 列集合：任一裸 JSON 列参与 WHERE 即拒。
     *
     * @param qualifiedTable 目标表限定名（MC06）
     * @param whereColumns   参与 WHERE 的列名（空/全空则通过）
     * @throws IllegalStateException 命中裸 JSON 列（MC02 违规）
     */
    public void guardWhereColumns(String qualifiedTable, java.util.Collection<String> whereColumns) {
        if (whereColumns == null || whereColumns.isEmpty()) return;
        for (String col : whereColumns) {
            if (col == null || col.isBlank()) continue;
            String c = col.trim();

            // ① 命名判：DR04/MC02 JSON 语义列（*_json）本就禁入 WHERE
            if (c.toLowerCase().endsWith("_json")) {
                throw new IllegalStateException(
                        "ECOS-DATA-052: MC02 违规 — JSON 语义列 " + c
                                + " 不得参与 WHERE（须投影为独立列，DR04/MC02）");
            }

            // ② 类型判：裸 JSON/JSONB 列参与 WHERE
            String type = resolver == null ? null : resolver.typeOf(qualifiedTable, c);
            if (type != null && JSON_TYPES.contains(type.trim().toUpperCase())) {
                throw new IllegalStateException(
                        "ECOS-DATA-052: MC02 违规 — 裸 JSON 列 " + c + "(" + type + ") 不得参与 WHERE（"
                                + qualifiedTable + "）");
            }
        }
    }
}
