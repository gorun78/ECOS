package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.base.MachineErrorCode;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import com.chinacreator.gzcm.engine.data.service.DataSourceServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PMO-45: 数据源管理 CRUD 全链路 — /api/v1/datasource/ 路径。
 *
 * 与 DataSourceController（/api/v1/datasource /datanet/datasource/）并存，两条路径都暴露。
 * 三滤波器：VersionPrefixRewriteFilter / SecurityConfig / ClearanceInterceptor
 * 已在 PMO-45 任务中注册。
 *
 * 新增 endpoint：
 *   POST /test-connection/{id}  — 对已保存数据源执行连通性测试
 *   POST /preview-schema         — 预览数据源代码结构 (body: {datasourceId})
 *
 * F02-01（W52 数据域）：本控制器为<b>兼容别名</b>（/api/v1/datasource、/datasource），
 * 唯一承流面 = DataSourceController（/api/v1/datanet/datasource）。两者均委托同一
 * {@link DataSourceRegistryService}（单套 CRUD，禁两份 SQL）。为标记其过渡性质，
 * 所有响应统一附 {@code X-Deprecation: true} 响应头，兼容 ≥2 迭代后删除。
 *（勿删 VersionPrefixRewriteFilter 双向条目 —— 归 gateway，与本控制器同批删除。）
 *
 * @author ECOS-BE (PMO-45)
 * @deprecated 兼容别名，请改用 {@code /api/v1/datanet/datasource}（F02-01 单套 CRUD 收口）
 */
@Deprecated
@RestController
@RequestMapping({"/api/v1/datasource", "/datasource"})
public class PMO45DataSourceController {

    /** F02-01：兼容别名弃用标记响应头（值固定 true；前端/客户端据此提示迁移）。 */
    private static final String DEPRECATION_HEADER = "X-Deprecation";
    private static final String DEPRECATION_VALUE = "true";

    /** #142/B4-3e-44：折叠面收口后，被拒／不可用腿一律留 warn 级原因（改前静默折成 200）。 */
    private static final Logger log = LoggerFactory.getLogger(PMO45DataSourceController.class);

    private final DataSourceRegistryService registryService;
    private final DataSourceServiceImpl impl;

    public PMO45DataSourceController(DataSourceRegistryService registryService,
                                     DataSourceServiceImpl impl) {
        this.registryService = registryService;
        this.impl = impl;
    }

    // ── CRUD ──────────────────────────────────────────────────────────

