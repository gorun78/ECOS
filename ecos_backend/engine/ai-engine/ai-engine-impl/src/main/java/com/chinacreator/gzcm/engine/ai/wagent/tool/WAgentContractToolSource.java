package com.chinacreator.gzcm.engine.ai.wagent.tool;

import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ToolContractStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@link ToolExecutorService.IContractToolSource} 的 W 侧实现（#143/B5-1，差距分析 B5「工具目录双轨」收口）。
 *
 * <p>把 {@code ecos_ai.ecos_wagent_tool_contract}（V236/V270/V266）的一行折算成咽喉可执行的
 * 声明体。本类是「契约目录进执行面」的<b>唯一</b>读面：咽喉只见
 * {@link ToolExecutorService.ContractToolDeclaration}，不见 DO、不解析契约列。</p>
 *
 * <p><b>地板次序与拒绝语义</b>（任一不成立即返回空 ⇒ 咽喉维持「工具未找到」，
 * 与 {@code PlanValidator#checkToolAndContract}、{@code EngineEndpointInvoker#invoke} 用同一
 * active 词表与同一版本闭合口径；禁在此"退而求其次"放宽，那会让三处判据重新分叉）：</p>
 * <ol>
 *   <li>{@code name+version} 齐备 —— 无版本不猜最新版（F10-17 已裁决口径）；</li>
 *   <li>{@code status = active} —— V266 seed 落 {@code registered} 属 F10-17 设计形态，
 *       升 {@code active} 必须走评审发布面，禁改 seed 直发；</li>
 *   <li>{@code endpoint_kind = rest} —— 非 rest 形态（jdbc/反射/直连库表）在读取端同样拒绝，
 *       这是 V265「禁用目标黑名单」配置项要求的二次拒绝位（注册面守卫仍未落地，见差距台账）；</li>
 *   <li>路径为 {@code /api/v1/} 相对形态、无 {@code ..}，且命中唯一在位 Engine 前缀族
 *       （{@link WAgentEngineCatalog#isLiveServicePrefix}）—— 不可归属 = 不出向；</li>
 *   <li>{@code endpoint_method} 落在 GET/POST 白名单；</li>
 *   <li>{@code operation} 由 {@link WAgentToolContractDO#operationClassResolved()} 折算，
 *       词表外的值拒绝（宁严勿宽：读判写只多要一次写权限，写判读＝绕过 OPA 写面）。</li>
 * </ol>
 */
@Component
public class WAgentContractToolSource implements ToolExecutorService.IContractToolSource {

    private static final Logger log = LoggerFactory.getLogger(WAgentContractToolSource.class);

    /** V236 {@code endpoint_kind} 唯一放行值（V265 黑名单口径：{@code endpoint_kind ∉ {rest}} ⇒ 拒绝）。 */
    private static final String ENDPOINT_KIND_REST = "rest";

    /** 出向方法白名单：契约面只开放读（GET）与建（POST），PUT/PATCH/DELETE 需先补设计再登记。 */
    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST");

    private static final String OPERATION_READ = "READ";

    private final WAgentToolContractDao toolContractDao;

    public WAgentContractToolSource(WAgentToolContractDao toolContractDao) {
        this.toolContractDao = toolContractDao;
    }

    @Override
    public Optional<ToolExecutorService.ContractToolDeclaration> findActive(String toolName, String toolVersion) {
        if (isBlank(toolName) || isBlank(toolVersion)) {
            return Optional.empty();
        }
        WAgentToolContractDO contract =
                toolContractDao.findByNameAndVersion(toolName, toolVersion).orElse(null);
        if (contract == null) {
            return Optional.empty();
        }
        if (!ToolContractStatus.ACTIVE.value().equals(contract.getStatus())) {
            // 与 INVOKER「非 active 不可执行」同一结论，但此处只在读面折叠成「工具未找到」——
            // 咽喉对通用工具面无需知道契约状态；状态冲突的可观测面留在本行日志与 INVOKER 错误码
            log.warn("[B5-1] 契约非 active 版不进咽喉: tool={} version={} status={}",
                    toolName, toolVersion, contract.getStatus());
            return Optional.empty();
        }
        if (!ENDPOINT_KIND_REST.equalsIgnoreCase(trim(contract.getEndpointKind()))) {
            log.warn("[B5-1] 契约端点形态非 rest，读取端拒绝: tool={} kind={}",
                    toolName, contract.getEndpointKind());
            return Optional.empty();
        }
        String path = trim(contract.getEndpointUrl());
        if (!isRestRelativePath(path) || !WAgentEngineCatalog.isLiveServicePrefix(path)) {
            log.warn("[B5-1] 契约端点不可归属在位 Engine 前缀族，拒绝出向: tool={} path={}", toolName, path);
            return Optional.empty();
        }
        String method = trim(contract.getEndpointMethod()).toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(method)) {
            log.warn("[B5-1] 契约端点方法不在白名单: tool={} method={}", toolName, method);
            return Optional.empty();
        }
        String operation = contractOperation(contract);
        if (operation == null) {
            log.warn("[B5-1] 契约操作类别不可折算: tool={} operation_class={} side_effect={}",
                    toolName, contract.getOperationClass(), contract.getSideEffect());
            return Optional.empty();
        }
        return Optional.of(new ToolExecutorService.ContractToolDeclaration(
                trim(contract.getName()), trim(contract.getVersion()), path, method, operation,
                // 超时预算与 operation 同纪律：折算单源在 DO（V270 优先、V236 回落），接缝不再自算
                contract.getInputSchemaJson(), contract.timeoutMsResolved()));
    }

    /**
     * {@code operation_class}（V270）优先、{@code side_effect}（V236）回落的折算单源在 DO 里；
     * 本方法只把它折算成裁决词表 {@code read|write}，词表外（含 DO 回落抛出的枚举异常）一律 null。
     */
    private String contractOperation(WAgentToolContractDO contract) {
        String resolved;
        try {
            resolved = trim(contract.operationClassResolved()).toUpperCase(Locale.ROOT);
        } catch (IllegalArgumentException | IllegalStateException unknownVocabulary) {
            return null;
        }
        if (OPERATION_READ.equals(resolved)) {
            return "read";
        }
        if ("SUGGEST".equals(resolved) || "WRITE".equals(resolved)) {
            return "write";
        }
        return null;
    }

    /** 相对 REST 路径形态（绝对 URL／协议头／上跳一律拒绝，与咽喉基址拼接纪律配套）。 */
    private static boolean isRestRelativePath(String path) {
        return !isBlank(path) && path.startsWith("/api/v1/") && !path.contains("..")
                && !path.contains("://");
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
