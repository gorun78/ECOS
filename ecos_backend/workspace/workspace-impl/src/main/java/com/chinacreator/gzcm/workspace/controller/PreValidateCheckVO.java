package com.chinacreator.gzcm.workspace.controller;

/**
 * 场景预校验单项检查结果 VO（PMO-74 H11-T4）。
 *
 * <p>替代 {@code ScenarioPreValidateController} 原内部构造的
 * {@code Map<String,Object>}（键 name/pass/detail），JSON 形态不变（字段只增不删）。</p>
 */
public class PreValidateCheckVO {

    /** 检查项名称：mind / sandbox_coverage / bindings_exist */
    private String name;

    /** 是否通过 */
    private boolean pass;

    /** 人类可读明细（缺失类目清单 / 命中说明等） */
    private String detail;

    public PreValidateCheckVO() {
    }

    public PreValidateCheckVO(String name, boolean pass, String detail) {
        this.name = name;
        this.pass = pass;
        this.detail = detail;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
