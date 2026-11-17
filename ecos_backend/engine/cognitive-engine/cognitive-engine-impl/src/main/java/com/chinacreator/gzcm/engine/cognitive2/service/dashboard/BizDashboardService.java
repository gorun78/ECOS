package com.chinacreator.gzcm.engine.cognitive2.service.dashboard;

import com.chinacreator.gzcm.engine.cognitive2.dao.BizDashboardDao;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营看板聚合服务（D33）。
 *
 * <p>把 {@link BizDashboardDao} 的四表结果映射成前端 {@code BizDashboard.tsx#BizData} 契约形状
 * （camelCase + 数值降位/转串）；契约改动只增不改（补字段可，删/改名字禁）。</p>
 *
 * <p><b>诚实口径</b>：无数据的字段返回 0 / 空数组（前端自渲染空态），不编造业务数值；
 * {@code departments.manager} 在 {@code td_organization} 无对应列 ⇒ 返回空串（不拿负责人姓名占位造假）。</p>
 */
@Service
public class BizDashboardService {

    private final BizDashboardDao dao;

    public BizDashboardService(BizDashboardDao dao) {
        this.dao = dao;
    }

    /**
     * 聚合经营看板数据。
     */
    public Map<String, Object> dashboard() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("departments", mapDepartments(dao.listDepartments()));
        out.put("projectStats", mapProjectStats(dao.projectStats()));
        out.put("contractStats", mapContractStats(dao.contractStats()));
        out.put("metrics", mapMetrics(dao.listMetrics()));
        out.put("targets", mapTargets(dao.listTargets()));
        // ops 看板（OperationsDashboard）复用本端点，按 raw.monthlyTrend / raw.contractTrend 取；
        // 由 project/contract 按日期月真实聚合，不编造。maintenanceOutput / equipmentHealthRate
        // 无数据源 ⇒ 不返回（前端 numOrNull 渲染「无数据」，不冒充 0）。
        out.put("monthlyTrend", mapMonthlyTrend(dao.projectMonthlyTrend()));
        out.put("contractTrend", mapContractTrend(dao.contractMonthlyTrend()));
        return out;
    }

    private List<Map<String, Object>> mapMonthlyTrend(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", str(r.get("month")));
            m.put("value", toDecimal(r.get("value")));
            m.put("projects", toLong(r.get("projects")));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> mapContractTrend(List<Map<String, Object>> rows) {
        int total = 0;
        for (Map<String, Object> r : rows) {
            total += toLong(r.get("cnt")).intValue();
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", str(r.get("month")));
            // rate = 该月签订笔数占全年签订笔数比例（真实派生，非编造）
            m.put("rate", total == 0 ? 0.0 : toLong(r.get("cnt")).doubleValue() / total);
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> mapDepartments(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", str(r.get("org_id")));
            m.put("name", str(r.get("org_name")));
            m.put("manager", "");
            Object parent = r.get("parent_org_id");
            if (parent != null) {
                m.put("parent_id", str(parent));
            }
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> mapProjectStats(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", toLong(r.get("total")));
        m.put("inProgress", toLong(r.get("in_progress")));
        m.put("completed", toLong(r.get("completed")));
        m.put("planning", toLong(r.get("planning")));
        m.put("totalAmount", toDecimal(r.get("total_amount")));
        return m;
    }

    private Map<String, Object> mapContractStats(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", toLong(r.get("total")));
        // 前端 BizData.contractStats.totalValue 声明为 string
        m.put("totalValue", toDecimal(r.get("total_value")).toPlainString());
        m.put("incomeCount", toLong(r.get("income_count")));
        m.put("expenseCount", toLong(r.get("expense_count")));
        return m;
    }

    private List<Map<String, Object>> mapMetrics(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            // 前端 interface 声明 id: string ⇒ 统一转串（bigint 保真）
            m.put("id", str(r.get("id")));
            m.put("dept_id", str(r.get("dept_id")));
            m.put("metric_type", str(r.get("metric_type")));
            m.put("metric_value", toDecimal(r.get("metric_value")));
            m.put("metric_month", str(r.get("metric_month")));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> mapTargets(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", str(r.get("id")));
            m.put("dept_id", str(r.get("dept_id")));
            m.put("target_type", str(r.get("target_type")));
            m.put("target_value", toDecimal(r.get("target_value")));
            m.put("target_year", toLong(r.get("target_year")));
            out.add(m);
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(o));
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(String.valueOf(o));
    }
}
