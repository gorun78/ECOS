package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 数据湖对象 key 组装器 —— 近源层对象 key 的**唯一组装入口**（数据湖存储分层规范 §三）。
 *
 * <p>key 规范：
 * <ul>
 *   <li>结构化：{@code raw/structured/{source}/{table}/{partition_by}=YYYY-MM-DD/{table}_{yyyyMMddHHmmss}.{ext}}</li>
 *   <li>非结构化：{@code raw/unstructured/{source}/{docId}/{originalFileName}}</li>
 * </ul>
 *
 * <p>本类同时承担：
 * <ul>
 *   <li>分区字段名 {@code dw.lake.partition_by}（默认 {@code dt}）的读取与非法值回退（防把未校验值拼进对象 key）；</li>
 *   <li>存储格式 {@code dw.lake.storage_format}（默认 {@code parquet}）的读取与归一化；</li>
 *   <li>对象 key 各路径段的合法性校验（非空、无路径分隔符、无 {@code ..}、长度上限）。</li>
 * </ul>
 *
 * <p>禁止在管道/采集代码中硬编码 {@code raw/structured/} 前缀或 {@code dt=} 分区段；新增前缀一律走本类。
 *
 * @author DataBridge Datanet Team
 */
public final class LakeObjectKeys {

    private static final Logger log = LoggerFactory.getLogger(LakeObjectKeys.class);

    /** 近源层结构化区标识（分层规范 §二） */
    public static final String ZONE_STRUCTURED = "STRUCTURED";

    /** 近源层非结构化区标识（分层规范 §二） */
    public static final String ZONE_UNSTRUCTURED = "UNSTRUCTURED";

    /** 结构化近源区对象 key 前缀 */
    public static final String PREFIX_STRUCTURED = "raw/structured/";

    /** 非结构化近源区对象 key 前缀 */
    public static final String PREFIX_UNSTRUCTURED = "raw/unstructured/";

    /** 配置键：数据湖默认分区字段 */
    public static final String CFG_PARTITION_BY = "dw.lake.partition_by";

    /** 配置键：数据湖存储格式 */
    public static final String CFG_STORAGE_FORMAT = "dw.lake.storage_format";

    /** 分区字段默认值（与 SysConfigService 种子 / V41 迁移默认值一致） */
    public static final String DEFAULT_PARTITION_FIELD = "dt";

    /** 存储格式默认值（与 SysConfigService 种子 / V41 迁移默认值一致） */
    public static final String DEFAULT_STORAGE_FORMAT = "parquet";

    /** 分区字段名合法标识符（防注入：只允许 [A-Za-z_][A-Za-z0-9_]*） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** 分区日值格式（{@code YYYY-MM-DD}） */
    private static final Pattern DAY_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /** 路径段最大长度 */
    private static final int MAX_SEGMENT_LEN = 128;

    /** 对象 key 最大长度（td_data_resource.source_path VARCHAR(512)） */
    private static final int MAX_KEY_LEN = 512;

    /** 分区日期格式 */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 对象名时间戳格式 */
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private LakeObjectKeys() {
    }

