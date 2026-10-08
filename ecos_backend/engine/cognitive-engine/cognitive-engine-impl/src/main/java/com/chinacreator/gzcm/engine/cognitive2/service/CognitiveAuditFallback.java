package com.chinacreator.gzcm.engine.cognitive2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * 认知审计本地兜底通道（F05-15 / X-39 fail-closed 第二级）。
 *
 * <p>铁律 §2.4#5 审计 fail-closed 三段式：主通道（runtime-event {@code ecos.audit}）不可用 →
 * 写本地兜底文件（JSONL 追加，供运维重放补投）；兜底也失败 → 调用方必须拒绝业务动作（抛异常），
 * 禁止"log.warn + continue"式假审计。</p>
 */
@Component
public class CognitiveAuditFallback {

    private static final Logger log = LoggerFactory.getLogger(CognitiveAuditFallback.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 兜底文件路径（相对启动目录可配）；每行一条 JSON 审计事件。 */
    @Value("${ecos.cognitive.audit-fallback-file:logs/cognitive-audit-fallback.jsonl}")
    private String fallbackFile;

    /** 包内可见，便于单测替换落盘路径。 */
    void setFallbackFile(String fallbackFile) {
        this.fallbackFile = fallbackFile;
    }

    /**
     * 追加写入一条审计事件（同步串行，审计量小无需异步）。
     *
     * @param event 审计事件（action/resource/result/detail/userId/timestamp）
     * @throws IOException 落盘失败（调用方据此 fail-closed 拒绝业务动作）
     */
    public synchronized void write(Map<String, Object> event) throws IOException {
        Path path = Path.of(fallbackFile);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String line = MAPPER.writeValueAsString(event) + System.lineSeparator();
        Files.writeString(path, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        log.warn("审计主通道不可用，已落本地兜底文件: {}", path.toAbsolutePath());
    }
}
