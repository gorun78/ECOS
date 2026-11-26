package com.chinacreator.gzcm.workspace.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.common.service.IObjectStorageService;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 场景运行产物写通道（V367 批次 F）。
 *
 * <p>实体字节落对象存储（MinIO / PG 实现二选一，由既有 {@link IObjectStorageService} 决定），
 * DB 只存 {@code storage_ref/digest/size_bytes}。存储不可用时<b>不写引用行</b>，
 * 只记 WARN —— 不产生「有引用无实体」的假产物。</p>
 */
@Component
public class ScenarioRunArtifactWriter {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRunArtifactWriter.class);

    private final WorkspaceDataDao dao;
    private final ObjectProvider<IObjectStorageService> storageProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScenarioRunArtifactWriter(WorkspaceDataDao dao,
                                     ObjectProvider<IObjectStorageService> storageProvider) {
        this.dao = dao;
        this.storageProvider = storageProvider;
    }

    /** 写一段 JSON 产物；失败只记日志，不打断运行主链路。 */
    public void writeJson(String runId, String scenarioId, String artifactType, Object payload) {
        if (payload == null) return;
        IObjectStorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.warn("场景产物：对象存储未装配，跳过 {} run={}", artifactType, runId);
            return;
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            String key = "scenario-runs/" + scenarioId + "/" + runId + "/"
                    + artifactType.toLowerCase() + "-" + UUID.randomUUID() + ".json";
            storage.putObject(key, bytes, "application/json");
            // 主键列 VARCHAR(36)：直接用 UUID（带 art- 前缀会 40 字符撞长度）
            dao.insertRunArtifact(UUID.randomUUID().toString(), runId, scenarioId,
                    artifactType, key, sha256Hex(bytes), bytes.length);
        } catch (RuntimeException e) {
            log.warn("场景产物写入失败 type={} run={} err={}", artifactType, runId, e.getMessage());
        } catch (Exception e) {
            log.warn("场景产物序列化失败 type={} run={} err={}", artifactType, runId, e.getMessage());
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "sha256:unavailable";
        }
    }

    /** 供测试/调用方读取 UTF-8 字节（避免重复实现）。 */
    static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}
