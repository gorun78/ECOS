package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.QueryHistoryService;
import com.chinacreator.gzcm.workspace.QueryRecord;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 查询历史控制器。
 *
 * <pre>
 * GET  /api/query/history?limit=20  → 返回最近查询记录
 * DELETE /api/query/history/{id}     → 删除指定记录
 * </pre>
 */
@RestController
@RequestMapping("/api/query")
public class QueryHistoryController {

    private final QueryHistoryService historyService;

    public QueryHistoryController(QueryHistoryService historyService) {
        this.historyService = historyService;
    }

    /**
     * 获取查询历史列表。
     */
    @GetMapping("/history")
    public ApiResponse<List<QueryRecord>> list(@RequestParam(defaultValue = "20") int limit) {
        List<QueryRecord> records = historyService.getHistory(Math.min(limit, 100));
        return ApiResponse.success(records);
    }

    /**
     * 删除指定历史记录。
     */
    @DeleteMapping("/history/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        boolean ok = historyService.delete(id);
        if (ok) {
            return ApiResponse.success(Map.of("deleted", true, "id", id));
        }
        return ApiResponse.error(404, "记录不存在: " + id);
    }
}
