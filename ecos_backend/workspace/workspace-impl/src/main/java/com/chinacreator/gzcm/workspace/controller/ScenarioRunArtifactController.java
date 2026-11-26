package com.chinacreator.gzcm.workspace.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.service.IObjectStorageService;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;

/**
 * 运行产物下载（V367 批次 F）：DB 只存引用，实体从对象存储按 storage_ref 读回。
 *
 * <p>读守卫复用 {@code scenario_analyses}；存储未装配 → 503，实体缺失 → 404（不返回空文件）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class ScenarioRunArtifactController {

    private final WorkspaceDataDao dao;
    private final ScenarioReadGuard readGuard;
    private final ObjectProvider<IObjectStorageService> storageProvider;

    public ScenarioRunArtifactController(WorkspaceDataDao dao, ScenarioReadGuard readGuard,
                                         ObjectProvider<IObjectStorageService> storageProvider) {
        this.dao = dao;
        this.readGuard = readGuard;
        this.storageProvider = storageProvider;
    }

    @GetMapping("/scenario-run-artifacts/{id}")
    public ResponseEntity<byte[]> download(@PathVariable("id") String id) {
        readGuard.assertReadable("scenario_analyses");
        List<Map<String, Object>> rows = dao.findRunArtifact(id);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "SCENARIO-ARTIFACT-404: 产物不存在: " + id);
        }
        IObjectStorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new BusinessException(503, "SCENARIO-ARTIFACT-503: 对象存储未装配");
        }
        Map<String, Object> row = rows.get(0);
        String ref = String.valueOf(row.get("storage_ref"));
        byte[] bytes = storage.getObject(ref);
        if (bytes == null) {
            throw new BusinessException(404, "SCENARIO-ARTIFACT-404: 实体缺失（引用=" + ref + "）");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + row.get("artifact_type") + "-" + id + ".json\"");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
