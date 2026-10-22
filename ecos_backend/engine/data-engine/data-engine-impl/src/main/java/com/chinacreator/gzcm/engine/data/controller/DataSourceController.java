package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.base.MachineErrorCode;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/datanet/datasource", "/datanet/datasource"})
public class DataSourceController {

    /**
     * #142/B4-3e-46：新承流端点的被拒／不可用腿一律留 warn 级原因
     *（承 #142/B4-3e-44 对 {@code PMO45DataSourceController} 的同一要求：改前折叠成 200 时
     * 既无状态行也无日志，事后无法取证）。既有裸回方法本批不动（属 D-B4-59 族另账）。
     */
    private static final Logger log = LoggerFactory.getLogger(DataSourceController.class);

    private final DataSourceRegistryService service;

    public DataSourceController(DataSourceRegistryService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<DataSourceEntity> register(@RequestBody DataSourceDTO dto) {
        return ApiResponse.success(service.register(dto));
    }

    @GetMapping
    public ApiResponse<List<DataSourceEntity>> list() {
        return ApiResponse.success(service.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<DataSourceEntity> getById(@PathVariable String id) {
        DataSourceEntity entity = service.getById(id);
        if (entity == null) {
            return ApiResponse.error(404, "数据源不存在: " + id);
        }
        return ApiResponse.success(entity);
    }

    @PutMapping("/{id}")
    public ApiResponse<DataSourceEntity> update(@PathVariable String id,
                                                @RequestBody DataSourceDTO dto) {
        DataSourceEntity updated = service.update(id, dto);
        if (updated == null) {
            return ApiResponse.error(404, "数据源不存在: " + id);
        }
        return ApiResponse.success(updated);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable String id) {
        boolean ok = service.testConnection(id);
        return ApiResponse.success(Map.of("success", ok, "datasourceId", id));
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnectionRaw(@RequestBody DataSourceDTO dto) {
        try {
            return ApiResponse.success(service.testConnectionRaw(dto));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable String id) {
        service.remove(id);
        return ApiResponse.success(null);
    }

    /**
     * #142/B4-3e-46（差距 <b>D-B4-76</b>）：未保存连接的原始配置结构预览（承流端点）。
     *
     * <p>请求体与 {@code POST /test} 同一形态（{@code datasourceName}/{@code datasourceType}/
     * {@code connectionConfig}），响应键面 {@code type}/{@code tableNames}/{@code fields}/
     * {@code fieldsObject}/{@code fieldsDegraded}/{@code fieldsReason}/{@code note}
     * ——契约见 {@code DataSourceServiceImpl#previewSchemaByConfig} 的注释单源。</p>
     *
     * <p>本方法体位于承流面（{@code /api/v1/datanet/datasource}），既有方法签名一律不动（API 只增不改）；
     * 三条失败腿的状态行与信封 code 同源：400＝入参不合法（有意不带机读码，本域 400 段无对应号义，
     * 借用他码会重犯 #142/B4-3e-44 已登记的「503 段被非降级腿借用」同型错误）；携带态腿经
     * {@link DataBridgeException#resolveHttpStatus(int)} 单源推出（404／503）；意外失败＝500。
     * 成功腿显式 200，与失败腿成对，防「成功也被状态行推导」。</p>
     */
    @PostMapping("/preview-schema")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewSchema(@RequestBody DataSourceDTO dto) {
        try {
            return ok(ApiResponse.success(service.previewSchemaRaw(dto)));
        } catch (IllegalArgumentException e) {
            return failure(ApiResponse.error(400, e.getMessage()));
        } catch (DataBridgeException e) {
            int status = DataBridgeException.resolveHttpStatus(e.getHttpStatus());
            log.warn("数据源结构预览（原始配置）被拒或不可用: {}", e.getMessage());
            return ResponseEntity.status(status).body(ApiResponse.error(status,
                    MachineErrorCode.orNumeric(e.getMessage(), e.getErrorCode()), e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("数据源结构预览（原始配置）未预期失败: {}", e.getMessage());
            return failure(ApiResponse.error(500,
                    DataDomainErrorCode.ECOS_DATA_031, "数据源结构预览失败: " + e.getMessage()));
        }
    }

    /**
     * #142/B4-3e-44 手法（承流面复用）：失败腿的状态行<b>唯一</b>由信封 {@code code} 经
     * {@link DataBridgeException#resolveHttpStatus(int)} 推出，本类不写第二个状态字面量。
     * 约束：成功腿必须走 {@link #ok(ApiResponse)}——{@code CODE_SUCCESS = 0} 喂进推导得 500。
     */
    private static <T> ResponseEntity<ApiResponse<T>> failure(ApiResponse<T> body) {
        return ResponseEntity.status(DataBridgeException.resolveHttpStatus(body.getCode())).body(body);
    }

    /** 成功腿显式 200（与 {@link #failure} 成对）。 */
    private static <T> ResponseEntity<ApiResponse<T>> ok(ApiResponse<T> body) {
        return ResponseEntity.ok(body);
    }
}
