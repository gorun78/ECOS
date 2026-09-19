package com.chinacreator.gzcm.runtime.access.document;

import com.chinacreator.gzcm.common.exception.DataAccessException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档解析公共能力 — Tika + MinerU 双通道路由（B6-2 由 kb-engine 上移 runtime-access）。
 *
 * <p>对齐 ECOS-DESIGN-COG-05 §三：按文件大小路由：
 * <ul>
 *   <li>文件 ≥ 5MB → {@link MinerUHttpParser}（OCR + 版面分析）</li>
 *   <li>文件 &lt; 5MB → Tika（既有通道）</li>
 * </ul>
 *
 * <p>本类是「文档解析」在 ECOS 的唯一实现（铁律 §2.5-6「补强而非自建」）：
 * data-engine 文档解析节点与 kb-engine 归集链路均复用本类，禁止各引擎重复实现。
 *
 * @author ECOS Runtime Access Team
 */
@Service
public class DocumentParseService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);

    /** MinerU 路由阈值：5MB */
    private static final long MINERU_SIZE_THRESHOLD = 5_242_880L;

    private final MinerUHttpParser mineruParser;

    public DocumentParseService(MinerUHttpParser mineruParser) {
        this.mineruParser = mineruParser;
    }

    /**
     * 解析文件 — 按文件大小路由到 Tika 或 MinerU 通道。
     *
     * @param filePath 待解析文件
     * @return 解析结果
     * @throws DataAccessException 解析失败（不返回空文本兜底）
     */
    public DocumentParseResult parse(Path filePath) {
        long size;
        try {
            size = Files.size(filePath);
            if (size > MINERU_SIZE_THRESHOLD) {
                log.info("DocumentParseService routing to MinerU (file size {} bytes > {} KB threshold)",
                        size, MINERU_SIZE_THRESHOLD / 1024);
                return mineruParser.parse(filePath);
            }
        } catch (Exception e) {
            log.warn("File size check failed, falling back to Tika: {}", e.getMessage());
        }
        return parseWithTika(filePath);
    }

    /**
     * Tika 通道解析（迁移自 kb-engine DocumentParserService，语义不变）。
     *
     * @param filePath 待解析文件
     * @return 解析结果（txt 兜底直读；仍为空时保留空文本并 warn）
     * @throws DataAccessException 解析异常
     */
    public DocumentParseResult parseWithTika(Path filePath) {
        String text = "";
        String fileType = "";
        int pageCount = 1;
        int charCount = 0;

        try (InputStream input = Files.newInputStream(filePath)) {
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(-1); // unlimited
            AutoDetectParser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();

            parser.parse(input, handler, metadata, context);

            text = handler.toString();
            fileType = detectFileType(metadata, filePath);
            pageCount = detectPageCount(metadata, fileType);
            charCount = text.length();

            // txt 文件 Tika 返回空时直读兜底
            if (text.isEmpty() && "txt".equalsIgnoreCase(fileType)) {
                text = Files.readString(filePath);
                charCount = text.length();
            }

            // 仍为空（如扫描版 PDF）保留空文本并告警，由上层决定后续处理
            if (text.isEmpty()) {
                log.warn("Parsed text is empty for file: {}", filePath);
            }
        } catch (Exception e) {
            log.error("Tika 解析失败: file={}", filePath, e);
            throw new DataAccessException("Parse failed: " + e.getMessage(), e);
        }

        return new DocumentParseResult(text, fileType, pageCount, charCount);
    }

    /** 依据 Tika 元数据 CONTENT_TYPE，回退文件扩展名推断文件类型。 */
    private String detectFileType(Metadata metadata, Path filePath) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType != null && !contentType.isEmpty()) {
            String lower = contentType.toLowerCase();
            if (lower.contains("pdf")) {
                return "pdf";
            }
            if (lower.contains("word") || lower.contains("docx")) {
                return "docx";
            }
            if (lower.contains("excel") || lower.contains("xlsx")) {
                return "xlsx";
            }
            if (lower.contains("powerpoint") || lower.contains("pptx")) {
                return "pptx";
            }
            if (lower.contains("html")) {
                return "html";
            }
            if (lower.contains("text/plain")) {
                return "txt";
            }
        }
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            return "pdf";
        }
        if (fileName.endsWith(".docx")) {
            return "docx";
        }
        if (fileName.endsWith(".xlsx")) {
            return "xlsx";
        }
        if (fileName.endsWith(".pptx")) {
            return "pptx";
        }
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "html";
        }
        if (fileName.endsWith(".txt")) {
            return "txt";
        }
        return "unknown";
    }

    /** PDF 页数推断（xmpTPg:NPages / Page-Count），非 PDF 或缺失时返回 1。 */
    private int detectPageCount(Metadata metadata, String fileType) {
        if ("pdf".equalsIgnoreCase(fileType)) {
            String pages = metadata.get("xmpTPg:NPages");
            if (pages == null || pages.isEmpty()) {
                pages = metadata.get("Page-Count");
            }
            if (pages != null && !pages.isEmpty()) {
                try {
                    return Integer.parseInt(pages);
                } catch (NumberFormatException e) {
                    log.warn("Invalid page count: {}", pages);
                }
            }
        }
        return 1;
    }
}
