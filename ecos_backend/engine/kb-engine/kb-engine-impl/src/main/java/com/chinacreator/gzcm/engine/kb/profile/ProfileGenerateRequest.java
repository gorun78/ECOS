package com.chinacreator.gzcm.engine.kb.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史画像生成请求（F04-06 / D.3）。
 * <p>
 * body = {metricCodes:[], dims:{}, windowFrom, windowTo, dryRun}。
 *
 * @author ECOS KB Team
 */
public class ProfileGenerateRequest {

    /** 指标编码列表（对齐 PRD-03，口径只引用不定义）。 */
    private List<String> metricCodes;

    /** 分组维度（如 project_type/department/stage）。 */
    private Map<String, Object> dims = new LinkedHashMap<>();

    /** 观察窗口起 YYYY-MM。 */
    private String windowFrom;

    /** 观察窗口止 YYYY-MM。 */
    private String windowTo;

    /** 是否干跑（不写库，仅统计）。 */
    private boolean dryRun;

    public List<String> getMetricCodes() {
        return metricCodes;
    }

    public void setMetricCodes(List<String> metricCodes) {
        this.metricCodes = metricCodes;
    }

    public Map<String, Object> getDims() {
        return dims;
    }

    public void setDims(Map<String, Object> dims) {
        this.dims = dims;
    }

    public String getWindowFrom() {
        return windowFrom;
    }

    public void setWindowFrom(String windowFrom) {
        this.windowFrom = windowFrom;
    }

    public String getWindowTo() {
        return windowTo;
    }

    public void setWindowTo(String windowTo) {
        this.windowTo = windowTo;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
