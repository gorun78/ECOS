package com.chinacreator.gzcm.runtime.access.document;

import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MinerU HTTP 解析器 — 非结构化文档解析的 OCR + 版面分析通道（B6-2 迁入 runtime-access）。
 *
 * <p>对齐 ECOS-DESIGN-COG-05 §三：
 * <ul>
 *   <li>MinerU 是外部服务（docker: mineru --serve 端口 8002），由 infra 部署，不在本仓库 compose；</li>
 *   <li>{@code POST {ecos.mineru.base-url}/v1/parse} body={@code {file: base64, options:{ocr,layout,max_pages}}}；</li>
 *   <li>resp={@code {text, page_count, page_meta[]}} → 映射为 {@link DocumentParseResult}；</li>
 *   <li>限制：单文件 &lt; 50MB；失败抛 {@link DataAccessException}（不静默、不返回空文本）。</li>
 * </ul>
 *
 * <p>HTTP 调用使用 JDK {@link HttpClient}（与 runtime-access {@code RestApiConnector} 同一客户端选型，
 * 不引入新依赖）。配置项 key {@code ecos.mineru.base-url} 与原 kb-engine 实现保持一致。
 *
 * @author ECOS Runtime Access Team
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

    /** MinerU 解析为 OCR 重活，请求超时放宽到 5 分钟 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    /** OCR + 版面分析请求的最大页数 */
    private static final int MAX_PAGES = 500;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 使用 MinerU HTTP API 解析文件。
     *
     * @param filePath 待解析文件路径
     * @return 解析结果（文本 / fileType=pdf / pageCount / charCount）
     * @throws DataAccessException 文件过大、不可读或 MinerU 不可达时
     */
    public DocumentParseResult parse(Path filePath) {
        long size;
        try {
            size = Files.size(filePath);
        } catch (IOException e) {
            log.error("MinerU: 无法读取文件 {}", filePath, e);
            throw new DataAccessException("MinerU: cannot read file " + filePath, e);
        }
        if (size > MAX_FILE_SIZE) {
            throw new DataAccessException("MinerU: file too large (" + size + " bytes), max " + MAX_FILE_SIZE);
        }

        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("MinerU: 文件读取失败 {}", filePath, e);
            throw new DataAccessException("MinerU: read failed for " + filePath, e);
        }
        String base64File = Base64.getEncoder().encodeToString(fileBytes);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("ocr", true);
        options.put("layout", true);
        options.put("max_pages", MAX_PAGES);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("file", base64File);
        payload.put("options", options);

        String url = resolveBaseUrl() + "/v1/parse";
        log.info("MinerU parse: file={}, size={}bytes, url={}", filePath.getFileName(), size, url);

        Map<String, Object> resp = postJson(url, payload);
        String text = extractStr(resp, "text");
        int pageCount = extractInt(resp, "page_count", 1);
        if (pageCount == 0) {
            Object pageMeta = resp.get("page_meta");
            if (pageMeta instanceof List<?> list) {
                pageCount = list.size();
            }
        }
        int charCount = text != null ? text.length() : 0;

        log.info("MinerU parse OK: file={}, charCount={}, pages={}", filePath.getFileName(), charCount, pageCount);
        return new DocumentParseResult(text, "pdf", pageCount, charCount);
    }

    /**
     * 检查 MinerU 服务是否可达（健康检查）。
     *
     * @return true 如果 GET /health 返回 2xx，否则 false
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveBaseUrl() + "/health"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.debug("MinerU health check failed: {}", e.getMessage());
            return false;
        }
    }

    /** 生效的 MinerU 基础 URL（未配置时回退 localhost:8002）。 */
    private String resolveBaseUrl() {
        return (mineruBaseUrl == null || mineruBaseUrl.isBlank()) ? "http://localhost:8002" : mineruBaseUrl;
    }

    /** POST JSON 并解析响应为 Map；非 2xx 或解析失败抛 {@link DataAccessException}。 */
    private Map<String, Object> postJson(String url, Map<String, Object> payload) {
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DataAccessException("MinerU: parse failed, HTTP " + response.statusCode());
            }
            String respStr = response.body();
            if (respStr == null || respStr.isEmpty()) {
                throw new DataAccessException("MinerU: empty response");
            }
            return mapper.readValue(respStr, new TypeReference<Map<String, Object>>() {});
        } catch (DataAccessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU: 解析调用失败 url={}", url, e);
            throw new DataAccessException("MinerU: parse failed: " + e.getMessage(), e);
        }
    }

    /** 从 MinerU 响应中提取字符串字段。 */
    private String extractStr(Map<String, Object> resp, String key) {
        Object val = resp.get(key);
        return val != null ? val.toString() : "";
    }

    /** 从 MinerU 响应中提取整数字段。 */
    private int extractInt(Map<String, Object> resp, String key, int def) {
        Object val = resp.get(key);
        if (val instanceof Number number) {
            return number.intValue();
        }
        return def;
    }
}
