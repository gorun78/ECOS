package com.chinacreator.gzcm.engine.kb.nav.model;

import java.util.List;

/**
 * 导航模块列表 VO — {@code GET /api/v1/knowledge/nav/modules} 返回体（F10）。
 *
 * <p>数据源：{@link com.chinacreator.gzcm.engine.kb.KbEngineModuleRegistry} 常量表，
 * 不走 DB / Neo4j / pgvector，响应 ≤ 5ms。
 *
 * @since PMO-D 2026-09-24
 */
public class NavModulesVO {

    /** 7 个模块列表（顺序即前端侧栏展示顺序） */
    private List<ModuleInfoVO> modules;

    public NavModulesVO() {
    }

    public List<ModuleInfoVO> getModules() { return modules; }
    public void setModules(List<ModuleInfoVO> modules) { this.modules = modules; }
}
