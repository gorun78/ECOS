package com.chinacreator.gzcm.runtime.core.common.util;

import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObject;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectColumn;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.ScheduleBean;
import org.apache.commons.lang3.StringUtils;
import java.util.List;

public class FlowContorlUtil {

    public static String buildTransDir(boolean isOut) {
        return isOut ? "out_trans" : "in_trans";
    }

    public static String buildTransName(ScheduleBean schedule, String suffix) {
        if (suffix == null) {
            return schedule.getSchedule_name();
        }
        return schedule.getSchedule_name() + "_" + suffix;
    }

    public static String buildTransIncFirstName(String processName) {
        return processName + "_inc_first";
    }

    public static String buildCustomDir() {
        return "custom_trans";
    }

    public static String buildDataObjectSql(String filterSql, String srcSql) {
        if (StringUtils.isBlank(filterSql)) {
            return srcSql;
        }
        return "select * from (" + srcSql + ") t where " + filterSql;
    }
    
    public static String buildDataObjectSql(DataObject dataObject, List<DataObjectColumn> columns, String filterSql, boolean isMDMSchedule) {
        // 简化实现：根据数据对象和字段构建SQL
        if (dataObject == null || columns == null || columns.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(columns.get(i).getColumn_code());
        }
        sql.append(" FROM ").append(dataObject.getParam_table_name());
        if (StringUtils.isNotBlank(filterSql)) {
            sql.append(" WHERE ").append(filterSql);
        }
        return sql.toString();
    }

    public static String getDefaultStartTimeByIncType(String incDataType) {
        if ("Date".equalsIgnoreCase(incDataType) || "Timestamp".equalsIgnoreCase(incDataType)) {
            return "1970-01-01 00:00:00";
        }
        return "0";
    }
    
    public static String getTransNameNoLoseRequest(String jobName) {
        if (jobName != null && jobName.endsWith("_lose")) {
            return jobName.substring(0, jobName.length() - 5);
        }
        return jobName;
    }
    
    public static String getTransNameNoInc(String jobName) {
        if (jobName != null && jobName.endsWith("_inc")) {
            return jobName.substring(0, jobName.length() - 4);
        }
        return jobName;
    }
    
    public static String getInputTransNameNoLoseRequest(String transName) {
        return getTransNameNoLoseRequest(transName);
    }
    
    public static String buildTransLoseRequestName(String transName) {
        if (transName == null) {
            return null;
        }
        return transName + "_lose";
    }
    
    public static String buildInputTransLoseRequestName(ScheduleBean schedule, String shareRefId) {
        String transName = buildTransName(schedule, shareRefId);
        return buildTransLoseRequestName(transName);
    }
}
