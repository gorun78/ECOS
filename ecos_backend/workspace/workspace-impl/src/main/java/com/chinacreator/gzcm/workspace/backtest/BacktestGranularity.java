package com.chinacreator.gzcm.workspace.backtest;

/**
 * 回测粒度词表单源（#146aa 附录A 第 33 条，契约行 = 详设 line 8267
 * {@code granularity ∈ 项目/部门/环节}；机读取值取 DDL 单源）。
 *
 * <p>取值以 DDL 单源为准（{@code docs/50-db_script/02-flyway-migration/gateway/V217__fc_backtest.sql:15}
 * 列 {@code granularity VARCHAR(16)} 注释「PROJECT/DEPARTMENT/STAGE（§3.4 粒度三选）」，
 * 与详设 line 8366 列面表 {@code granularity IN('PROJECT','DEPARTMENT','STAGE')} 同值）。
 * 中文合同词「项目/部门/环节」是<b>展示词</b>，不是 API 入参值——薄承流面只认 DDL 枚举，
 * 入参出现枚举外取值（含中文词、小写、拼写变体）一律 400 逐字段拒绝，禁按
 * 「大小写不敏感」或「别名归一」放行（alias 归一＝第二套口径，与 DDL 单源漂移）。</p>
 */
public enum BacktestGranularity {

    PROJECT("项目"),
    DEPARTMENT("部门"),
    STAGE("环节");

    private final String contractLabel;

    BacktestGranularity(String contractLabel) {
        this.contractLabel = contractLabel;
    }

    /** 契约合同的中文标注（仅取证/回显用，不接收该值作为入参）。 */
    public String contractLabel() {
        return contractLabel;
    }

    /** 入参解析：空白或枚举外取值返回 null（调用方判 400），禁在此处做归一化。 */
    public static BacktestGranularity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BacktestGranularity.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