    @PostMapping
    public org.springframework.http.ResponseEntity<ApiResponse<DataSourceEntity>> register(@RequestBody DataSourceDTO dto) {
        try {
            // PMO-45 T5 验收: POST 创建资源返回 201 Created（REST 规范）
            ResponseEntity<ApiResponse<DataSourceEntity>> body = org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.CREATED)
                    .body(ApiResponse.success(registryService.register(dto)));
            return markDeprecated(body);
        } catch (IllegalArgumentException e) {
            return markDeprecated(org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage())));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DataSourceEntity>>> list() {
        return markDeprecated(ResponseEntity.ok(ApiResponse.success(registryService.listAll())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DataSourceEntity>> getById(@PathVariable String id) {
        DataSourceEntity entity = registryService.getById(id);
        if (entity == null) {
            // #142/B4-3e-44（D-B4-74）：改前写 HTTP 200 ＋ 信封 code 404，三枚既有状态行门均不可见。
            return markDeprecated(failure(ApiResponse.error(404,
                    DataDomainErrorCode.ECOS_DATA_032, "数据源不存在: " + id)));
        }
        return markDeprecated(ok(ApiResponse.success(entity)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DataSourceEntity>> update(@PathVariable String id,
                                                 @RequestBody DataSourceDTO dto) {
        DataSourceEntity updated = registryService.update(id, dto);
        if (updated == null) {
            return markDeprecated(failure(ApiResponse.error(404,
                    DataDomainErrorCode.ECOS_DATA_032, "数据源不存在: " + id)));
        }
        return markDeprecated(ok(ApiResponse.success(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable String id) {
        registryService.remove(id);
        return markDeprecated(ResponseEntity.ok(ApiResponse.success(null)));
    }

    // ── 未保存前测试（raw） ──────────────────────────────────────────

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnectionRaw(@RequestBody DataSourceDTO dto) {
        try {
            return markDeprecated(ok(ApiResponse.success(registryService.testConnectionRaw(dto))));
        } catch (IllegalArgumentException e) {
            // #142/B4-3e-44（D-B4-74）：参数校验失败写 400 状态行；机读码有意留空（本域 400 段无
            // 对应号义，借用他码会重犯 D-B4-73「503 段被非降级腿借用」的同型错误）。
            return markDeprecated(failure(ApiResponse.error(400, e.getMessage())));
        }
    }

    // ── PMO-45 新增 ──────────────────────────────────────────────────

    /**
     * 对已保存数据源执行连通性测试。
     * 关系型: 走 JdbcConnector 真实连接。
     * MINIO/FILESYSTEM: 验证 URI 格式合法性。
     */
    @PostMapping("/test-connection/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnectionById(@PathVariable String id) {
        Map<String, Object> result = impl.testConnectionById(id);
        return markDeprecated(ResponseEntity.ok(ApiResponse.success(result)));
    }

    /**
     * 预览数据源代码结构。
     * body: {"datasourceId": "xxx"}
     */
    @PostMapping("/preview-schema")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewSchema(
            @RequestBody java.util.Map<String, String> body) {
        String id = body.get("datasourceId");
        if (id == null || id.isBlank()) {
            // #142/B4-3e-44（D-B4-74）：入参校验失败写 400 状态行（改前 200＋code 400）。
            return markDeprecated(failure(ApiResponse.error(400, "datasourceId 不能为空")));
        }
        try {
            return markDeprecated(ok(ApiResponse.success(impl.previewSchema(id))));
        } catch (DataBridgeException e) {
            // #142/B4-3e-44（D-B4-74／D-B4-75，§3.2.7 同号性）：属主服务改抛携带态后，本腿必须
            // 排在 catch (RuntimeException) <b>之前</b>，否则 404／503 又被兜底折成 500＝假修。
            // 状态行取携带值经单源裁决，信封 code 与状态行同源，机读码取 message 前缀。
            int status = DataBridgeException.resolveHttpStatus(e.getHttpStatus());
            log.warn("数据源结构预览被拒或不可用: {}", e.getMessage());
            return markDeprecated(ResponseEntity.status(status)
                    .body(ApiResponse.error(status,
                            MachineErrorCode.orNumeric(e.getMessage(), e.getErrorCode()),
                            e.getMessage())));
        } catch (RuntimeException e) {
            // 非携带态的意外失败＝服务端故障，写 500（不折成 404，也不留 200）。
            log.warn("数据源结构预览未预期失败: {}", e.getMessage());
            return markDeprecated(failure(ApiResponse.error(500,
                    DataDomainErrorCode.ECOS_DATA_031, "数据源结构预览失败: " + e.getMessage())));
        }
    }

    /**
     * 查询支持的数据源类型列表（供前端动态渲染类型下拉）。
     */
    @GetMapping("/supported-types")
    public ResponseEntity<ApiResponse<List<String>>> supportedTypes() {
        return markDeprecated(ResponseEntity.ok(ApiResponse.success(List.copyOf(
                DataSourceServiceImpl.SUPPORTED_TYPES))));
    }

    /** F02-01：为兼容别名响应统一追加 X-Deprecation:true 响应头（幂等、不改响应体契约）。 */
    private static <T> ResponseEntity<T> markDeprecated(ResponseEntity<T> response) {
        HttpHeaders headers = new HttpHeaders();
        if (response.getHeaders() != null) {
            headers.putAll(response.getHeaders());
        }
        headers.put(DEPRECATION_HEADER, List.of(DEPRECATION_VALUE));
        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
    }

    /**
     * #142/B4-3e-44（D-B4-74 收口手法，承 #142/B4-3e-37 先例）：失败腿的状态行<b>唯一</b>由信封
     * {@code code} 经 {@link DataBridgeException#resolveHttpStatus(int)} 单源推出——本类不写
     * 第二个状态字面量，杜绝「body 写 404、状态行写 200」两面各自成立。
     *
     * <p>约束：成功腿必须走 {@link #ok(ApiResponse)}。{@code ApiResponse.CODE_SUCCESS = 0}
     * 喂进 {@code resolveHttpStatus} 得 500＝把成功判成失败（3e-37 已实测过一次）。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> failure(ApiResponse<T> body) {
        return ResponseEntity.status(DataBridgeException.resolveHttpStatus(body.getCode())).body(body);
    }

    /** #142/B4-3e-44：成功腿显式 200（与 {@link #failure} 成对，防「成功也被状态行推导」误伤）。 */
    private static <T> ResponseEntity<ApiResponse<T>> ok(ApiResponse<T> body) {
        return ResponseEntity.ok(body);
    }
}
