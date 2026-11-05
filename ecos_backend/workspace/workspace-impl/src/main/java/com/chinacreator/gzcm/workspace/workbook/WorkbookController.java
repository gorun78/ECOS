package com.chinacreator.gzcm.workspace.workbook;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.dto.WorkbookEnvStatusVO;
import com.chinacreator.gzcm.workspace.dto.WorkbookExecutionRecordVO;
import com.chinacreator.gzcm.workspace.dto.WorkbookHealthVO;
import com.chinacreator.gzcm.workspace.dto.WorkbookSessionVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workbook")
public class WorkbookController {

    private static final Logger log = LoggerFactory.getLogger(WorkbookController.class);

    private final WorkbookExecutionEngine engine;
    private final RRuntime rRuntime;

    private final Cache<String, List<WorkbookExecutionRecordVO>> history = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    public WorkbookController(WorkbookExecutionEngine engine, RRuntime rRuntime) {
        this.engine = engine;
        this.rRuntime = rRuntime;
    }

    public static class ExecuteRequest {
        private String language;
        private String code;
        private String sessionId;

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    @Operation(summary = "执行代码", description = "执行 SQL/Python/R 代码并返回结果")
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody ExecuteRequest req) {
        if (req.getLanguage() == null || req.getLanguage().isBlank()) {
            return ApiResponse.badRequest("language is required");
        }
        if (req.getCode() == null || req.getCode().isBlank()) {
            return ApiResponse.badRequest("code is required");
        }

        String sessionId = req.getSessionId() != null ? req.getSessionId() : "default";

        try {
            var result = engine.execute(req.getLanguage(), req.getCode(), sessionId);

            history.get(sessionId, k -> new ArrayList<>()).add(new WorkbookExecutionRecordVO(
                    UUID.randomUUID().toString().substring(0, 8),
                    req.getLanguage(),
                    req.getCode(),
                    sessionId,
                    LocalDateTime.now().toString(),
                    result));

            return ApiResponse.success(result);

        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Workbook execute error", e);
            return ApiResponse.internalError("Execution failed");
        }
    }

    @Operation(summary = "会话列表", description = "获取所有活动会话列表")
    @GetMapping("/sessions")
    public ApiResponse<List<WorkbookSessionVO>> sessions() {
        List<WorkbookSessionVO> list = new ArrayList<>();
        for (var entry : history.asMap().entrySet()) {
            list.add(new WorkbookSessionVO(entry.getKey(), entry.getValue().size()));
        }
        return ApiResponse.success(list);
    }

    @Operation(summary = "历史记录", description = "获取指定会话的历史执行记录")
    @GetMapping("/history")
    public ApiResponse<List<WorkbookExecutionRecordVO>> history(
            @RequestParam(value = "sessionId", defaultValue = "default") String sessionId) {
        var records = history.getIfPresent(sessionId);
        return ApiResponse.success(records != null ? records : List.of());
    }

    @Operation(summary = "运行环境状态", description = "检查各语言运行环境的可用性")
    @GetMapping("/health")
    public ApiResponse<WorkbookHealthVO> health() {
        WorkbookEnvStatusVO pyInfo;
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            Process process = pb.start();
            boolean ok = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            pyInfo = new WorkbookEnvStatusVO(ok && process.exitValue() == 0, null);
        } catch (Exception e) {
            pyInfo = new WorkbookEnvStatusVO(false, e.getMessage());
        }

        return ApiResponse.success(new WorkbookHealthVO(
                new WorkbookEnvStatusVO(true, null), pyInfo, rRuntime.availabilityInfo()));
    }
}
