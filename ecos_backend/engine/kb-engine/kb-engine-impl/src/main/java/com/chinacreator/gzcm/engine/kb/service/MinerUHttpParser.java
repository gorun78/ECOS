package com.chinacreator.gzcm.engine.kb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MinerU HTTP 解析器 — 非结构化文档解析的 OCR+版面分析通道。
 *
 * <p>对齐 ECOS-DESIGN-COG-05 §三：
 * <ul>
 *   <li>MinerU 是外部服务 (docker: mineru --serve 端口 8002)，由 infra 部署，不在本仓库 compose</li>
 *   <li>POST {mineru.base-url}/v1/parse body={file: base64, options:{ocr:true, layout:true, max_pages:500}}</li>
 *   <li>resp={text, blocks[], page_meta[], usage} → 映射为 ParseResult</li>
 *   <li>限制: 单文件 &lt; 50MB；失败抛 BusinessException（不静默）</li>
 * </ul>
 *
 * <p>由 {@link DocumentParserService#route()} 在 file ≥ 5MB 或 PDF 且含图片/扫描时调用。
 * 不与 Tika 通道冲突，两条路在 parse() 内按 file_features 路由。</p>
 *
 * @author ECOS KB Engine Team
 * @since 2026-09-02 (Wave-2C)
 */
@Service
public class MinerUHttpParser {

    private static final Logger log = LoggerFactory.getLogger(MinerUHttpParser.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** MinerU 服务基础 URL（由 infra 部署，非本仓库 Docker） */
    @Value("${ecos.mineru.base-url:http://localhost:8002}")
    private String mineruBaseUrl;

    /** 最大文件大小 50MB */
    private static final long MAX_FILE_SIZE = 50_485_760L;

    private final RestTemplate restTemplate;

    public MinerUHttpParser() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 使用 MinerU HTTP API 解析文件。
     *
     * @param filePath 待解析文件路径
     * @return ParseResult 包含 text / fileType / pageCount / charCount
     * @throws RuntimeException 文件过大或 MinerU 不可达时
     */
    public DocumentParserService.ParseResult parse(Path filePath) {
        // 1. 文件大小检查
        long size;
        try {
            size = Files.size(filePath);
        } catch (IOException e) {
            throw new RuntimeException("MinerU: cannot read file " + filePath, e);
        }
        if (size > MAX_FILE_SIZE) {
            throw new RuntimeException("MinerU: file too large (" + size + " bytes), max " + MAX_FILE_SIZE);
        }

        // 2. 读取文件并 base64
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("MinerU: read failed for " + filePath, e);
        }
        String base64File = Base64.getEncoder().encodeToString(fileBytes);

        // 3. 构造请求体
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("ocr", true);
        options.put("layout", true);
        options.put("max_pages", 500);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("file", base64File);
        payload.put("options", options);

        // 4. POST 到 MinerU
        String url = mineruBaseUrl + "/v1/parse";
        log.info("MinerU parse: file={}, size={}bytes, url={}", filePath.getFileName(), size, url);
        Map<String, Object> resp;
        try {
            String respStr = restTemplate.postForObject(url, payload, String.class);
            if (respStr == null || respStr.isEmpty()) {
                throw new RuntimeException("MinerU: empty response");
            }
            resp = mapper.readValue(respStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("MinerU: parse failed: " + e.getMessage(), e);
        }

        // 5. 解析响应
        String text = extractStr(resp, "text");
        int pageCount = extractInt(resp, "page_count", 1);
        if (pageCount == 0) {
            // 从 page_meta 数组推断
            Object pageMeta = resp.get("page_meta");
            if (pageMeta instanceof java.util.List) {
                pageCount = ((java.util.List<?>) pageMeta).size();
            }
        }
        int charCount = text != null ? text.length() : 0;

        log.info("MinerU parse OK: file={}, charCount={}, pages={}",
                filePath.getFileName(), charCount, pageCount);

        return new DocumentParserService.ParseResult(text, "pdf", pageCount, charCount);
    }

    /** 从 MinerU 响应中提取字符串字段 */
    private String extractStr(Map<String, Object> resp, String key) {
        Object val = resp.get(key);
        return val != null ? val.toString() : "";
    }

    /** 从 MinerU 响应中提取整数字段 */
    private int extractInt(Map<String, Object> resp, String key, int def) {
        Object val = resp.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return def;
    }

    /**
     * 检查 MinerU 服务是否可达（健康检查）。
     *
     * @return true 如果 GET /health 返回 200，否则 false
     */
    public boolean isAvailable() {
        try {
            String healthUrl = mineruBaseUrl + "/health";
            String resp = restTemplate.getForObject(healthUrl, String.class);
            return resp != null;
        } catch (Exception e) {
            log.debug("MinerU health check failed: {}", e.getMessage());
            return false;
        }
    }
}