    /**
     * 解析分区字段名（{@code dw.lake.partition_by}）。
     * <p>空值/非法标识符 → 回退默认 {@code dt} 并记 warn（禁止把未校验的配置值直接拼进对象 key）。
     *
     * @param configured 配置值（可空）
     * @return 合法分区字段名（永不为空）
     */
    public static String resolvePartitionField(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PARTITION_FIELD;
        }
        String candidate = configured.trim();
        if (!IDENTIFIER_PATTERN.matcher(candidate).matches()) {
            log.warn("dw.lake.partition_by 配置非法: '{}'（须匹配 [A-Za-z_][A-Za-z0-9_]*），回退默认分区字段 {}",
                    configured, DEFAULT_PARTITION_FIELD);
            return DEFAULT_PARTITION_FIELD;
        }
        return candidate;
    }

    /**
     * 解析存储格式（{@code dw.lake.storage_format}），归一化为小写。
     *
     * @param configured 配置值（可空）
     * @return 小写格式名（空值回退 {@link #DEFAULT_STORAGE_FORMAT}）
     */
    public static String resolveStorageFormat(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_STORAGE_FORMAT;
        }
        return configured.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 组装结构化近源层对象 key（分层规范 §三）。
     *
     * @param source         上游数据源标识（{@code {source}} 段）
     * @param table          表名
     * @param partitionField 分区字段名（为空则取默认 {@code dt}）
     * @param extension      文件扩展名（如 {@code csv}，为空则取 {@code csv}）
     * @param now            时间基准（为空取当前时间）
     * @return 形如 {@code raw/structured/ds1/orders/dt=2026-09-19/orders_20260919103000.csv}
     * @throws ValidationException 路径段非法或 key 超长
     */
    public static String structuredObjectKey(String source, String table, String partitionField,
                                             String extension, LocalDateTime now) {
        String safeSource = requireSegment("source", source);
        String safeTable = requireSegment("table", table);
        String safePartition = resolvePartitionField(partitionField);
        String safeExt = normalizeExtension(extension);
        LocalDateTime ts = now != null ? now : LocalDateTime.now();
        String key = PREFIX_STRUCTURED + safeSource + "/" + safeTable + "/"
                + safePartition + "=" + ts.format(DAY_FORMAT) + "/"
                + safeTable + "_" + ts.format(TS_FORMAT) + "." + safeExt;
        return requireKeyLength(key);
    }

    /**
     * 组装结构化近源层**前缀**（读取时按前缀列对象，取最新分区对象）。
     *
     * @param source         上游数据源标识
     * @param table          表名
     * @param partitionField 分区字段名（为空取默认）
     * @param day            指定分区日（{@code YYYY-MM-DD}，可空 = 不限分区日）
     * @return 形如 {@code raw/structured/ds1/orders/} 或 {@code raw/structured/ds1/orders/dt=2026-09-19/}
     * @throws ValidationException 路径段或分区日非法
     */
    public static String structuredPrefix(String source, String table, String partitionField, String day) {
        String safeSource = requireSegment("source", source);
        String safeTable = requireSegment("table", table);
        String prefix = PREFIX_STRUCTURED + safeSource + "/" + safeTable + "/";
        if (day == null || day.isBlank()) {
            return prefix;
        }
        String safeDay = day.trim();
        if (!DAY_PATTERN.matcher(safeDay).matches()) {
            throw new ValidationException("dt", "分区日非法（须为 YYYY-MM-DD）: " + day);
        }
        return prefix + resolvePartitionField(partitionField) + "=" + safeDay + "/";
    }

    /**
     * 组装非结构化近源层对象 key（分层规范 §三）。
     *
     * @param source           上游标识（{@code {source}} 段）
     * @param docId            文档 ID
     * @param originalFileName 原始文件名（只保留末段文件名，防越出 {docId} 目录）
     * @return 形如 {@code raw/unstructured/kb/doc-1/manual.pdf}
     * @throws ValidationException 入参非法或 key 超长
     */
    public static String unstructuredObjectKey(String source, String docId, String originalFileName) {
        String safeSource = requireSegment("source", source);
        String safeDocId = requireSegment("docId", docId);
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new ValidationException("originalFileName", "文件名不能为空");
        }
        // 先去路径（含反斜杠），只保留末段文件名，防止越出 {docId}/ 目录
        String fileName = originalFileName.trim().replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        if (fileName.isBlank()) {
            throw new ValidationException("originalFileName", "文件名不能为空");
        }
        String safeFileName = requireSegment("originalFileName", fileName);
        return requireKeyLength(PREFIX_UNSTRUCTURED + safeSource + "/" + safeDocId + "/" + safeFileName);
    }

    /**
     * 组装非结构化近源层**前缀**（读取时按前缀列对象，B6-2 解析节点未显式指定对象名时用）。
     *
     * @param source 上游标识（{@code {source}} 段）
     * @param docId  文档 ID
     * @return 形如 {@code raw/unstructured/kb/doc-1/}
     * @throws ValidationException 段非法
     */
    public static String unstructuredPrefix(String source, String docId) {
        String safeSource = requireSegment("source", source);
        String safeDocId = requireSegment("docId", docId);
        return PREFIX_UNSTRUCTURED + safeSource + "/" + safeDocId + "/";
    }

    /**
     * 校验显式指定的对象名（节点 {@code objectName} 配置，含旧前缀 {@code datalake/} 兼容读取场景）。
     *
     * @param objectKey 显式对象名
     * @return 校验后的对象名
     * @throws ValidationException 为空/超长/含越界字符
     */
    public static String requireExplicitObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ValidationException("objectName", "对象名不能为空");
        }
        String key = objectKey.trim();
        if (key.startsWith("/") || key.contains("\\") || key.contains("..")) {
            throw new ValidationException("objectName", "非法对象名（含路径越界字符）: " + objectKey);
        }
        return requireKeyLength(key);
    }

    /**
     * 校验对象 key 路径段（非空、无 {@code /} {@code \} {@code ..}、长度上限）。
     *
     * @param field 字段名（报错定位用）
     * @param value 段值
     * @return trim 后的段值
     * @throws ValidationException 非法
     */
    public static String requireSegment(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, "对象 key 段不能为空: " + field);
        }
        String segment = value.trim();
        if (segment.length() > MAX_SEGMENT_LEN) {
            throw new ValidationException(field, "对象 key 段超长(>" + MAX_SEGMENT_LEN + "): " + segment);
        }
        if (segment.contains("/") || segment.contains("\\") || segment.contains("..")) {
            throw new ValidationException(field, "对象 key 段含非法字符: " + segment);
        }
        return segment;
    }

    /** 归一化扩展名（去前导点、转小写，默认 csv）。 */
    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "csv";
        }
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        if (!ext.matches("[a-z0-9]{1,16}")) {
            throw new ValidationException("format", "非法文件扩展名: " + extension);
        }
        return ext;
    }

    /** 对象 key 长度上限校验。 */
    private static String requireKeyLength(String key) {
        if (key.length() > MAX_KEY_LEN) {
            throw new ValidationException("objectName", "组装的对象 key 超长(>" + MAX_KEY_LEN + "): " + key);
        }
        return key;
    }
}
