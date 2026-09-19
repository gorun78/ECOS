package com.chinacreator.gzcm.runtime.access.document;

/**
 * 文档分块结果（runtime-access 公共能力，B6-2）。
 *
 * <p>滑动窗口切分的单块产物：序号 + 文本 + 字符区间。
 * 由 {@link DocumentChunkSplitter} 产出，data-engine 与 kb-engine 各自映射到目标表。
 *
 * @param chunkIndex 分块序号（自 0 递增）
 * @param content    分块文本
 * @param charStart  原文起始字符偏移（含）
 * @param charEnd    原文结束字符偏移（不含）
 * @author ECOS Runtime Access Team
 */
public record DocumentChunk(int chunkIndex, String content, int charStart, int charEnd) {
}
