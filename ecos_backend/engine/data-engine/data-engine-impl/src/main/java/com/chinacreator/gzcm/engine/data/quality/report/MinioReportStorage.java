package com.chinacreator.gzcm.engine.data.quality.report;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;

/**
 * DQ 报告 MinIO 存储适配（PMO-48-D T15）。
 *
 * <p>runtime-access 的 {@link MinioStorageService} 固化为 bucket {@code ecos-datalake}，
 * 不暴露 bucket 参数；按架构铁律 2.5 #6（补强而非自建），本类<b>不</b>直接
 * {@code new MinioClient}，而是基于既有 {@link MinioStorageService} 做<b>参数化薄封装</b>：
 * 固定 bucket {@code dq-reports} 前缀路径 {@code dq-reports/}，确保与既有 datalake
 * 对象隔离。
 *
 * <p>上传失败<b>不阻塞主流程</b>（MinIO 不可达时 PDF 退化为 PG 内嵌 HTML 仍可查），
 * 仅日志 warn + 返回 null。
 *
 * @author PMO-48-D T15
 */
@Component("ecosDqReportMinioStorage")
public class MinioReportStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioReportStorage.class);

    /** DQ 报告专用 object key 前缀（与 ecos-datalake bucket 对象隔离） */
    private static final String REPORT_OBJECT_PREFIX = "dq-reports/";

    private final MinioStorageService storageService;

    public MinioReportStorage(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 上传报告字节到 MinIO。
     *
     * @param objectKey 完整对象名（如 {@code dq-reports/daily_ALL_20260910.html}）
     * @param content   报告字节
     * @param contentType MIME（HTML/PDF）
     * @return 成功时的完整 object key；MinIO 不可达 / 失败时返回 null（不抛，主流程继续）
     */
    public String putReport(String objectKey, byte[] content, String contentType) {
        if (objectKey == null || objectKey.isBlank() || content == null || content.length == 0) {
            log.warn("DqReport MinIO put skipped: invalid args key={}", objectKey);
            return null;
        }
        // 兼容：调用方传相对路径，统一拼上 rq 前缀
        String fullKey = objectKey.startsWith(REPORT_OBJECT_PREFIX)
                ? objectKey
                : REPORT_OBJECT_PREFIX + objectKey;
        try {
            Map<String, Object> r = storageService.putObject(fullKey, content, contentType);
            String status = String.valueOf(r.get("status"));
            if ("success".equals(status)) {
                log.info("DqReport MinIO put ok: {}/{}", r.get("bucket"), fullKey);
                return fullKey;
            }
            log.warn("DqReport MinIO put degraded: key={}, msg={}", fullKey, r.get("message"));
            return null;
        } catch (Exception e) {
            // 不回抛：主流程不阻塞；报告已落 dq_report 行（HTML 内嵌仍可视）
            log.warn("DqReport MinIO put failed (ignored): key={}, error={}", fullKey, e.getMessage());
            return null;
        }
    }

    /**
     * 从 MinIO 读报告字节（PDF 场景用；本 Task 未启用，保留供 Phase 5 调用）。
     *
     * @param objectKey 完整对象名（如 {@code dq-reports/daily_ALL_20260910.pdf}）
     * @return 字节；对象不存在返 null
     */
    public byte[] getReport(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException("报告对象 key 不能为空");
        }
        byte[] data = storageService.getObject(objectKey);
        return data;
    }
}
