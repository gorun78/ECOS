package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;
import com.chinacreator.gzcm.sysman.config.service.ISystemParamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/config/params")
public class SystemParamController {
    private static final Logger log = LoggerFactory.getLogger(SystemParamController.class);
    @Autowired(required = false)
    private ISystemParamService paramService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> r = new LinkedHashMap<>();
            if (paramService == null) {
                r.put("data", Collections.emptyList()); r.put("total", 0);
                r.put("page", page); r.put("pageSize", pageSize);
                return ApiResponse.success(r);
            }
            Map<String, Object> cond = new HashMap<>();
            if (keyword != null) cond.put("keyword", keyword);
            List<SystemParam> all = paramService.listParams(cond);
            int from = (page - 1) * pageSize, to = Math.min(from + pageSize, all.size());
            r.put("data", from < all.size() ? all.subList(from, to) : Collections.emptyList());
            r.put("total", all.size()); r.put("page", page); r.put("pageSize", pageSize);
            return ApiResponse.success(r);
        } catch (Throwable e) {
            log.error("查询系统参数失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            if (paramService == null) return ApiResponse.internalError("服务未就绪");
            SystemParam p = paramService.getParam(id);
            return p == null ? ApiResponse.notFound("参数不存在") : ApiResponse.success(p);
        } catch (Throwable e) {
            log.error("查询系统参数失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody SystemParam param) {
        try {
            if (paramService == null) return ApiResponse.internalError("服务未就绪");
            param.setParamId(UUID.randomUUID().toString().replace("-", ""));
            return ApiResponse.success(paramService.createParam(param, "admin"));
        } catch (Throwable e) {
            log.error("创建系统参数失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody SystemParam param) {
        try {
            if (paramService == null) return ApiResponse.internalError("服务未就绪");
            return ApiResponse.success(paramService.updateParam(id, param, "admin"));
        } catch (Throwable e) {
            log.error("更新系统参数失败", e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            if (paramService == null) return ApiResponse.internalError("服务未就绪");
            paramService.deleteParam(id, "admin");
            return ApiResponse.success(Map.of("success", true));
        } catch (Throwable e) {
            log.error("删除系统参数失败", e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }
}
