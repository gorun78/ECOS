package com.chinacreator.gzcm.sysman.security.profile.service;

import com.chinacreator.gzcm.sysman.model.SecurityProfile;

/**
 * 「取用户最高角色档」的<b>属主单源读面</b>（#142/B4-3e-83，收口差距 D-B4-99 d)）。
 *
 * <p><b>属主是谁</b>：{@code td_user_role}（用户↔角色隶属）与 {@code td_role_security_profile}
 * （角色级安全档案）都在 sysman，本接口把「按用户角色取密级最高的那一档」交付为可复用读面。
 * 铁律 #5：跨域事实经属主 api 门面，故安全引擎侧不再抄这段 SQL、也不新增一跳 REST。</p>
 *
 * <p><b>收口前有三处独立实现</b>，其中两处是逐字副本、一处是聚合腿，同一主体可拿到不同档位：</p>
 * <ol>
 *   <li>{@code SecurityProfileQueryDao#findHighestRoleForUser}：行读
 *       {@code ORDER BY rsp.clearance_level DESC LIMIT 1}；</li>
 *   <li>{@code SecurityConfigDao#queryHighestRoleProfileForUser}（security-engine）：上一条的逐字副本；</li>
 *   <li>{@code ClearanceLookupDao#highestRoleClearanceLevel}：聚合腿
 *       {@code COALESCE(MAX(rsp.clearance_level), -1)}。</li>
 * </ol>
 *
 * <p><b>漂移不是理论风险</b>：{@code clearance_level} 在 DDL 上可空
 * （{@code docs/50-db_script/01-ddl-多方言/postgresql/02_ecos_security.sql}：
 * {@code clearance_level INT DEFAULT 0}，显式写 {@code NULL} 即落空值），而 PG 的
 * {@code ORDER BY … DESC} 默认 <b>NULLS FIRST</b>、{@code MAX()} 却<b>忽略 NULL</b>。
 * 于是同一个用户在行读面会取到那条空密级行（{@code rs.getInt} 读成 {@code 0}＝L0 公开），
 * 在聚合面却按「无配置」处理——两个面给出的密级不同，谁生效取决于调用方走了哪条腿。
 * 故本接口的单条语句显式排除空值档（{@code clearance_level IS NOT NULL}），
 * 让「最高档」只有一个含义：<b>非空密级里的最大值</b>。</p>
 *
 * <p><b>本接口只交付事实，不做裁决</b>：返回 {@code null} 表示该用户没有任何带密级的角色档案
 * （未解析），<b>不</b>回落到任何默认档位、也不区分「没配档案」与「档案没填密级」——
 * 两者对「最高档是什么」是同一个答案；取数失败则以异常上抛，由调用方按自己的语义处置
 * （准入面 fail-soft、策略面 fail-loud 转不可用），本读面不吞异常。</p>
 */
public interface ISecurityProfileQueryService {

    /**
     * 取该用户所有角色中密级最高的一档档案。
     *
     * @param userId   主体用户 id（只取信任链读数，禁取客户端自报）
     * @param tenantId 租户 id，可为 {@code null}（既有跨租户读面语义：null 即不带租户过滤）
     * @return 命中的最高档档案；该用户无任何带密级的角色档案 ⇒ {@code null}（禁兜底成任何档位）
     */
    SecurityProfile findHighestRoleForUser(String userId, String tenantId);
}
