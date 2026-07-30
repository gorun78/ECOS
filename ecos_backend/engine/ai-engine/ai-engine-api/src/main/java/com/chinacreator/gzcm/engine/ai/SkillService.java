package com.chinacreator.gzcm.engine.ai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ai.entity.SkillEntity;

/**
 * Skill 技能包 Service 接口
 */
public interface SkillService {

    /** 列出所有技能包，支持分页和条件过滤 */
    List<SkillEntity> listSkills(String category, Boolean enabled);

    /** 根据 ID 查询 */
    Optional<SkillEntity> getSkill(Long id);

    /** 创建/上传技能包 */
    SkillEntity createSkill(Map<String, Object> body);

    /** 更新技能包 */
    Optional<SkillEntity> updateSkill(Long id, Map<String, Object> body);

    /** 删除技能包 */
    boolean deleteSkill(Long id);

    /** 启用/禁用 */
    Optional<SkillEntity> toggleSkill(Long id, boolean enabled);

    /** 版本历史 (同一名称的历史版本列表) */
    List<SkillEntity> listVersions(String name);

    /** 总数量 */
    long totalCount();
}
