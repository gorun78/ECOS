package com.chinacreator.gzcm.engine.kb.service;

import org.apache.tika.Tika;
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

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    public ParseResult parse(Path filePath) throws Exception {
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

            // Fallback for txt files if Tika returns empty
            if (text.isEmpty() && "txt".equalsIgnoreCase(fileType)) {
                text = Files.readString(filePath);
                charCount = text.length();
            }

            // If still empty, keep empty text (scanned PDF TODO)
            if (text.isEmpty()) {
                log.warn("Parsed text is empty for file: {}", filePath);
            }

        } catch (Exception e) {
            throw new RuntimeException("Parse failed: " + e.getMessage(), e);
        }

        return new ParseResult(text, fileType, pageCount, charCount);
    }

    private String detectFileType(Metadata metadata, Path filePath) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType != null && !contentType.isEmpty()) {
            String lower = contentType.toLowerCase();
            if (lower.contains("pdf")) return "pdf";
            if (lower.contains("word") || lower.contains("docx")) return "docx";
            if (lower.contains("excel") || lower.contains("xlsx")) return "xlsx";
            if (lower.contains("powerpoint") || lower.contains("pptx")) return "pptx";
            if (lower.contains("html")) return "html";
            if (lower.contains("text/plain")) return "txt";
        }
        // fallback to extension
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) return "pdf";
        if (fileName.endsWith(".docx")) return "docx";
        if (fileName.endsWith(".xlsx")) return "xlsx";
        if (fileName.endsWith(".pptx")) return "pptx";
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) return "html";
        if (fileName.endsWith(".txt")) return "txt";
        return "unknown";
    }

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

    public static class ParseResult {
        private String text;
        private String fileType;
        private int pageCount;
        private int charCount;

        public ParseResult() {
        }

        public ParseResult(String text, String fileType, int pageCount, int charCount) {
            this.text = text;
            this.fileType = fileType;
            this.pageCount = pageCount;
            this.charCount = charCount;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public int getPageCount() {
            return pageCount;
        }

        public void setPageCount(int pageCount) {
            this.pageCount = pageCount;
        }

        public int getCharCount() {
            return charCount;
        }

        public void setCharCount(int charCount) {
            this.charCount = charCount;
        }
    }
}
