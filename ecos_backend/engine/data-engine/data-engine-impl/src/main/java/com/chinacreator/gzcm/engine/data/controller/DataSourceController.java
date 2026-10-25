package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.base.MachineErrorCode;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.access.connector.DataSourceTypeVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * #142/B4-3e-64（差距 <b>D-B4-78 前端侧腿</b>，并为 <b>D-B4-93</b> 的显示面收敛提供属主目录）：
     * 数据源类型目录的<b>承流出口</b>。
     *
     * <p><b>为什么必须在承流面新增而不是让前端去读别名面</b>：本目录此前只存在于
     * {@code PMO45DataSourceController}（{@code /api/v1/datasource/supported-types}，
     * F02-01 已判定为<b>兼容别名</b>、约定 ≥2 迭代后删除）。前端若绑别名面，等于把
     * 一枚「将被删除的出口」写成新的依赖面；本类是 {@code /api/v1/datanet/datasource}
     * 唯一承流面（{@code DatasourceSingleSurfaceArchTest} 守护），故在此补一枚同口径出口，
     * 别名面保持原样不动（API 只增不改）。</p>
     *
     * <p><b>键面契约（前端唯一解析依据）</b>：数组元素恰两键
     * {@code canonical}（{@link String}，词表规范名）＋ {@code aliases}
     * （{@link java.util.List}，同物异名，可为空列表）。数据单源 =
     * {@link DataSourceTypeVocabulary}，取 {@code registrable()} 为真的成员、按枚举声明序输出，
     * <b>与服务准入面 {@code DataSourceServiceImpl.SUPPORTED_TYPES} 同一来源</b>——
     * 本端点不写第二份类型表，前端切到本通道后「下拉可选」与「后端能登」恒等。</p>
     *
     * <p>纯内存枚举遍历，不含 I/O，故<b>没有通道失败腿</b>（不会出现 503／超时）；
     * 但<b>有数据缺陷腿</b>：{@code registrable} 成员缺规范名或缺别名表时 {@link #typeCatalogRow}
     * 显式抛出，不折叠成「少一枚」。成功腿走 {@link #ok(ApiResponse)} 显式 200，与本域
     * 「失败必须有状态行」的口径成对。前端读取侧的分态（不可用／无权限／空目录）
     * 见 {@code ecos_frontend/src/pages/data-workbench/api/datasource.ts}。</p>
     */
    @GetMapping("/supported-types")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> supportedTypes() {
        return ok(ApiResponse.success(registrableTypeCatalog()));
    }

    /**
     * 目录构造（包内可见供单测直接调用）：逐枚取词表 {@code registrable} 成员的
     * 规范名与别名表，按枚举声明序输出。
     */
    static List<Map<String, Object>> registrableTypeCatalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DataSourceTypeVocabulary v : DataSourceTypeVocabulary.values()) {
            if (!v.registrable()) {
                continue;
            }
            out.add(typeCatalogRow(v.name(), v.canonical(), v.aliases()));
        }
        return out;
    }

    /**
     * 目录单行构造（包内可见供夹具直调）：{@code registrable} 成员 → 恰两键
     * {@code {canonical, aliases}}。
     *
     * <p><b>非法入参一律抛，不静默跳过、不补默认值</b>：词表被改成「可登记却无规范名／
     * 无别名表」属数据缺陷，若在此折叠过去，用户面读数就是「前端下拉少一枚而日志无痕」——
     * 与 D-B4-78 的原始病灶（两面各自演化、无人报错）同型。抛出的消息带枚名，便于定位。</p>
     */
    static Map<String, Object> typeCatalogRow(String enumName, String canonical, List<String> aliases) {
        if (canonical == null || canonical.isBlank()) {
            throw new IllegalStateException("词表成员 " + enumName
                    + " registrable=true 但规范名为空——数据源类型目录无法构造（禁静默跳过）");
        }
        if (aliases == null) {
            throw new IllegalStateException("词表成员 " + enumName
                    + " 的别名表为 null——契约要求数组（可为空数组），禁落 null 给前端（缺省即空面的语义由属主显式给出）");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("canonical", canonical);
        item.put("aliases", aliases);
        return item;
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
