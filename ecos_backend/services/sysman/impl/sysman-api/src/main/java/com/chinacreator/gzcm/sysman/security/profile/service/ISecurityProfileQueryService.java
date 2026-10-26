package com.chinacreator.gzcm.sysman.security.profile.service;

import com.chinacreator.gzcm.sysman.model.SecurityProfile;

import java.util.List;

/**
 * 安全档案（clearance/level 画像）的<b>属主单源读面</b>。
 *
 * <p><b>属主是谁</b>：{@code td_user_security_profile}、{@code td_role_security_profile}、
 * {@code td_user_role}（用户↔角色隶属）都在 sysman。铁律 #5：跨域事实经属主 api 门面，
 * 故安全引擎侧不再抄这些语句、也不新增一跳 REST。</p>
 *
 * <p><b>两批收口</b>：</p>
 * <ol>
 *   <li><b>D-B4-99 d)（#142/B4-3e-83）</b>：{@link #findHighestRoleForUser} —— 原有三处独立实现
 *       （sysman 行读、安全引擎逐字副本、密级链 {@code COALESCE(MAX(...), -1)} 聚合腿）。
 *       漂移不是理论风险：{@code clearance_level} 在 DDL 上可空
 *       （{@code clearance_level INT DEFAULT 0}，显式写 {@code NULL} 即落空值），而 PG 的
 *       {@code ORDER BY … DESC} 默认 <b>NULLS FIRST</b>、{@code MAX()} 却<b>忽略 NULL</b>。
 *       于是同一个用户在行读面会取到空密级行（{@code rs.getInt} 读成 {@code 0}＝L0 公开），
 *       在聚合面却按「无配置」处理。本接口的单条语句显式排除空值档
 *       （{@code clearance_level IS NOT NULL}），让「最高档」只有一个含义：
 *       <b>非空密级里的最大值</b>。</li>
 *   <li><b>D-B4-100（#142/B4-3e-84）</b>：其余六组读面 —— 用户档、角色档、全局默认档、
 *       全量用户档、全量角色档、按作用域取档／列档。安全引擎 {@code SecurityConfigDao}
 *       原先各存一份同形语句（与 sysman {@code SecurityProfileQueryDao} 两存），
 *       本批改为只经本门面。</li>
 * </ol>
 *
 * <p><b>本接口只交付事实，不做裁决</b>：单条读面返回 {@code null} 表示无命中（未解析），
 * 列表读面返回空表，<b>不</b>回落到任何默认档位；取数失败则以异常上抛，由调用方按自己的
 * 语义处置（准入面 fail-soft、策略面 fail-loud 转不可用），本读面不吞异常。</p>
 *
 * <p><b>tenantId 语义</b>：{@code null}＝不带租户过滤（既有跨租户读面语义）；
 * 非 {@code null} 时按既有语句语义取「本租户或全局（{@code tenant_id IS NULL}）」。</p>
 */
public interface ISecurityProfileQueryService {

    /**
     * 取该用户所有角色中密级最高的一档档案。
     *
     * @param userId   主体用户 id（只取信任链读数，禁取客户端自报）
     * @param tenantId 租户 id，可为 {@code null}（null 即不带租户过滤）
     * @return 命中的最高档档案；该用户无任何带密级的角色档案 ⇒ {@code null}（禁兜底成任何档位）
     */
    SecurityProfile findHighestRoleForUser(String userId, String tenantId);

    /**
     * 取用户级安全档案（{@code td_user_security_profile}）。
     *
     * @param userId   用户 id
     * @param tenantId 租户 id，可为 {@code null}
     * @return 命中的档案；无命中 ⇒ {@code null}
     */
    SecurityProfile findUser(String userId, String tenantId);

    /**
     * 取角色级安全档案（{@code td_role_security_profile}）。
     *
     * @param roleId   角色 id
     * @param tenantId 租户 id，可为 {@code null}
     * @return 命中的档案；无命中 ⇒ {@code null}
     */
    SecurityProfile findRole(String roleId, String tenantId);

    /**
     * 取全局默认档（用户表中 {@code is_default = TRUE} 的那一档）。
     *
     * @param tenantId 租户 id，可为 {@code null}
     * @return 命中的档案；无命中 ⇒ {@code null}
     */
    SecurityProfile findGlobalDefault(String tenantId);

    /**
     * 全量用户级档案，按 {@code updated_at DESC}。
     *
     * @param tenantId 租户 id，可为 {@code null}
     * @return 列表，无命中 ⇒ 空表
     */
    List<SecurityProfile> listAllUser(String tenantId);

    /**
     * 全量角色级档案，按 {@code clearance_level DESC}。
     *
     * @param tenantId 租户 id，可为 {@code null}
     * @return 列表，无命中 ⇒ 空表
     */
    List<SecurityProfile> listAllRole(String tenantId);

    /**
     * 按作用域取<b>单档</b>用户档案（{@code TENANT} 按租户、{@code ORG} 按机构）。
     *
     * @param scopeType 作用域类型；非 {@code TENANT}/{@code ORG} ⇒ {@code null}（既有取一语义）
     * @param scopeId   作用域 id（租户 id 或机构 id）
     * @return 命中的档案；未知作用域或无命中 ⇒ {@code null}
     */
    SecurityProfile findUserByScope(String scopeType, String scopeId);

    /**
     * 按作用域<b>列出</b>档案：{@code TENANT}＝该租户的用户档与角色档合并，
     * {@code ORG}＝该机构的用户档，其余按 {@code scope_type} 匹配用户档；
     * 三者均按 {@code updated_at DESC}（{@code TENANT} 分支的角色档亦按此序）。
     * <p>这是跨租户的管理面列举，<b>不</b>受 {@code tenantId} 过滤（既有语义）。</p>
     *
     * @param scopeType 作用域类型（大小写不敏感）
     * @param scopeId   作用域 id；空白 ⇒ 退化为按 {@code scope_type} 列举
     * @return 列表，无命中 ⇒ 空表
     */
    List<SecurityProfile> listProfilesByScope(String scopeType, String scopeId);
}
