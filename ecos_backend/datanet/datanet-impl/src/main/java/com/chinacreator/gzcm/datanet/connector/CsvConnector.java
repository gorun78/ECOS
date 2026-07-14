package com.chinacreator.gzcm.datanet.connector;

import com.chinacreator.gzcm.datanet.model.DataResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CSV/Excel 连接器 — 读取文件系统中的 CSV 文件作为数据源。
 * <p>
 * 支持存量客户从旧系统导出的 CSV/TSV 文件直接接入 ECOS Pipeline。
 * connectionConfig JSON 格式：
 * <pre>{@code
 * {
 *   "filePath": "/data/migrate/projects.csv",
 *   "delimiter": ",",
 *   "hasHeader": true,
 *   "encoding": "UTF-8"
 * }
 * }</pre>
 *
 * @author DataBridge Datanet Team
 */
@Component
public class CsvConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(CsvConnector.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String supportedType() {
        return "SOURCE_CSV";
    }

    @Override
    public boolean testConnection(String connectionConfig) {
        try {
            Map<String, Object> config = parseConfig(connectionConfig);
            String filePath = (String) config.get("filePath");
            if (filePath == null || filePath.isBlank()) {
                log.warn("CSV connection test failed: filePath is empty");
                return false;
            }
            Path path = Path.of(filePath);
            boolean exists = Files.exists(path) && Files.isReadable(path);
            if (!exists) {
                log.warn("CSV connection test failed: file not found or not readable: {}", filePath);
            }
            return exists;
        } catch (Exception e) {
            log.warn("CSV connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<DataResource> listResources(String connectionConfig, String orgId, String orgName) {
        List<DataResource> resources = new ArrayList<>();
        Map<String, Object> config = parseConfig(connectionConfig);

        String filePath = (String) config.get("filePath");
        if (filePath == null || filePath.isBlank()) {
            log.warn("CSV listResources: filePath is empty");
            return resources;
        }

        Path path = Path.of(filePath);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            log.warn("CSV listResources: file not found or not readable: {}", filePath);
            return resources;
        }

        // 单个 CSV 文件或目录
        List<Path> csvFiles = new ArrayList<>();
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(path, "*.{csv,tsv,txt}")) {
                for (Path p : ds) {
                    csvFiles.add(p);
                }
            } catch (IOException e) {
                log.error("Failed to list CSV directory: {}", e.getMessage());
            }
        } else {
            csvFiles.add(path);
        }

        String delimiter = (String) config.getOrDefault("delimiter", ",");
        boolean hasHeader = (boolean) config.getOrDefault("hasHeader", true);

        for (Path csvFile : csvFiles) {
            DataResource r = new DataResource();
            r.setResourceId(UUID.randomUUID().toString().replace("-", ""));
            r.setResourceName(stripExtension(csvFile.getFileName().toString()));
            r.setResourceType("FILE");
            r.setOrgId(orgId);
            r.setOrgName(orgName);
            r.setSourcePath(csvFile.toAbsolutePath().toString());
            r.setDescription("CSV data file: " + csvFile.getFileName());
            r.setStatus("ACTIVE");
            r.setCreateTime(LocalDateTime.now());
            r.setUpdateTime(LocalDateTime.now());

            // 读取 header 行获取字段数量
            try {
                List<String> headers = readHeaders(csvFile, delimiter, hasHeader);
                r.setFieldCount(headers.size());
                r.setDescription("CSV file with columns: " + String.join(", ", headers));
            } catch (IOException e) {
                log.warn("Failed to read CSV headers for {}: {}", csvFile, e.getMessage());
                r.setFieldCount(0);
            }

            resources.add(r);
        }

        return resources;
    }

    @Override
    public List<Map<String, Object>> queryPreview(String connectionConfig, String tableName, int limit) {
        log.debug("CSV preview not supported for: {}", tableName);
        return Collections.emptyList();
    }

    /**
     * 读取 CSV 文件的表头（第一行），用于推断字段列表。
     */
    private List<String> readHeaders(Path file, String delimiter, boolean hasHeader) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.isBlank()) {
                return Collections.emptyList();
            }
            if (hasHeader) {
                return Arrays.stream(firstLine.split(delimiter, -1))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            } else {
                // 无 header，生成 C1, C2, C3...
                int colCount = firstLine.split(delimiter, -1).length;
                List<String> cols = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    cols.add("C" + i);
                }
                return cols;
            }
        }
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String connectionConfig) {
        try {
            return mapper.readValue(connectionConfig, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CSV connection config JSON: " + connectionConfig, e);
        }
    }
}
