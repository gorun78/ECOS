package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.dto.IngestRunRequest;
import com.chinacreator.gzcm.engine.data.dto.IngestScheduleRequest;
import com.chinacreator.gzcm.engine.data.service.DataIngestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据采集 REST API — 采集型管道（SOURCE_JDBC → SINK_MINIO，MinIO 近源库）快捷入口。
 * <p>
 * 端点：
 * <ul>
 *   <li>POST /api/v1/datanet/ingest/run            — 即时采集：对每表创建/复用采集管道并提交 runtime-task</li>
 *   <li>GET  /api/v1/datanet/ingest/status/{taskId} — 采集任务状态（委托 runtime-task，前端轮询）</li>
 *   <li>POST /api/v1/datanet/ingest/schedule        — 保存定时采集策略（复用 metadata_config.ingest）</li>
 *   <li>GET  /api/v1/datanet/ingest/schedule/{datasourceId} — 查询定时采集策略</li>
 *   <li>GET  /api/v1/datanet/ingest/targets         — 可用目标数据湖（MINIO 类型数据源）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/datanet/ingest")
public class DataIngestController {

    private final DataIngestService ingestService;

    public DataIngestController(DataIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /** 即时采集（创建/复用采集型管道 + 提交执行，返回各表 taskId 供轮询）。 */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> run(@RequestBody IngestRunRequest request) {
        return ApiResponse.success(ingestService.runOnce(request.getDatasourceId(), request.getTableNames()));
    }

    /** 采集任务状态（前端轮询进度/结果）。 */
    @GetMapping("/status/{taskId}")
    public ApiResponse<Map<String, Object>> status(@PathVariable String taskId) {
        return ApiResponse.success(ingestService.getTaskStatus(taskId));
    }

    /** 保存定时采集策略（cron 为空 = 停用）。 */
    @PostMapping("/schedule")
    public ApiResponse<Map<String, Object>> saveSchedule(@RequestBody IngestScheduleRequest request) {
        return ApiResponse.success(ingestService.saveSchedule(
                request.getDatasourceId(), request.getTableNames(), request.getCron()));
    }

    /** 查询定时采集策略。 */
    @GetMapping("/schedule/{datasourceId}")
    public ApiResponse<Map<String, Object>> getSchedule(@PathVariable String datasourceId) {
        return ApiResponse.success(ingestService.getSchedule(datasourceId));
    }

    /** 可用目标数据湖（MINIO 类型数据源，近源库默认目标）。 */
    @GetMapping("/targets")
    public ApiResponse<List<Map<String, Object>>> targets() {
        return ApiResponse.success(ingestService.listTargets());
    }
}
