package com.chinacreator.gzcm.workspace.caliber;

import com.chinacreator.gzcm.workspace.caliber.CaliberOwnerClient.OwnerReply;
import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.exception.CaliberFormulaInvalidException;
import com.chinacreator.gzcm.workspace.exception.CaliberNotFoundException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerFailureException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 口径读面编排服务（#146ac 附录A 第 31 条 {@code listCalibers} / {@code getCaliber} /
 * {@code validateCaliber}，契约行 = 详设-12 §12.4.2 line 8248~8250、8254）。
 *
 * <h3>三枚腿的做实程度（逐枚，禁整族外推）</h3>
 * <ol>
 *   <li><b>取数腿</b>：全走属主 REST（{@link CaliberOwnerClient}），本面零 SQL、零副本判定逻辑；</li>
 *   <li><b>403 腿</b>：读端点出口前过 {@link ScenarioReadGuard#assertReadable} F07-18 咽喉
 *       （明确拒绝/应答漂移 ⇒ 403，security 进程不可用 ⇒ 503，默认 DENY）。契约 403 腿由此承载；</li>
 *   <li><b>404 腿</b>：契约是 id 基寻址、属主是 code 基寻址，两跳解析（窗口内按 id 匹配主行 → 取 code →
 *       调详情面）落空即 404，并把解析窗口与是否触顶一并带出；</li>
 *   <li><b>400 腿</b>：{@code validate} 的判定权在属主 DSL 四关，本面只搬运结构化违反项；
 *       草稿 DSL 缺位属请求缺陷，400 逐字段（{@code ACTION_FIELDS_MISSING}），
 *       不拿去猜公式去空跑属主。</li>
 * </ol>
 *
 * <h3>契约出参列的真欠（显式 null + {@code *Applicable=false} + 原因单源键，禁兜底值伪装）</h3>
 * 见 {@link #REASONS}——每条都可由 grep/DDL 实扫复验。真欠列存在 ⇒ 本批<b>不翻转</b>台账第 31 条判定
 * （端点可路由且部分列有真值 ≠ 契约面齐备，同 #146aa 第 32/33 条口径）。
 */
@Service
public class CaliberReadService {

    private static final Logger log = LoggerFactory.getLogger(CaliberReadService.class);

    /** security-engine RLS 实体编码（{@code caliber:read} 档位在策略面的落点；未注册策略即默认 DENY）。 */
    static final String ENTITY_CODE = "caliber_definition";

    /** 取数来源标识（调用方据此区分「本面合成」与「属主真值」）。 */
    static final String DATA_SOURCE = "ontology-owner-rest";

    /** 属主主行面可透传列（其余列属版本行，缺席另记原因）。 */
    private static final List<String> HEAD_FIELDS =
            List.of("id", "code", "name", "status", "unit", "currency", "ownerRole", "dimensionJson", "domain");

    /** 属主版本行库侧键 → 契约驼峰键（原样搬运，不换算：additive 是数值码字典位，非布尔）。 */
    private static final Map<String, String> VERSION_FIELDS = Map.of(
            "version_no", "versionNo",
            "status", "status",
            "formula", "formula",
            "additive", "additive",
            "period_granularity", "periodGranularity",
            "approved_by", "approvedBy",
            "approved_at", "approvedAt",
            "git_ref", "gitRef",
            "create_time", "createTime");

    /** 属主 validate 入参白名单（DTO 五字段，多余键不下发，防把无关字段灌进属主）。 */
    private static final List<String> VALIDATE_REQUEST_FIELDS =
            List.of("code", "formula", "factFields", "units", "relatedFormulas");

    /** 四关闸名（属主 {@code CaliberFormulaValidationVO.Violation#gate} 词表，前端按 gate 分组渲染）。 */
    private static final List<String> DSL_GATES =
            List.of("DSL_V1_SYNTAX", "DSL_V2_REFERENCE", "DSL_V3_DIMENSION", "DSL_V4_CYCLE");

    /**
     * 真欠原因单源（每条可复验，文案不写跨引擎 schema/表限定名——与
     * {@code WorkspaceLayeringArchTest} 冻结判据同源）：
     * <ol>
     *   <li>list 面无版本链：属主 list 端点只回口径主行，版本行需按 code 逐枚调详情面（N+1 次跨服务调用），
     *       本批不以任何形式合成「空版本链」冒充契约「版本链列表」列；</li>
     *   <li>生效期无载体：属主版本表建表脚本 V167.1 的 16 列清单内无生效起止/期间列
     *       （实扫该脚本 0 命中 {@code effective}/{@code valid_from}/{@code valid_to}），
     *       契约「生效期」列在数据面即为真欠，不是取数遗漏；</li>
     *   <li>list 面无批准人：批准人在版本行（{@code approved_by}），随版本链一同缺席；</li>
     *   <li>详情面无 DSL 渲染文本：属主 DSL 服务只提供 {@code validate}/{@code validateFormula}
     *       两个方法（grep 实扫该类零 {@code render} 方法），无渲染产物可取；</li>
     *   <li>详情面无符号表：公式以表达式明文单列存储，无结构化符号表载体；符号定位只在
     *       validate 违例应答面出现，详情面不含；</li>
     *   <li>详情面无量纲推导结果：V3 推导单位仅用于闸门判定（违例才回文），不属详情面键；
     *       本面只搬运主行的 {@code unit}/{@code currency} 与版本行的 {@code additive}/
     *       {@code periodGranularity} 四枚真实载体；</li>
     *   <li>list 面无分页载体：属主 list 只有 {@code status}+{@code limit}（上限 500）无 offset/page，
     *       契约「分页」请求要点在属主面无对应入参，本批不合成页码字段。</li>
     * </ol>
     */
    static final Map<String, String> REASONS = Map.of(
            "versionChain", "属主 list 面只回口径主行，版本行需按 code 逐枚调详情面（N+1 跨服务调用）；本批不合成空版本链冒充列表列",
            "effectivePeriod", "属主版本表建表脚本 V167.1 十六列清单内无生效起止/期间列（实扫零命中），契约「生效期」列无数据载体",
            "approvedBy", "批准人属版本行列，随版本链在 list 面一同缺席；需批准人请调 GET /api/v1/workspace/calibers/{id}",
            "dslRenderedText", "属主 DSL 服务仅提供 validate/validateFormula 两方法（grep 实扫无 render 方法），无渲染文本产物",
            "symbolTable", "公式以表达式明文单列存储，无结构化符号表载体；符号定位仅在 validate 违例应答面出现",
            "derivedDimension", "V3 量纲推导结果仅用于闸门判定（违例才回文），不属详情面键；本面仅搬运 unit/currency/additive/periodGranularity 四枚真实载体",
            "pagination", "属主 list 面仅有 status+limit（上限 500）入参，无 offset/page，契约「分页」要点在属主面无载体");

    private final CaliberOwnerClient ownerClient;
    private final ScenarioReadGuard readGuard;

    public CaliberReadService(CaliberOwnerClient ownerClient, ScenarioReadGuard readGuard) {
        this.ownerClient = ownerClient;
        this.readGuard = readGuard;
    }

    /**
     * 口径主行列表（契约 #1 {@code listCalibers}）。
     *
     * @param code   口径编码精确过滤（本面客户端腿：属主 list 无 code 入参，在窗口内过滤并如实回报完整性）
     * @param status 状态过滤（透传属主等值匹配；缺省 = 不约束）
     * @throws com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException RLS 拒绝/漂移（403）
     * @throws com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException 安全引擎不可用（503）
     * @throws CaliberOwnerFailureException      属主显式回失败信封（500，带 ownerCode 取证）
     * @throws com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException 属主不可得（503）
     */
    public Map<String, Object> list(String code, String status) {
        readGuard.assertReadable(ENTITY_CODE);
        OwnerReply reply = ownerClient.listHeadRows(status);
        requireSuccess(reply, "list");
        List<Map<String, Object>> headRows = reply.rows();
        boolean windowSaturated = headRows.size() >= CaliberOwnerClient.OWNER_WINDOW_LIMIT;

        boolean codeFiltered = code != null && !code.isBlank();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : headRows) {
            if (codeFiltered && !code.equals(String.valueOf(row.get("code")))) {
                continue;
            }
            items.add(toListItem(row));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("filters", Map.of("code", code == null ? "" : code, "status", status == null ? "" : status));
        data.put("resolutionWindow", CaliberOwnerClient.OWNER_WINDOW_LIMIT);
        data.put("windowSaturated", windowSaturated);
        // code 过滤在窗口内完成：窗口触顶时结果不保证完整，禁把「窗口内没有」说成「没有」
        data.put("codeFilterComplete", !codeFiltered || !windowSaturated);
        data.put("dataSource", DATA_SOURCE);
        data.put("notApplicableReasons", pickReasons("versionChain", "approvedBy", "effectivePeriod", "pagination"));
        log.info("calibers 列表承流 code={} status={} 返回 {} 行（窗口 {} 枚，触顶={}）",
                code, status, items.size(), CaliberOwnerClient.OWNER_WINDOW_LIMIT, windowSaturated);
        return data;
    }

    /**
     * 口径详情（契约 #2 {@code getCaliber}，id 基寻址）。
     *
     * @throws CaliberNotFoundException          窗口内解析不到该 id，或属主详情面回 404（404）
     * @throws com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException RLS 拒绝/漂移（403）
     * @throws com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException 属主不可得（503）
     * @throws CaliberOwnerFailureException      属主显式回失败信封（500）
     */
    public Map<String, Object> detail(String caliberId) {
        readGuard.assertReadable(ENTITY_CODE);
        String code = resolveCodeById(caliberId);

        OwnerReply reply = ownerClient.detailByCode(code);
        if (reply.notFound()) {
            // 主行窗口内刚见到它、详情面却说没有 = 并发软删，按「不存在」回，不冒充 500
            throw new CaliberNotFoundException(caliberId, CaliberOwnerClient.OWNER_WINDOW_LIMIT, false);
        }
        requireSuccess(reply, "detail");
        Map<String, Object> head = reply.object();

        List<Map<String, Object>> versions = normalizeVersions(head.get("versions"));
        Map<String, Object> current = pickCurrentApproved(versions);

        Map<String, Object> data = new LinkedHashMap<>();
        copyPresent(head, data, List.of("id", "code", "name", "status", "unit", "currency",
                "ownerRole", "dimensionJson", "domain", "createTime"));
        data.put("versionChain", versions);
        data.put("dimension", dimensionOf(head, current));
        data.put("approvedBy", current == null ? null : current.get("approvedBy"));
        data.put("approvedAt", current == null ? null : current.get("approvedAt"));
        data.put("currentVersionNo", current == null ? null : current.get("versionNo"));
        // 生效期/渲染文本/符号表/推导量纲四列无载体：显式 null + Applicable=false，禁兜底值
        data.put("effectivePeriod", null);
        data.put("effectivePeriodApplicable", false);
        data.put("dslRenderedText", null);
        data.put("dslRenderedTextApplicable", false);
        data.put("symbolTable", null);
        data.put("symbolTableApplicable", false);
        data.put("derivedDimension", null);
        data.put("derivedDimensionApplicable", false);
        data.put("dataSource", DATA_SOURCE);
        data.put("notApplicableReasons",
                pickReasons("effectivePeriod", "dslRenderedText", "symbolTable", "derivedDimension"));
        log.info("calibers 详情承流 id={} → code={}（版本 {} 枚）", caliberId, code, versions.size());
        return data;
    }

    /**
     * DSL 四关预检（契约 #7 {@code validateCaliber}，只校验不落库）。
     *
     * @param request 草稿公式入参（{@code formula} 必填；白名单外字段不下发属主）
     * @return 四关全过的结果面（{@code valid=true} + 属主 target/registryMetricCount）
     * @throws ActionValidationException         formula 缺位（400 逐字段）
     * @throws CaliberFormulaInvalidException    四关违一（400，携逐条违反项与符号定位）
     * @throws com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException RLS 拒绝/漂移（403）
     * @throws com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException 属主不可得（503）
     */
    public Map<String, Object> validate(Map<String, Object> request) {
        Map<String, Object> payload = whitelist(request);
        Object formula = payload.get("formula");
        if (formula == null || String.valueOf(formula).isBlank()) {
            throw new ActionValidationException(List.of("formula"));
        }
        readGuard.assertReadable(ENTITY_CODE);

        OwnerReply reply = ownerClient.validate(payload);
        if (!reply.ok() && reply.code() != OwnerReply.CODE_BAD_REQUEST) {
            throw ownerFailure(reply, "validate");
        }
        Map<String, Object> vo = reply.object();
        boolean valid = Boolean.TRUE.equals(vo.get("valid"));
        List<Map<String, Object>> violations = violationRows(vo.get("violations"));
        String target = vo.get("target") == null ? null : String.valueOf(vo.get("target"));

        if (reply.ok() && !valid) {
            // 属主信封说成功、载荷说不过 = 键面自相矛盾，按载荷的失败为准并留痕（禁跟着信封报 200）
            log.warn("属主 validate 信封与载荷不一致（成功码 + valid=false），按 400 承流 target={}", target);
        }
        if (!valid) {
            throw new CaliberFormulaInvalidException(violations, target);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", true);
        data.put("violations", List.of());
        data.put("gates", DSL_GATES);
        data.put("target", target);
        data.put("registryMetricCount", vo.get("registryMetricCount"));
        data.put("persisted", false);
        data.put("dataSource", DATA_SOURCE);
        return data;
    }

    // ═══════════════ 私有辅助 ═══════════════

    /** id → code 两跳解析：属主 list 面主行含 id（{@code CaliberVO} 已暴露），窗口内精确匹配。 */
    private String resolveCodeById(String caliberId) {
        OwnerReply reply = ownerClient.listHeadRows(null);
        requireSuccess(reply, "list");
        List<Map<String, Object>> headRows = reply.rows();
        boolean windowSaturated = headRows.size() >= CaliberOwnerClient.OWNER_WINDOW_LIMIT;
        for (Map<String, Object> row : headRows) {
            if (caliberId.equals(String.valueOf(row.get("id")))) {
                Object code = row.get("code");
                if (code == null || String.valueOf(code).isBlank()) {
                    // 主行连 code 都没有 = 属主契约漂移，不能拿 null 去拼详情路径
                    throw new CaliberOwnerUnavailableException("detail");
                }
                return String.valueOf(code);
            }
        }
        throw new CaliberNotFoundException(caliberId, CaliberOwnerClient.OWNER_WINDOW_LIMIT, windowSaturated);
    }

    private void requireSuccess(OwnerReply reply, String operation) {
        if (!reply.ok()) {
            throw ownerFailure(reply, operation);
        }
    }

    /**
     * 属主非成功信封 → 真实状态行（属主面 HTTP 恒 200，语义只在信封码面，见
     * {@link CaliberOwnerClient} 类注释）。404 由 {@link #detail} 就地处置，其余码一律上抛。
     */
    private CaliberOwnerFailureException ownerFailure(OwnerReply reply, String operation) {
        log.warn("口径属主回失败信封 operation={} ownerCode={} ownerErrorCode={}",
                operation, reply.code(), reply.errorCode());
        return new CaliberOwnerFailureException(operation, reply.errorCode(), reply.code());
    }

    private Map<String, Object> toListItem(Map<String, Object> headRow) {
        Map<String, Object> item = new LinkedHashMap<>();
        copyPresent(headRow, item, HEAD_FIELDS);
        item.put("versionChain", null);
        item.put("versionChainApplicable", false);
        item.put("approvedBy", null);
        item.put("approvedByApplicable", false);
        item.put("effectivePeriod", null);
        item.put("effectivePeriodApplicable", false);
        return item;
    }

    /** 版本行键面归一（库侧 snake_case → 契约驼峰）；属主已回驼峰时原样保留，不做二次猜测。 */
    private List<Map<String, Object>> normalizeVersions(Object rawVersions) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(rawVersions instanceof List<?> list)) {
            return out;
        }
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> row) {
                Map<String, Object> version = new LinkedHashMap<>();
                VERSION_FIELDS.forEach((sourceKey, targetKey) -> {
                    Object value = row.get(sourceKey);
                    if (value != null) {
                        version.put(targetKey, value);
                    }
                });
                if (!version.isEmpty()) {
                    out.add(version);
                }
            }
        }
        return out;
    }

    /** 当前生效版本 = 版本链里首条 APPROVED（属主详情面按 create_time DESC、version_no DESC 排序）。 */
    private Map<String, Object> pickCurrentApproved(List<Map<String, Object>> versions) {
        for (Map<String, Object> version : versions) {
            if ("APPROVED".equals(String.valueOf(version.get("status")))) {
                return version;
            }
        }
        return null;
    }

    private Map<String, Object> dimensionOf(Map<String, Object> head, Map<String, Object> current) {
        Map<String, Object> dimension = new LinkedHashMap<>();
        dimension.put("unit", head.get("unit"));
        dimension.put("currency", head.get("currency"));
        dimension.put("additive", current == null ? null : current.get("additive"));
        dimension.put("periodGranularity", current == null ? null : current.get("periodGranularity"));
        return dimension;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> violationRows(Object rawViolations) {
        if (rawViolations instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return out;
        }
        return List.of();
    }

    private Map<String, Object> whitelist(Map<String, Object> request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request != null) {
            VALIDATE_REQUEST_FIELDS.forEach(field -> {
                Object value = request.get(field);
                if (value != null) {
                    payload.put(field, value);
                }
            });
        }
        return payload;
    }

    private void copyPresent(Map<String, Object> source, Map<String, Object> target, List<String> fields) {
        for (String field : fields) {
            if (source.containsKey(field)) {
                target.put(field, source.get(field));
            }
        }
    }

    private Map<String, String> pickReasons(String... keys) {
        Map<String, String> selected = new LinkedHashMap<>();
        for (String key : keys) {
            selected.put(key, REASONS.get(key));
        }
        return selected;
    }
}
