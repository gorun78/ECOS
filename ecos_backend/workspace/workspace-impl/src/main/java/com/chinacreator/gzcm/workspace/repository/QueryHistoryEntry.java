package com.chinacreator.gzcm.workspace.repository;

import java.time.LocalDateTime;

/**
 * 查询历史持久化行（卷07 F07-21 / W185）。
 *
 * <p>与 {@code QueryRecord}（对外响应体）分开：持久化面只落<b>摘要</b>
 * —— {@code sqlDigest} 而非原始 DSL，避免历史表变成第二份可执行查询库。</p>
 *
 * @param id         主键（应用侧生成，8 位短串 + 完整 UUID 由 repository 决定）
 * @param subjectId  归属主体（用户 ID，缺省 {@code anonymous}）
 * @param question   自然语言问题 / ObjectQL 简称
 * @param sqlDigest  查询 DSL 的摘要（SHA-256 前 16 位十六进制），<b>不存原文</b>
 * @param rowCount   返回行数
 * @param traceId    入口 traceId（与 MDC / X-Request-Id 同源）
 * @param createdAt  落库时间
 */
public record QueryHistoryEntry(
        String id,
        String subjectId,
        String question,
        String sqlDigest,
        int rowCount,
        String traceId,
        LocalDateTime createdAt) {
}
