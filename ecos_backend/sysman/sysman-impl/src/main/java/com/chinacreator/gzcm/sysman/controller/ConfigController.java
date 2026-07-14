package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.entity.ConfigVersion;
import com.chinacreator.gzcm.sysman.config.service.IConfigService;
import com.chinacreator.gzcm.sysman.config.service.IConfigVersionService;
import com.chinacreator.gzcm.sysman.security.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/config")
public class ConfigController {
    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    @Autowired(required = false)
    private IConfigService configService;
    @Autowired(required = false)
    private IConfigVersionService versionService;

    @GetMapping("/items")
    @RequirePermission("config:READ")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String configType,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            if (configService == null) {
                result.put("data", Collections.emptyList()); result.put("total", 0);
                result.put("page", page); result.put("pageSize", pageSize);
                return ApiResponse.success(result);
            }
            Map<String, Object> cond = new HashMap<>();
            if (configType != null) cond.put("configType", configType);
            if (environment != null) cond.put("environment", environment);
            List<Config> all = configService.listConfigs(cond);
            int from = (page - 1) * pageSize, to = Math.min(from + pageSize, all.size());
            result.put("data", from < all.size() ? all.subList(from, to) : Collections.emptyList());
            result.put("total", all.size()); result.put("page", page); result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (Throwable e) {
            log.error("查询配置失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/items/{id}")
    @RequirePermission("config:READ")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            if (configService == null) return ApiResponse.internalError("配置服务未就绪");
            Config c = configService.getConfig(id);
            return c == null ? ApiResponse.notFound("配置不存在") : ApiResponse.success(c);
        } catch (Throwable e) {
            log.error("查询配置失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/items")
    @RequirePermission("config:WRITE")
    public ApiResponse<?> create(@RequestBody Config config) {
        try {
            if (configService == null) return ApiResponse.internalError("配置服务未就绪");
            config.setConfigId(UUID.randomUUID().toString().replace("-", ""));
            return ApiResponse.success(configService.createConfig(config));
        } catch (Throwable e) {
            log.error("创建配置失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/items/{id}")
    @RequirePermission("config:WRITE")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Config config) {
        try {
            if (configService == null) return ApiResponse.internalError("配置服务未就绪");
            return ApiResponse.success(configService.updateConfig(id, config));
        } catch (Throwable e) {
            log.error("更新配置失败", e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/items/{id}")
    @RequirePermission("config:WRITE")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            if (configService == null) return ApiResponse.internalError("配置服务未就绪");
            configService.deleteConfig(id);
            return ApiResponse.success(Map.of("success", true));
        } catch (Throwable e) {
            log.error("删除配置失败", e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/items/{id}/versions")
    @RequirePermission("config:READ")
    public ApiResponse<?> versions(@PathVariable String id) {
        try {
            if (versionService == null) return ApiResponse.success(Collections.emptyList());
            return ApiResponse.success(versionService.listVersions(id));
        } catch (Throwable e) {
            log.error("查询版本历史失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }
}
