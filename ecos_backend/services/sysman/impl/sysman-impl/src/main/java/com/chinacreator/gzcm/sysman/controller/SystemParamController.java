package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;
import com.chinacreator.gzcm.sysman.config.service.ISystemParamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统参数 REST API — {@code /api/v1/system/config/params} 族属主端点。
 *
 * <p><b>#142/B4-3e-53 收口 D-B4-83</b>：与 {@code ConfigController} 同批同修法——
 * 原口径「{@code ISystemParamService} 未装配 ⇒ 列表回 200＋空集」把依赖缺失谎报成无数据，
 * 其余四枚腿折成 {@code internalError(500)} 把缺装配谎报成本服务故障，两侧都违详设 §4.7.2。
 * 现统一抛携带 503＋机读码 {@code ECOS-SYS-503} 的显式不可用载体；
 * <b>服务在位且参数表为空仍回 200＋空集</b>（真实空结果不动）。</p>
 */
@RestController
@RequestMapping("/api/v1/system/config/params")
public class SystemParamController {
    private static final Logger log = LoggerFactory.getLogger(SystemParamController.class);

    private final ObjectProvider<ISystemParamService> paramProvider;

    public SystemParamController(ObjectProvider<ISystemParamService> paramProvider) {
        this.paramProvider = paramProvider;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        ISystemParamService service = paramProvider.getIfAvailable();
        if (service == null) {
            log.error("ISystemParamService 未装配，系统参数列表不可用（fail-closed）");
            throw ConfigController.unavailability("系统参数服务 ISystemParamService");
        }
        try {
            Map<String, Object> r = new LinkedHashMap<>();
            Map<String, Object> cond = new HashMap<>();
            if (keyword != null) cond.put("keyword", keyword);
            List<SystemParam> all = service.listParams(cond);
            int from = (page - 1) * pageSize, to = Math.min(from + pageSize, all.size());
            r.put("data", from < all.size() ? all.subList(from, to) : Collections.emptyList());
            r.put("total", all.size()); r.put("page", page); r.put("pageSize", pageSize);
            return ApiResponse.success(r);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("查询系统参数失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        ISystemParamService service = paramProvider.getIfAvailable();
        if (service == null) {
            log.error("ISystemParamService 未装配，系统参数 {} 查询不可用（fail-closed）", id);
            throw ConfigController.unavailability("系统参数服务 ISystemParamService");
        }
        try {
            SystemParam p = service.getParam(id);
            return p == null ? ApiResponse.notFound("参数不存在") : ApiResponse.success(p);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("查询系统参数失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody SystemParam param) {
        ISystemParamService service = paramProvider.getIfAvailable();
        if (service == null) {
            log.error("ISystemParamService 未装配，系统参数创建不可用（fail-closed）");
            throw ConfigController.unavailability("系统参数服务 ISystemParamService");
        }
        try {
            param.setParamId(UUID.randomUUID().toString().replace("-", ""));
            return ApiResponse.success(service.createParam(param, "admin"));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("创建系统参数失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody SystemParam param) {
        ISystemParamService service = paramProvider.getIfAvailable();
        if (service == null) {
            log.error("ISystemParamService 未装配，系统参数 {} 更新不可用（fail-closed）", id);
            throw ConfigController.unavailability("系统参数服务 ISystemParamService");
        }
        try {
            return ApiResponse.success(service.updateParam(id, param, "admin"));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("更新系统参数失败", e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        ISystemParamService service = paramProvider.getIfAvailable();
        if (service == null) {
            log.error("ISystemParamService 未装配，系统参数 {} 删除不可用（fail-closed）", id);
            throw ConfigController.unavailability("系统参数服务 ISystemParamService");
        }
        try {
            service.deleteParam(id, "admin");
            return ApiResponse.success(Map.of("success", true));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("删除系统参数失败", e);
            return ApiResponse.internalError("删除失败");
        }
    }
}
