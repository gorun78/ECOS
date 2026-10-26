package com.chinacreator.gzcm.sysman.security.clearance.service;

import com.chinacreator.gzcm.sysman.security.clearance.model.SubjectClearanceFact;

/**
 * 主体密级（{@code clearanceLevel}）的<b>属主单源读面</b>（#142/B4-3e-81，收口差距 D-B4-23 余项腿）。
 *
 * <p><b>属主是谁</b>：密级档案的表（{@code td_user_security_profile}／
 * {@code td_role_security_profile}／{@code td_user_role}）与五档优先链一直私有在
 * sysman 的 {@code ClearanceInterceptor} 里——它是准入面的拦截器，不是可复用读面。结果是
 * security 引擎的 {@code /decision} 与 {@code /rls/apply} 只能把主体密级写死 {@code null}
 * （详设-01 C.2.2 的 {@code CONTEXT_CLEARANCE_LEVEL} 绑定腿因此恒按「未解析」拒绝）。
 * 铁律 #5：跨域事实经属主 api 门面，故本接口把这条链从拦截器里抽出来交付，
 * 引擎侧不再抄 SQL、也不新增一跳 REST。</p>
 *
 * <p><b>本接口只交付事实，不做裁决</b>：返回 {@link SubjectClearanceFact} 三态
 * （真值／未解析／含断腿），<b>不</b>内置任何默认档位。原拦截器末端
 * {@code return 1}（无档案冒充 L1 内部）是准入语义，属裁决事项，留在拦截器执行面上，
 * 不改语义、不搬进本读面（改它会改变现网准入判定，须另批裁决）。</p>
 *
 * <p><b>{@code orgId} 由调用方给出，属主不再自行解析</b>（刻意的不对称）：机构隶属的唯一主机构
 * 裁决在 #142/B4-3e-80 已收敛到 security 侧的
 * {@code SubjectDepartmentResolutionService} 单点。若本读面再解析一次机构，仓内就有了两套
 * 「恰好一枚主机构」判据——那正是 D-B4-92 族的重复族形态（两套判据必然漂移，漂移后无人知晓哪套生效）。
 * 机构档（{@code scope_type = 'ORG'}）取不到 {@code orgId} 时按「该档无从查询」跳过，
 * 记进 {@link SubjectClearanceFact#degradedTiers()} 之外还留在 {@code tier} 读数上，
 * 不会伪造命中。</p>
 */
public interface ISubjectClearanceQueryService {

    /**
     * 按五档优先链解析主体密级（USER &gt; ROLE &gt; ORG &gt; TENANT &gt; GLOBAL）。
     *
     * <p>实现必须逐档如实：读到即返、查不到继续下探、查失败记降级档；
     * 全部未命中 ⇒ {@link SubjectClearanceFact#unresolved(java.util.List)}，
     * <b>禁</b>回落到任何档位值。</p>
     *
     * @param userId   主体用户 id（只取信任链读数，禁取客户端自报）
     * @param tenantId 租户 id，可为 {@code null}（既有跨租户读面语义：null 即不带租户过滤）
     * @param orgId    机构档比对用的机构号，可为 {@code null}（＝机构档无从查询，禁拿空串比行）
     * @return 三态事实，恒非 {@code null}
     */
    SubjectClearanceFact resolveSubjectClearance(String userId, String tenantId, String orgId);
}
