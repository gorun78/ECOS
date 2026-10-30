package com.chinacreator.gzcm.workspace.caliber;

import com.chinacreator.gzcm.common.ontology.CaliberDslContract;
import com.chinacreator.gzcm.workspace.caliber.CaliberOwnerClient.OwnerReply;
import com.chinacreator.gzcm.workspace.exception.CaliberFormulaInvalidException;
import com.chinacreator.gzcm.workspace.exception.CaliberNotFoundException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerFailureException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 口径寻址与信封判定的读写共用单源（#146ad）。
 *
 * <p>契约面是 id 基寻址、属主面是 code 基寻址；版本行键面是库侧 snake_case、契约面是驼峰。
 * 这三件事在读面（{@code getCaliber}）与写面（submit/approve/supersede）上完全同义，
 * 各写一套就是两套口径 —— 本类把它们收成一处，两面料禁复制。</p>
 *
 * <p>本类不做能力判定（哪些腿上线、哪些腿 501 属服务层的裁决），只负责
 * 「取到属主应答之后怎么读」。</p>
 */
@Component
public class CaliberAddressing {

    /** security-engine RLS 实体编码（{@code caliber:*} 档位在策略面的落点；未注册策略即默认 DENY）。 */
    public static final String ENTITY_CODE = "caliber_definition";

    /** 取数来源标识（调用方据此区分「本面合成」与「属主真值」）。 */
    public static final String DATA_SOURCE = "ontology-owner-rest";

    /** 属主主行面可透传列（其余列属版本行，缺席另记原因）。 */
    public static final List<String> HEAD_FIELDS =
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

    /**
     * 属主四关机读码 → 契约闸名（#146ah：取 common-api {@link CaliberDslContract} 契约单源，
     * 与 ontology 写出面同值；<b>不猜一关顶替</b>——映射不上由属主返回 {@code null}）。
     *
     * <p>属主 {@code CaliberDslValidator} 把 V1~V4 分别落在 {@code ECOS-ONTO-070/071/072/073}，
     * 写入咽喉（{@code POST /{code}/versions}）与审批咽喉只回这个机读码、不回 data 段
     * ⇒ 闸名是委托侧唯一可复验的结构化拒绝证据。</p>
     */
    private static final Map<String, String> OWNER_DSL_GATE = Map.of(
            CaliberDslContract.CODE_V1_SYNTAX, CaliberDslContract.GATE_V1_SYNTAX,
            CaliberDslContract.CODE_V2_REFERENCE, CaliberDslContract.GATE_V2_REFERENCE,
            CaliberDslContract.CODE_V3_DIMENSION, CaliberDslContract.GATE_V3_DIMENSION,
            CaliberDslContract.CODE_V4_CYCLE, CaliberDslContract.GATE_V4_CYCLE);

    private final CaliberOwnerClient ownerClient;

    public CaliberAddressing(CaliberOwnerClient ownerClient) {
        this.ownerClient = ownerClient;
    }

    /**
     * id → code 两跳解析：属主 list 面主行含 id（{@code CaliberVO} 已暴露），窗口内精确匹配。
     *
     * @throws CaliberNotFoundException          窗口内解析不到该 id（404，携解析窗口与是否触顶）
     * @throws CaliberOwnerUnavailableException  属主失败信封或主行连 code 都没有（503）
     */
    public String resolveCodeById(String caliberId) {
        return headRowById(caliberId).code();
    }

