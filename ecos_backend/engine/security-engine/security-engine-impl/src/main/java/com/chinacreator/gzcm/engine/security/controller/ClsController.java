package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SecurityErrorCode;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/security/cls", "/api/v1/security/cls"})
public class ClsController {

    private static final Logger log = LoggerFactory.getLogger(ClsController.class);

    /** §4.3.3 {@code ECOS-SEC-402}（CLS 允许列为空集）的机读码值，只在新 GET 别名上出现。单源取 {@link SecurityErrorCode#ECOS_SEC_402}。 */
    public static final String ERROR_CLS_EMPTY_DENY = SecurityErrorCode.ECOS_SEC_402;

    private final ColumnLevelSecurityServiceImpl clsService;

    public ClsController(ColumnLevelSecurityServiceImpl clsService) {
        this.clsService = clsService;
    }

    @PostMapping("/columns")
    public ApiResponse<Map<String, Object>> getColumns(@RequestBody Map<String, Object> body) {
        String tableName = (String) body.get("tableName");
        String userId = (String) body.get("userId");
        if (tableName == null || tableName.isBlank()) {
            return ApiResponse.badRequest("tableName不能为空");
        }
        if (userId == null || userId.isBlank()) {
            return ApiResponse.badRequest("userId不能为空");
        }
        @SuppressWarnings("unchecked")
        List<String> allColumns = (List<String>) body.get("allColumns");
        try {
            return ApiResponse.success(clsService.getColumns(tableName, userId, allColumns));
        } catch (Exception e) {
            log.error("CLS列权限查询失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    /**
     * #142/B4-1 {@code GET /security/cls/columns} —— 设计 §4.3.2「既有端点兼容承诺」行
     * （L2593：签名不变｜<b>新增 mode</b>；<b>空集仍按 DENY 解释</b>）的 GET 兼容别名。
     *
     * <p><b>与 POST 的关系（只增不改）</b>：{@link #getColumns(Map)} 一个字节未动，
     * 本方法只是把同一份 service 调用（同 {@code tableName/userId/allColumns} 三字段、
     * 同 {@code data} 四键）另开一条只读通道；差异只有设计点名的新增 {@code mode}
     * 与配套的可机读 DENY 解释位。故既有 POST 调用方零影响，新调用方按契约取 {@code mode}。</p>
     *
     * <p><b>{@code mode} 取值口径</b>：与单源裁决面同形——{@code SecurityDecisionService}
     * 组装 columns 部件时写 {@code mode="allow"}（L135），DDL 侧 {@code ecos_cls_policy.mode}
     * 默认 {@code 'deny'}（V164 L72）。本别名按「可读列非空 = allow，空集 = deny」判定，
     * 与 §4.3.3 {@code ECOS-SEC-402}（CLS 允许列为空集 → DENY）一致。</p>
     *
     * <p><b>为什么空集仍返 200 而非 403</b>：契约原文对这一行写的是「<b>签名不变</b>」+
     * 「空集<b>仍按 DENY 解释</b>」——「解释」义务在消费方，把 HTTP 状态改成 403 就是改签名、
     * 破坏兼容承诺（AGENTS.md「Don't change existing API paths or signatures」）。
     * 折中做法：{@code data.effectiveDecision="DENY"} + {@code data.errorCode="ECOS-SEC-402"}
     * 让调用方能机读，同时 {@code denyIfEmpty=true} 把「空集即拒绝」写进响应本身，
     * 不留「空列表＝放行」的歧义面。</p>
     *
     * <p><b>登记在案的分歧</b>（不改，只记）：legacy 契约册
     * {@code docs/30-设计/legacy-plans/api-contract.md} L94 把该端点 {@code data} 写作
     * {@code {allowedColumns[]}}，而现网实现与本体别名都是 {@code visibleColumns}。
     * 本批不新造第三套键名做「别名」，该命名分歧已回写差距文档，由契约批（#143）定名单点。</p>
     */
    @GetMapping("/columns")
    public ApiResponse<Map<String, Object>> getColumnsByGet(
            @RequestParam String tableName,
            @RequestParam String userId,
            @RequestParam(required = false) List<String> allColumns) {
        if (tableName == null || tableName.isBlank()) {
            return ApiResponse.badRequest("tableName不能为空");
        }
        if (userId == null || userId.isBlank()) {
            return ApiResponse.badRequest("userId不能为空");
        }
        try {
            Map<String, Object> base = clsService.getColumns(tableName, userId, allColumns);
            Map<String, Object> out = new LinkedHashMap<>(base);
            Object visible = out.get("visibleColumns");
            boolean empty = !(visible instanceof List<?> list) || list.isEmpty();
            out.put("mode", empty ? "deny" : "allow");
            out.put("denyIfEmpty", true);
            out.put("effectiveDecision", empty ? "DENY" : "ALLOW");
            if (empty) {
                out.put("errorCode", ERROR_CLS_EMPTY_DENY);
                out.put("message", "当前角色无该资产可读列（空集按 DENY 解释）");
            }
            return ApiResponse.success(out);
        } catch (Exception e) {
            log.error("CLS列权限查询失败(GET)", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/policies")
    public ApiResponse<List<Map<String, Object>>> listPolicies(
            @RequestParam(required = false) String tableName) {
        return ApiResponse.success(clsService.listPolicies(tableName));
    }

    @GetMapping("/policies/{id}")
    public ApiResponse<?> getPolicy(@PathVariable String id) {
        Map<String, Object> policy = clsService.getPolicy(id);
        if (policy == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(policy);
    }

    @PostMapping("/policies")
    public ApiResponse<?> createPolicy(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(clsService.createPolicy(body));
        } catch (Exception e) {
            log.error("创建CLS策略失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<?> updatePolicy(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> updated = clsService.updatePolicy(id, body);
        if (updated == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<?> deletePolicy(@PathVariable String id) {
        boolean deleted = clsService.deletePolicy(id);
        if (!deleted) return ApiResponse.notFound("策略不存在: " + id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", id);
        return ApiResponse.success(result);
    }
}
