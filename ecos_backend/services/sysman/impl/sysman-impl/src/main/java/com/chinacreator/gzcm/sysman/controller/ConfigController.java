package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.entity.ConfigVersion;
import com.chinacreator.gzcm.sysman.config.service.IConfigService;
import com.chinacreator.gzcm.sysman.config.service.IConfigVersionService;
import com.chinacreator.gzcm.sysman.security.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 配置项 REST API — {@code /api/v1/system/config/items} 族属主端点。
 *
 * <p><b>#142/B4-3e-53 收口 D-B4-83</b>：本域两枚服务接口按「可选装配」口径接入
 * （构造注入 {@code ObjectProvider}，与 {@code AssetService}／{@code PreconditionEngine}
 * 同范式）。原口径有两类折叠，都违详设 §4.7.2（不可用 ≠ 无权限 ≠ 空结果）：</p>
 * <ul>
 *   <li>读侧「服务未装配」直接回 200＋空集＝把「服务没起来」谎报成「没有数据」；</li>
 *   <li>写侧与详情侧折成 {@code internalError(500)}＝把可预期的依赖缺失当成本服务自身故障。</li>
 * </ul>
 * <p>现两态统一抛显式不可用载体（503＋机读码 {@code ECOS-SYS-503}），由属主
 * {@code GlobalExceptionHandler} 按携带状态渲染状态行。<b>真实空结果不受影响</b>：
 * 服务在位且查到零条仍回 200＋空集。</p>
 */
@RestController
@RequestMapping("/api/v1/system/config")
public class ConfigController {
    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final ObjectProvider<IConfigService> configProvider;
    private final ObjectProvider<IConfigVersionService> versionProvider;

    public ConfigController(ObjectProvider<IConfigService> configProvider,
                            ObjectProvider<IConfigVersionService> versionProvider) {
        this.configProvider = configProvider;
        this.versionProvider = versionProvider;
    }

    /**
     * 未装配腿的统一载体＝503＋{@code ECOS-SYS-503}。
     * <p>禁折成 200＋空集（掩盖依赖缺失），也禁折成 500（把可预期缺装配报成本服务故障）。</p>
     */
    static DataBridgeException unavailability(String component) {
        return new DataBridgeException(503, 503,
            SystemErrorCode.ECOS_SYS_503 + ": " + component + "未装配，配置面不可用");
    }

    @GetMapping("/items")
    @RequirePermission("config:READ")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String configType,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        IConfigService service = configProvider.getIfAvailable();
        if (service == null) {
            log.error("IConfigService 未装配，配置项列表不可用（fail-closed）");
            throw unavailability("配置服务 IConfigService");
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> cond = new HashMap<>();
            if (configType != null) cond.put("configType", configType);
            if (environment != null) cond.put("environment", environment);
            List<Config> all = service.listConfigs(cond);
            int from = (page - 1) * pageSize, to = Math.min(from + pageSize, all.size());
            result.put("data", from < all.size() ? all.subList(from, to) : Collections.emptyList());
            result.put("total", all.size()); result.put("page", page); result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("查询配置失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/items/{id}")
    @RequirePermission("config:READ")
    public ApiResponse<?> get(@PathVariable String id) {
        IConfigService service = configProvider.getIfAvailable();
        if (service == null) {
            log.error("IConfigService 未装配，配置项 {} 查询不可用（fail-closed）", id);
            throw unavailability("配置服务 IConfigService");
        }
        try {
            Config c = service.getConfig(id);
            return c == null ? ApiResponse.notFound("配置不存在") : ApiResponse.success(c);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("查询配置失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PostMapping("/items")
    @RequirePermission("config:WRITE")
    public ApiResponse<?> create(@RequestBody Config config) {
        IConfigService service = configProvider.getIfAvailable();
        if (service == null) {
            log.error("IConfigService 未装配，配置项创建不可用（fail-closed）");
            throw unavailability("配置服务 IConfigService");
        }
        try {
            config.setConfigId(UUID.randomUUID().toString().replace("-", ""));
            return ApiResponse.success(service.createConfig(config));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("创建配置失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/items/{id}")
    @RequirePermission("config:WRITE")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Config config) {
        IConfigService service = configProvider.getIfAvailable();
        if (service == null) {
            log.error("IConfigService 未装配，配置项 {} 更新不可用（fail-closed）", id);
            throw unavailability("配置服务 IConfigService");
        }
        try {
            return ApiResponse.success(service.updateConfig(id, config));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("更新配置失败", e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/items/{id}")
    @RequirePermission("config:WRITE")
    public ApiResponse<?> delete(@PathVariable String id) {
        IConfigService service = configProvider.getIfAvailable();
        if (service == null) {
            log.error("IConfigService 未装配，配置项 {} 删除不可用（fail-closed）", id);
            throw unavailability("配置服务 IConfigService");
        }
        try {
            service.deleteConfig(id);
            return ApiResponse.success(Map.of("success", true));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("删除配置失败", e);
            return ApiResponse.internalError("删除失败");
        }
    }

    @GetMapping("/items/{id}/versions")
    @RequirePermission("config:READ")
    public ApiResponse<?> versions(@PathVariable String id) {
        IConfigVersionService service = versionProvider.getIfAvailable();
        if (service == null) {
            log.error("IConfigVersionService 未装配，配置项 {} 版本历史不可用（fail-closed）", id);
            throw unavailability("配置版本服务 IConfigVersionService");
        }
        try {
            return ApiResponse.success(service.listVersions(id));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Throwable e) {
            log.error("查询版本历史失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }
}
