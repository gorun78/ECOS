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
 * <p><b>三批收口</b>：</p>
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
 *   <li><b>D-B4-103（#142/B4-3e-86）</b>：{@link #hasRoleCode} —— 角色码判读面。
 *       安全引擎原 {@code SecuritySandboxDao.countSuperAdminRole} 用两枚安全档案表间
 *       的幻影列（{@code profile_id}）JOIN 并按 {@code role_name} 过滤，两表 DDL 均无此二列
 *       ⇒ SQL 恒 PG error 被 catch 折 0，角色判读永远为否。本批把判读谓词落在属主真列
 *       （{@code td_user_role → td_role} 的 {@code role_code}/{@code role_name}）上，
 *       幻影腿 DAO 整文件删除。</li>
 *   <li><b>D-B4-101（#142/B4-3e-87）</b>：写面三组 —— 安全引擎 {@code SecurityConfigDao}
 *       原九腿（upsert×3／clearAllDefaults／activate／delete×2／updateFields／clone）
 *       直写本域属主表，{@code upsertProfile} 以 {@code String.format} 拼表名列名。
 *       本批把写语句收到 {@code SecurityProfileQueryDao} 单源，引擎侧九腿全部
 *       {@code UnsupportedOperationException} 删除、只经本门面写。
 *       写面语义裁决（DDL 实证 + 属主控制器现存行为）：角色表<b>无</b> {@code is_default} 列
 *       （V24/V41），故 {@link #upsertCoalescing} 对角色表写入被引擎面拒绝；自定义
 *       {@code isDefault} 只允许经 {@link #setUserAsDefault}/{@link #activateDefault}
 *       两腿；克隆腿固定 {@code is_default=FALSE}；通用 UPSERT 的 {@code ON CONFLICT}
 *       子句<b>不碰</b> {@code is_default}（属主通用腿现状），用户专属 UPSERT 的
 *       {@code ON CONFLICT} 子句<b>不收</b> {@code is_default}（属主 {@code PUT /user/{userId}}
 *       现状），这两处与引擎原八处 {@code COALESCE(?, is_default)} 的差即本批改法。</li>
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
     * 判定该用户是否挂有指定角色码（#142/B4-3e-86，收口 D-B4-103）。
     *
     * <p><b>语义裁决</b>（本机单源）：平台超管角色 {@code ROLE_SUPER_ADMIN} 在真库同时落
     * {@code td_role.role_code} 与 {@code td_role.role_name} 两列同文，故本判据按
     * {@code role_code 或 role_name} 带内等值命中其一即认定；谓词落在
     * {@code td_user_role}/{@code td_role} 真列上（属主单源语句见
     * {@code SecurityProfileQueryDao#hasRoleCode}）。平台级裁决，不带租户过滤
     * （与 {@code Authorization} 头角色面口径一致）。</p>
     *
     * @param userId   主体用户 id（只取信任链读数，禁取客户端自报）
     * @param roleCode 角色码（本面消费：{@code ROLE_SUPER_ADMIN}）
     * @return 命中任一角色列 ⇒ {@code true}；无命中 ⇒ {@code false}。
     *         <b>取数失败上抛</b>（三分态由调用方处置，本读面不吞异常、禁兜底 true）
     */
    boolean hasRoleCode(String userId, String roleCode);

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

    // ──────────────────────────────────────────────────────────────────
    // 写面（#142/B4-3e-87，收口 D-B4-101）
    // ──────────────────────────────────────────────────────────────────

    /**
     * 用户级档案 UPSERT（属主单源语句）。
     *
     * <p><b>语义裁决</b>：INSERT 阶段 {@code is_default} 取 {@code isDefault}；
     * {@code ON CONFLICT (user_id)} 阶段更新七字段 + {@code updated_at}，
     * <b>不</b>碰 {@code is_default}（属主 {@code PUT /user/{userId}} 既有语义）。
     * 默认档切换只走 {@link #clearAllDefaults}/{@link #activateDefault}。</p>
     *
     * @return 影响行数
     */
    int upsertUserProfile(String userId, Integer clearanceLevel, String linkedWorkstation,
                          String auditMode, Boolean sandboxMandatory, boolean isDefault,
                          String scopeType, String tenantId, String orgId);

    /**
     * 角色级档案 UPSERT（属主单源语句）。
     *
     * <p>{@code td_role_security_profile} 无 {@code is_default} 列（V24/V41），
     * 本问句与用户腿同形除键列外一致。</p>
     *
     * @return 影响行数
     */
    int upsertRoleProfile(String roleId, Integer clearanceLevel, String linkedWorkstation,
                          String auditMode, Boolean sandboxMandatory,
                          String scopeType, String tenantId, String orgId);

    /**
     * 按具名目标档 UPSERT（属主单源语句，替代原引擎面 {@code String.format} 拼标识符腿）。
     *
     * @param profileTarget {@code USER} 或 {@code ROLE}（大写）；<b>非白名单值（含
     *        {@code null} 与任意标识符）抛 {@link IllegalArgumentException}，拼不出语句</b>
     * @param isDefault     仅进 INSERT 阶段；{@code ON CONFLICT} 阶段不碰 {@code is_default}
     * @return 影响行数
     */
    int upsertCoalescing(String profileTarget, String idValue, Integer clearanceLevel,
                         String linkedWorkstation, String auditMode, Boolean sandboxMandatory,
                         boolean isDefault, String scopeType, String tenantId, String orgId);

    /**
     * 清除全部用户档默认位（属主单源，全表无租户过滤＝属主既有语义）。
     *
     * @return 影响行数
     */
    int clearAllDefaults();

    /**
     * 激活指定用户档为全局默认（属主单源，{@code is_default=TRUE} + {@code updated_at=NOW()}）。
     *
     * @return 影响行数
     */
    int activateDefault(String userId);

    /**
     * 删除指定用户档（属主单源）。
     *
     * @return 影响行数
     */
    int deleteUserProfile(String userId);

    /**
     * 删除指定角色档（属主单源）。
     *
     * @return 影响行数
     */
    int deleteRoleProfile(String roleId);

    /**
     * 按主键部分更新用户档字段（属主单源，六字段 + {@code updated_at}）。
     *
     * @return 影响行数
     */
    int updateProfileFields(String userId, Integer clearanceLevel, String linkedWorkstation,
                            String auditMode, Boolean sandboxMandatory, boolean isDefault);

    /**
     * 克隆一份用户档（属主单源，固定 {@code is_default=FALSE}）。
     *
     * @return 影响行数
     */
    int cloneUserProfile(String newUserId, Integer clearanceLevel, String linkedWorkstation,
                         String auditMode, Boolean sandboxMandatory,
                         String scopeType, String tenantId, String orgId);
}
