package com.chinacreator.gzcm.engine.cognitive.api.outcome;

import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 评估窗口（《认知域与智能域补充详细设计 v1.0》§9.3 {@code evaluationWindow}）。
 *
 * <p>§9.2 步骤 2「对齐口径」要求预测与实际在<b>同一时间范围</b>下比较，
 * 本类承载该范围。日期以 {@code yyyy-MM-dd} 字符串入参（避免 Jackson
 * JavaTimeModule 装配差异），落库对应 V267 {@code window_from_date}/{@code window_to_date}
 * （DATE 无时区，MC03）。</p>
 */
@Data
public class EvaluationWindow {

    /** 窗口起（含），{@code yyyy-MM-dd}。 */
    private String from;

    /** 窗口止（含），{@code yyyy-MM-dd}。 */
    private String to;

    /**
     * 契约校验：返回问题列表（空 = 合法）。
     * 规则：两端必填、格式可解析、{@code from <= to}（倒置窗口不得静默纠正，
     * 否则误差归因失真 —— §9.2 步骤 3「样本不适用时返回明确原因」）。
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        LocalDate f = parse(from, "from", problems);
        LocalDate t = parse(to, "to", problems);
        if (f != null && t != null && f.isAfter(t)) {
            problems.add("evaluationWindow inverted (from " + from + " > to " + to + ")");
        }
        return problems;
    }

    /** 窗口天数（含端点）；任一端不可解析返回 -1（调用方须先过 {@link #validate()}）。 */
    public int days() {
        LocalDate f = parseQuiet(from);
        LocalDate t = parseQuiet(to);
        if (f == null || t == null) {
            return -1;
        }
        return (int) (t.toEpochDay() - f.toEpochDay() + 1);
    }

    /** 转 SQL DATE 用 LocalDate；不可解析返回 null（由 validate 前置拦截）。 */
    public LocalDate fromDate() {
        return parseQuiet(from);
    }

    /** 转 SQL DATE 用 LocalDate；不可解析返回 null（由 validate 前置拦截）。 */
    public LocalDate toDate() {
        return parseQuiet(to);
    }

    private static LocalDate parse(String raw, String field, List<String> problems) {
        LocalDate d = parseQuiet(raw);
        if (raw == null || raw.isBlank()) {
            problems.add(field);
            return null;
        }
        if (d == null) {
            problems.add(field + " not parseable as yyyy-MM-dd");
        }
        return d;
    }

    private static LocalDate parseQuiet(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
