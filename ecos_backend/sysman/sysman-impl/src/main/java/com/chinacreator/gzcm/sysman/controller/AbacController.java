package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/abac")
public class AbacController {
    private static final Logger log = LoggerFactory.getLogger(AbacController.class);

    @Autowired(required = false)
    private IAbacPolicyService policyService;

    @GetMapping("/policies")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            if (policyService == null) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("data", Collections.emptyList());
                empty.put("total", 0);
                empty.put("page", page);
                empty.put("pageSize", pageSize);
                return ApiResponse.success(empty);
            }
            List<AbacPolicy> all = policyService.listPolicies();
            // filter
            if (keyword != null && !keyword.isEmpty()) {
                all = all.stream().filter(p ->
                    p.getPolicyName() != null && p.getPolicyName().contains(keyword)
                ).collect(java.util.stream.Collectors.toList());
            }
            // paginate
            int from = (page - 1) * pageSize;
            int to = Math.min(from + pageSize, all.size());
            List<AbacPolicy> pageList = from < all.size() ? all.subList(from, to) : Collections.emptyList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", pageList);
            result.put("total", all.size());
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询ABAC策略列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/policies/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            if (policyService == null) return ApiResponse.internalError("ABAC策略服务未就绪");
            AbacPolicy p = policyService.getPolicy(id);
            if (p == null) return ApiResponse.notFound("策略不存在");
            return ApiResponse.success(p);
        } catch (Exception e) {
            log.error("查询ABAC策略失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/policies")
    @RequirePermission(permission = "security:abac:manage")
    public ApiResponse<?> create(@RequestBody AbacPolicy policy) {
        try {
            if (policyService == null) return ApiResponse.internalError("ABAC策略服务未就绪");
            policy.setPolicyId(UUID.randomUUID().toString().replace("-", ""));
            AbacPolicy created = policyService.createPolicy(policy);
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建ABAC策略失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/policies/{id}")
    @RequirePermission(permission = "security:abac:manage")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody AbacPolicy policy) {
        try {
            if (policyService == null) return ApiResponse.internalError("ABAC策略服务未就绪");
            policy.setPolicyId(id);
            AbacPolicy updated = policyService.updatePolicy(policy);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新ABAC策略失败", e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/policies/{id}")
    @RequirePermission(permission = "security:abac:manage")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            if (policyService == null) return ApiResponse.internalError("ABAC策略服务未就绪");
            policyService.deletePolicy(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", true);
            return ApiResponse.success(m);
        } catch (Exception e) {
            log.error("删除ABAC策略失败", e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }
}
