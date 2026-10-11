package com.chinacreator.gzcm.workspace.forecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 预测 run 证据出参（H-5 第 6 要素「证据引用」的展开形态）。
 *
 * <p>文本/条目由后端组装（引擎/证据源），编排面<b>只搬运不改写、不拼公式</b>
 * （R-62 ① + 卷 10 F10-24 证据链纪律）。锚定六要素保证证据与 run 可追溯同属。</p>
 */
public final class ForecastRunEvidence {

    private final String forecastRunId;
    private final String caliberId;
    private final String caliberVersion;
    private final String asOfTime;
    private final String snapshotId;
    private final String formulaVersion;
    private final List<String> evidenceRefs;
    private final List<Map<String, Object>> evidenceItems;

    public ForecastRunEvidence(String forecastRunId, String caliberId, String caliberVersion,
                               String asOfTime, String snapshotId, String formulaVersion,
                               List<String> evidenceRefs, List<Map<String, Object>> evidenceItems) {
        this.forecastRunId = forecastRunId;
        this.caliberId = caliberId;
        this.caliberVersion = caliberVersion;
        this.asOfTime = asOfTime;
        this.snapshotId = snapshotId;
        this.formulaVersion = formulaVersion;
        this.evidenceRefs = evidenceRefs == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(evidenceRefs));
        this.evidenceItems = evidenceItems == null
                ? Collections.<Map<String, Object>>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(evidenceItems));
    }

    public String getForecastRunId() {
        return forecastRunId;
    }

    public String getCaliberId() {
        return caliberId;
    }

    public String getCaliberVersion() {
        return caliberVersion;
    }

    public String getAsOfTime() {
        return asOfTime;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getFormulaVersion() {
        return formulaVersion;
    }

    public List<String> getEvidenceRefs() {
        return evidenceRefs;
    }

    public List<Map<String, Object>> getEvidenceItems() {
        return evidenceItems;
    }
}
