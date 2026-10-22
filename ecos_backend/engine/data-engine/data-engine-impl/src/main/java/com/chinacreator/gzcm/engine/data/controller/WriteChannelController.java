package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelRequest;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelResult;
import com.chinacreator.gzcm.engine.data.writechannel.DwWriteChannelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F02-05 第 3 条 / C.3.6 / ADR-14 · 业务域数据<b>唯一写通道</b> REST 端点。
 *
 * <p>端点：{@code POST /api/v1/datanet/write-channel}
 * <ul>
 *   <li>铁律 §0.6 / ADR-14：业务域生产数据的业务写入统一收口于此，
 *       禁止其他模块另开口子直写 production schema；</li>
 *   <li>入参必带 {@code layer + carrierRef + traceId + idempotencyKey}（缺一即拒），
 *       data-engine 侧执行 MC06 限定名解析 / MC02 列类型白名单 / MC04 OLAP 双例外 /
 *       ST06 审计 / PipelineEvent / 幂等（同键重放不产生第二行）。</li>
 * </ul>
 *
 * <h3>三件套过滤器/拦截器注册（v1.2+ 单源规则）</h3>
 * <ol>
 *   <li><b>VersionPrefixRewriteFilter</b>：{@code /api/v1/datanet} 前缀
 *       <b>不在</b> V1_REWRITE_MAP 中 → KEEP（保持原路径转发），无需改 map；</li>
 *   <li><b>路由单源</b>：{@code route-manifest.json} 已声明
 *       {@code /api/v1/datanet}（owner = datanet，route = service），本端点随既有
 *       DatanetController/DatalakeController 同源下发；仅 gateway :8080 可达，
 *       service 直连端口不由业务面调用；</li>
 *   <li><b>SecurityConfig / ClearanceInterceptor</b>：两者均从单源
 *       {@code AnonymousEndpointRegistry} 生成豁免清单；本端点是<b>业务数据端点</b>，
 *       默认 DENY + L1 权限（§2.4-6：业务数据端点禁匿名），<b>不注册</b>为匿名端点，
 *       保持鉴权放行链（token + ClearanceInterceptor 默认 {@code /api/v1/} → L1）。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/datanet/write-channel")
public class WriteChannelController {

    private static final Logger log = LoggerFactory.getLogger(WriteChannelController.class);

    private final DwWriteChannelService writeChannelService;

    public WriteChannelController(DwWriteChannelService writeChannelService) {
        this.writeChannelService = writeChannelService;
    }

    /**
     * 唯一写通道：登记一次业务域载体写入（ADR-14 单入口）。
     *
     * @param request 强类型入参（layer/carrierRef/traceId/idempotencyKey 必填）
     * @return writeId / declared(存在性) / idempotentReplay(是否重放命中)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<WriteChannelResult>> write(@RequestBody WriteChannelRequest request) {
        try {
            return ok(ApiResponse.success(writeChannelService.write(request)));
        } catch (IllegalArgumentException e) {
            // ECOS-DATA-050 入参缺失 / ECOS-DATA-051 限定名残缺 / ECOS-DATA-053 非法层
            log.warn("写通道入参拒绝: {}", e.getMessage());
            return failure(ApiResponse.badRequest(e.getMessage()));
        } catch (IllegalStateException e) {
            // ECOS-DATA-052 MC02 裸 JSON 列 / ECOS-DATA-054 MC04 OLAP 违规
            log.warn("写通道守卫拒绝: {}", e.getMessage());
            return failure(ApiResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            log.error("写通道内部错误: {}", e.getMessage(), e);
            return failure(ApiResponse.error(ApiResponse.CODE_INTERNAL_ERROR,
                    "ECOS-DATA-055", "写通道内部错误: " + e.getMessage()));
        }
    }

    /**
     * 成功腿承载（#142/B4-3e-35，收口差距 D-B4-59 的写通道裸回腿）。
     *
     * <p>信封一字不动，只补状态行——{@code ApiResponse} 只描述 body、没有任何 HTTP 能力，
     * 裸回时 MC02/MC04 守卫拒绝的 403 与内部错误的 500 全落 HTTP 200，§4.7.2「不可用≠无权限」
     * 的分态在传输层同形。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> ok(ApiResponse<T> body) {
        return ResponseEntity.ok(body);
    }

    /**
     * 失败腿承载：状态行一律从信封 {@code code} 经 {@link DataBridgeException#resolveHttpStatus(int)}
     * 单源推出（改码即改行，禁在本类再写第二个状态字面量）。
     *
     * <p>{@code CODE_INTERNAL_ERROR} 的信封值是 {@code -1} 而非 500 ⇒ 本 helper 会落状态行 500
     * 而信封仍  {@code -1}。这个同号差不在本批收口面内（属 D-B4-65 余项），由行为门显式钉住
     * 两侧读数，不靠沉默掩盖。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> failure(ApiResponse<T> body) {
        return ResponseEntity.status(DataBridgeException.resolveHttpStatus(body.getCode())).body(body);
    }
}
