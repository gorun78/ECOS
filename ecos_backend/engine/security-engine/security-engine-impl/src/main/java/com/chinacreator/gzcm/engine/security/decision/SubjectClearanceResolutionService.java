package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.sysman.security.clearance.model.SubjectClearanceFact;
import com.chinacreator.gzcm.sysman.security.clearance.service.ISubjectClearanceQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 主体密级（{@code subject.clearanceLevel} / RLS {@code CONTEXT_CLEARANCE_LEVEL}）的
 * <b>服务端真值解析消费腿</b>（#142/B4-3e-81，差距 D-B4-23 余项腿）。
 *
 * <p><b>缺陷本质</b>：五档优先链（USER &gt; ROLE &gt; ORG &gt; TENANT &gt; GLOBAL）此前只私有在
 * sysman 的 {@code ClearanceInterceptor} 里，引擎侧无从复用 ⇒ {@code /decision} 与
 * {@code /rls/apply} 把密级恒写 {@code null}。凡绑定 {@code CONTEXT_CLEARANCE_LEVEL} 的 RLS
 * 谓词因此<b>结构上永远拿不到真值</b>（详设-01 C.2.2 期望的密级行范围从未生效过）。
 * 本类不重抄那条链——判据单源在属主 api 门面 {@link ISubjectClearanceQueryService}（铁律 #5），
 * 本类只做「三态到引擎语义」的转换。</p>
 *
 * <p><b>三态在此分岔（与准入面刻意不对称）</b>：</p>
 * <ul>
 *   <li><b>真值</b> ⇒ 返回档位数字；</li>
 *   <li><b>未解析</b>（五档查尽无档案行）⇒ 返回 {@code null}。引擎侧沿用既有「未解析 ⇒ 拒绝」
 *       渲染纪律（{@code RlsSubjectContext#resolveBySource} 返 {@code null} ⇒ 谓词
 *       {@code RLS_CONTEXT_UNRESOLVED}）。<b>禁</b>照抄拦截器末端的 {@code return 1}：
 *       那是准入兜底，把「没档案」冒充成「L1 内部真值」；策略面若也兜底，密级谓词就会对着一个
 *       不存在的事实做放行；</li>
 *   <li><b>不可用</b>（门面缺席，或 {@link SubjectClearanceFact#isDegraded()} 任一档取数抛错）⇒ 抛
 *       {@link SubjectClearanceSourceUnavailableException}，由调用方转 503＋{@code ECOS-SEC-501}。
 *       含断腿时即便低档读到一个数字也<b>不</b>算可信密级：高优先档是「断了」不是「没有」，
 *       取到它就等于承认一个被截断的优先级链（§4.7.2「不可用 ≠ 无权限 ≠ 空结果」）。</li>
 * </ul>
 *
 * <p><b>{@code orgId} 由调用方传入</b>：机构隶属的唯一主机构裁决单源在
 * {@link SubjectDepartmentResolutionService}（#142/B4-3e-80）。本类不再解析一次机构，
 * 免得仓内出现两套「恰好一枚主机构」判据（漂移后无人知晓哪套生效）。机构档拿不到 {@code orgId}
 * 时属主按「该档无从查询」跳过，与「该档断了」是两种读数，分别落未解析与不可用。</p>
 */
@Service
public class SubjectClearanceResolutionService {

    private static final Logger log = LoggerFactory.getLogger(SubjectClearanceResolutionService.class);

    /** 真值源不可用的机读理由（进异常 message，供日志与取证区分形态）。 */
    public static final String REASON_SOURCE_UNAVAILABLE = "SUBJECT_CLEARANCE_SOURCE_UNAVAILABLE";

    private final ISubjectClearanceQueryService clearanceQueryService;

    /**
     * 生产装配：{@code Optional} 只用于<b>装配面</b>（boot 调试态可能不带 sysman bean），
     * 取数面仍 fail-loud——缺席时抛不可用态，禁把「没接上属主」折叠成「该用户没密级档案」，
     * 更禁折叠成任何档位。
     */
    @Autowired
    public SubjectClearanceResolutionService(Optional<ISubjectClearanceQueryService> queryServiceProvider) {
        this.clearanceQueryService = queryServiceProvider == null
                ? null : queryServiceProvider.orElse(null);
    }

    /** 测试装配：直接给属主门面（可为 {@code null}＝未装配形态的取证）。 */
    SubjectClearanceResolutionService(ISubjectClearanceQueryService clearanceQueryService) {
        this.clearanceQueryService = clearanceQueryService;
    }

    /**
     * 解析主体密级真值。
     *
     * @param userId   主体用户 id（只取信任链读数）
     * @param tenantId 信任链租户 id，可为 {@code null}
     * @param orgId    已按唯一主机构裁决的机构号，可为 {@code null}（＝机构档无从查询）
     * @return 命中档的档位值；五档查尽无档案 ⇒ {@code null}（未解析，禁兜底成任何档位）
     * @throws SubjectClearanceSourceUnavailableException 门面未装配、取数失败或链含断腿
     */
    public Integer resolveClearanceLevel(String userId, String tenantId, String orgId) {
        if (userId == null || userId.isBlank()) {
            // 没有主体就没有事实：不取数、不伪造
            return null;
        }
        if (clearanceQueryService == null) {
            throw unavailable("ISubjectClearanceQueryService 未装配，主体密级无法服务端解析"
                    + "（禁回落到调用方自报档位，也禁兜底成任何档位）");
        }
        SubjectClearanceFact fact;
        try {
            fact = clearanceQueryService.resolveSubjectClearance(userId, tenantId, orgId);
        } catch (Exception e) {
            throw unavailable("属主取数失败: userId=" + userId
                    + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
        if (fact == null) {
            throw unavailable("属主返回 null 事实对象，形态不可解析: userId=" + userId);
        }
        if (fact.isDegraded()) {
            throw unavailable("密级链含断腿，读数不可信: userId=" + userId
                    + ", degradedTiers=" + fact.degradedTiers()
                    + ", tier=" + fact.tier() + ", level=" + fact.level());
        }
        if (!fact.isResolved()) {
            log.warn("主体密级解析未果（五档无档案行），按未解析处置（不兜底成任何档位）: userId={}", userId);
            return null;
        }
        return fact.level();
    }

    private static SubjectClearanceSourceUnavailableException unavailable(String reason) {
        log.warn("主体密级真值源不可用（fail-closed 转不可用态，禁折叠成未解析/档位兜底）: {}", reason);
        return new SubjectClearanceSourceUnavailableException(REASON_SOURCE_UNAVAILABLE + ": " + reason);
    }

    /** 真值源不可用（门面未装配 / 取数失败 / 链含断腿）——调用方必须转不可用态，禁当「没密级」。 */
    public static class SubjectClearanceSourceUnavailableException extends RuntimeException {
        public SubjectClearanceSourceUnavailableException(String message) {
            super(message);
        }
    }
}
