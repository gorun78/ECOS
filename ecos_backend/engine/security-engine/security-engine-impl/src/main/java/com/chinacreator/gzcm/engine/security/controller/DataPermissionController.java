package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;
import com.chinacreator.gzcm.sysman.datapermission.service.IDataPermissionPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/data-permission")
public class DataPermissionController {
    private static final Logger log = LoggerFactory.getLogger(DataPermissionController.class);
    @Autowired(required = false)
    private IDataPermissionPolicyService policyService;

    @GetMapping("/policies")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String policyType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> r = new LinkedHashMap<>();
            if (policyService == null) {
                r.put("data", Collections.emptyList()); r.put("total", 0);
                r.put("page", page); r.put("pageSize", pageSize);
                return ApiResponse.success(r);
            }
            Map<String, Object> cond = new HashMap<>();
            if (keyword != null) cond.put("keyword", keyword);
            if (policyType != null) cond.put("policyType", policyType);
            List<DataPermissionPolicy> all = policyService.listPolicies(cond);
            int from = (page - 1) * pageSize, to = Math.min(from + pageSize, all.size());
            r.put("data", from < all.size() ? all.subList(from, to) : Collections.emptyList());
            r.put("total", all.size()); r.put("page", page); r.put("pageSize", pageSize);
            return ApiResponse.success(r);
        } catch (Throwable e) {
            log.error("查询数据权限策略失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/policies")
    public ApiResponse<?> create(@RequestBody DataPermissionPolicy policy) {
        try {
            if (policyService == null) return ApiResponse.internalError("服务未就绪");
            policy.setPolicyId(UUID.randomUUID().toString().replace("-", ""));
            return ApiResponse.success(policyService.createPolicy(policy, "admin"));
        } catch (Throwable e) {
            log.error("创建数据权限策略失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody DataPermissionPolicy policy) {
        try {
            if (policyService == null) return ApiResponse.internalError("服务未就绪");
            policy.setPolicyId(id);
            return ApiResponse.success(policyService.updatePolicy(policy, "admin"));
        } catch (Throwable e) {
            log.error("更新数据权限策略失败", e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            if (policyService == null) return ApiResponse.internalError("服务未就绪");
            policyService.deletePolicy(id);
            return ApiResponse.success(Map.of("success", true));
        } catch (Throwable e) {
            log.error("删除数据权限策略失败", e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }
}
