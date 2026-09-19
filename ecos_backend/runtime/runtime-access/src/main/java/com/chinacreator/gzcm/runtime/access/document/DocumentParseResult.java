package com.chinacreator.gzcm.runtime.access.document;

/**
 * 文档解析结果（runtime-access 公共能力，B6-2）。
 *
 * <p>统一承载 Tika / MinerU 双通道的解析产物：正文文本 + 基础元数据。
 * 迁移自 kb-engine 的 {@code DocumentParserService.ParseResult}，语义保持不变。
 *
 * @param text      解析出的正文文本（可能为空字符串，但不为 null）
 * @param fileType  文件类型（pdf/docx/xlsx/pptx/html/txt/unknown）
 * @param pageCount 页数（非 PDF 恒为 1）
 * @param charCount 文本字符数
 * @author ECOS Runtime Access Team
 */
public record DocumentParseResult(String text, String fileType, int pageCount, int charCount) {
}
