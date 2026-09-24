package com.chinacreator.gzcm.engine.kb.nav.model;

/**
 * 模块信息 VO — 描述一个前端页面模块及其归属 Controller 列表。
 *
 * <p>用于 {@code GET /api/v1/knowledge/nav/modules} 返回体
 * {@link NavModulesVO} 的列表项（F10 后端 Controller 分组文档化）。
 *
 * @since PMO-D 2026-09-24
 */
public class ModuleInfoVO {

    /** 模块名（如 "overview" / "assets" / "extract" / "graph" / "wiki" / "govern" / "center_p3"） */
    private String moduleName;
    /** 归属 Controller simple name 列表（如 ["KbEngineHealthController", ...]） */
    private java.util.List<String> controllers;

    public ModuleInfoVO() {
    }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public java.util.List<String> getControllers() { return controllers; }
    public void setControllers(java.util.List<String> controllers) { this.controllers = controllers; }
}