    /**
     * id → 属主主行（含 code 与解析成因）。写面在出网前要用主行状态做前置判定，故与
     * {@link #resolveCodeById} 同一条腿，禁另开一次列表调用。
     */
    public ResolvedHead headRowById(String caliberId) {
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
                return new ResolvedHead(String.valueOf(code), row);
            }
        }
        throw new CaliberNotFoundException(caliberId, CaliberOwnerClient.OWNER_WINDOW_LIMIT, windowSaturated);
    }

    /** 属主详情面（code 基）取主行 + 版本链；属主 404 码面上抛成 404 载体，由调用方带 id 出清。 */
    public Map<String, Object> fetchHeadWithVersions(String code, String caliberIdForEvidence) {
        OwnerReply reply = ownerClient.detailByCode(code);
        if (reply.notFound()) {
            // 主行窗口内刚见到它、详情面却说没有 = 并发软删，按「不存在」回，不冒充 500
            throw new CaliberNotFoundException(caliberIdForEvidence,
                    CaliberOwnerClient.OWNER_WINDOW_LIMIT, false);
        }
        requireSuccess(reply, "detail");
        return reply.object();
    }

    /** 版本行键面归一（库侧 snake_case → 契约驼峰）；属主已回驼峰时原样保留，不做二次猜测。 */
    public List<Map<String, Object>> normalizeVersions(Object rawVersions) {
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
    public Map<String, Object> currentApproved(List<Map<String, Object>> versions) {
        for (Map<String, Object> version : versions) {
            if ("APPROVED".equals(String.valueOf(version.get("status")))) {
                return version;
            }
        }
        return null;
    }

    /** 按版本号精确取版本行；缺位返回 null（由调用方按契约状态前置分派，本类不猜）。 */
    public Map<String, Object> versionByNo(List<Map<String, Object>> versions, String versionNo) {
        for (Map<String, Object> version : versions) {
            if (versionNo.equals(String.valueOf(version.get("versionNo")))) {
                return version;
            }
        }
        return null;
    }

    /** 版本链里首条状态命中给定词表的版本行；全不命中返回 null。 */
    public Map<String, Object> firstVersionWithStatus(List<Map<String, Object>> versions,
                                                     List<String> statuses) {
        for (Map<String, Object> version : versions) {
            if (statuses.contains(String.valueOf(version.get("status")))) {
                return version;
            }
        }
        return null;
    }

    public void requireSuccess(OwnerReply reply, String operation) {
        if (!reply.ok()) {
            throw ownerFailure(reply, operation);
        }
    }

    /**
     * 属主非成功信封 → 真实状态行（属主面 HTTP 恒 200，语义只在信封码面，见
     * {@link CaliberOwnerClient} 类注释）。404 由调用方就地处置，其余码一律上抛。
     */
    public CaliberOwnerFailureException ownerFailure(OwnerReply reply, String operation) {
        return new CaliberOwnerFailureException(operation, reply.errorCode(), reply.code());
    }

    public void copyPresent(Map<String, Object> source, Map<String, Object> target, List<String> fields) {
        for (String field : fields) {
            if (source.containsKey(field)) {
                target.put(field, source.get(field));
            }
        }
    }

    /** 单条版本行归一（属主写入咽喉 {@code findVersionById} 回的是库侧 snake_case 单行）。 */
    public Map<String, Object> normalizeVersionRow(Map<String, Object> rawRow) {
        List<Map<String, Object>> rows = normalizeVersions(rawRow == null
                ? List.of() : List.of(rawRow));
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    /** 属主 validate 违例数组搬运（非列表形态＝契约漂移 → 空集，由调用方按载荷判 400）。 */
    public List<Map<String, Object>> violationRows(Object rawViolations) {
        if (rawViolations instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    out.add(new LinkedHashMap<>(castRow(map)));
                }
            }
            return out;
        }
        return List.of();
    }

    /**
     * 属主 400 拒绝信封 → 契约 400 {@code CALIBER_DSL_INVALID}（四关违一，读预检与写咽喉共用）。
     *
     * <p>data 段在位就把结构化违反项与 target 原样搬上；data 段缺位（属主写入/审批咽喉的真实形态）
     * 则闸名取 {@code ECOS-ONTO-07x} 机读码 + {@code symbolLocationApplicable=false}。
     * 两种形态都<b>不落 503</b>：那是把客户端公式错误报成引擎故障。</p>
     */
    public CaliberFormulaInvalidException formulaRejection(OwnerReply reply) {
        if (reply.data() instanceof Map<?, ?> map && !map.isEmpty()) {
            Object target = map.get("target");
            return new CaliberFormulaInvalidException(violationRows(map.get("violations")),
                    target == null ? null : String.valueOf(target));
        }
        return new CaliberFormulaInvalidException(reply.errorCode(), OWNER_DSL_GATE.get(reply.errorCode()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castRow(Object row) {
        return (Map<String, Object>) row;
    }

    /** id 基寻址解析结果（code + 属主主行原样，供写面在出网前判状态前置）。 */
    public record ResolvedHead(String code, Map<String, Object> headRow) {
    }
}
