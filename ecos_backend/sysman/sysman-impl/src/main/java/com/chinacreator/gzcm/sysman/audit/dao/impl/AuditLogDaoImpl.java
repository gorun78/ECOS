package com.chinacreator.gzcm.sysman.audit.dao.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.audit.dao.AuditLogDao;
import com.chinacreator.gzcm.sysman.audit.entity.AuditLog;

/**
 * 瀹¤鏃ュ織DAO瀹炵幇
 * 閫氳繃Runtime鐨勭郴缁熸暟鎹簱璁块棶鎺ュ彛瀹屾垚鏁版嵁搴撴搷浣?
 */
@Repository
public class AuditLogDaoImpl implements AuditLogDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/audit/dao/impl/AuditLog-sql.xml";

    @Autowired
    public AuditLogDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(AuditLog auditLog) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertAuditLog", auditLog);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("鎻掑叆瀹¤鏃ュ織澶辫触: " + e.getMessage(), e);
        }
    }

    @Override
    public AuditLog findById(String logId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findById", AuditLog.class, logId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("鏌ヨ瀹¤鏃ュ織澶辫触: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AuditLog> query(AuditLog condition, int offset, int limit) throws Exception {
        try {
            // 直接构建SQL，使用td_audit_log实际列名
            StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM td_audit_log WHERE 1=1");
            List<Object> paramList = new java.util.ArrayList<>();
            if (condition != null) {
                if (condition.getUserId() != null) { sqlBuilder.append(" AND \"USER_ID\" = ?"); paramList.add(condition.getUserId()); }
                if (condition.getEventType() != null) { sqlBuilder.append(" AND \"ACTION\" = ?"); paramList.add(condition.getEventType()); }
                if (condition.getResource() != null) { sqlBuilder.append(" AND \"RESOURCE_TYPE\" LIKE ?"); paramList.add("%" + condition.getResource() + "%"); }
                if (condition.getResult() != null) { sqlBuilder.append(" AND \"OPERATION_RESULT\" = ?"); paramList.add(condition.getResult()); }
            }
            sqlBuilder.append(" ORDER BY \"CREATED_TIME\" DESC");
            
            List<Map<String, Object>> rows = databaseAccess.executeQuery(sqlBuilder.toString(), paramList.toArray());
            List<AuditLog> all = new java.util.ArrayList<>();
            for (Map<String, Object> row : rows) {
                AuditLog log = new AuditLog();
                log.setLogId((String) row.get("LOG_ID"));
                log.setUserId((String) row.get("USER_ID"));
                log.setAction((String) row.get("ACTION"));
                log.setEventType((String) row.get("ACTION"));  // 复用ACTION作为eventType
                log.setResource((String) row.get("RESOURCE_TYPE"));
                log.setResult((String) row.get("OPERATION_RESULT"));
                log.setIpAddress((String) row.get("IP_ADDRESS"));
                log.setUserAgent((String) row.get("USER_AGENT"));
                log.setDetails((String) row.get("REQUEST_DATA"));
                Object ts = row.get("CREATED_TIME");
                if (ts instanceof java.sql.Timestamp) log.setTimestamp(((java.sql.Timestamp) ts).toLocalDateTime());
                else if (ts instanceof java.time.LocalDateTime) log.setTimestamp((java.time.LocalDateTime) ts);
                all.add(log);
            }
            if (all == null || all.isEmpty()) {
                return new java.util.ArrayList<>();
            }
            int fromIndex = Math.min(offset, all.size());
            int toIndex = Math.min(offset + limit, all.size());
            return all.subList(fromIndex, toIndex);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("鏌ヨ瀹¤鏃ュ織鍒楄〃澶辫触: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AuditLog> query(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime, int offset, int limit) throws Exception {
        try {
            // 鏋勫缓鍖呭惈鏃堕棿鑼冨洿鐨勬煡璇㈡潯浠?
            Map<String, Object> params = new java.util.HashMap<>();
            if (condition != null) {
                // 灏哻ondition杞崲涓篗ap
                params.put("userId", condition.getUserId());
                params.put("tenantId", condition.getTenantId());
                params.put("resource", condition.getResource());
                params.put("eventType", condition.getEventType());
                params.put("result", condition.getResult());
            }
            params.put("startTime", startTime);
            params.put("endTime", endTime);
            
            List<AuditLog> all = databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "queryAuditLogWithTimeRange", AuditLog.class, params);
            if (all == null || all.isEmpty()) {
                return new java.util.ArrayList<>();
            }
            int fromIndex = Math.min(offset, all.size());
            int toIndex = Math.min(offset + limit, all.size());
            return all.subList(fromIndex, toIndex);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("鏌ヨ瀹¤鏃ュ織鍒楄〃澶辫触: " + e.getMessage(), e);
        }
    }

    @Override
    public long count(AuditLog condition) throws Exception {
        try {
            Long count = databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "countAuditLog", Long.class, condition);
            return count != null ? count : 0;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("缁熻瀹¤鏃ュ織鏁伴噺澶辫触: " + e.getMessage(), e);
        }
    }

    @Override
    public Long count(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        try {
            Map<String, Object> params = new java.util.HashMap<>();
            if (condition != null) {
                params.put("userId", condition.getUserId());
                params.put("tenantId", condition.getTenantId());
                params.put("resource", condition.getResource());
                params.put("eventType", condition.getEventType());
                params.put("result", condition.getResult());
            }
            params.put("startTime", startTime);
            params.put("endTime", endTime);
            
            Long count = databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "countAuditLogWithTimeRange", Long.class, params);
            return count != null ? count : 0L;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("缁熻瀹¤鏃ュ織鏁伴噺澶辫触: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Long> countByEventType(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        return countByField("event_type", condition, startTime, endTime);
    }

    @Override
    public Map<String, Long> countByUser(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        return countByField("user_id", condition, startTime, endTime);
    }

    @Override
    public Map<String, Long> countByResource(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        return countByField("resource", condition, startTime, endTime);
    }

    @Override
    public Map<String, Long> countByDate(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        try {
            // 鎸夋棩鏈熺粺璁￠渶瑕佺壒娈婂鐞嗭紝浣跨敤DATE鍑芥暟
            String sql = "SELECT DATE(timestamp) as date_value, COUNT(*) as count FROM td_audit_log " +
                    "WHERE timestamp >= ? AND timestamp <= ? ";
            List<Object> params = new java.util.ArrayList<>();
            params.add(startTime);
            params.add(endTime);
            
            // 娣诲姞鏉′欢杩囨护
            if (condition != null) {
                if (condition.getUserId() != null) {
                    sql += " AND user_id = ?";
                    params.add(condition.getUserId());
                }
                if (condition.getTenantId() != null) {
                    sql += " AND tenant_id = ?";
                    params.add(condition.getTenantId());
                }
                if (condition.getResource() != null) {
                    sql += " AND resource = ?";
                    params.add(condition.getResource());
                }
                if (condition.getEventType() != null) {
                    sql += " AND event_type = ?";
                    params.add(condition.getEventType());
                }
            }
            
            sql += " GROUP BY DATE(timestamp) ORDER BY date_value";
            
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql, params.toArray());
            
            Map<String, Long> resultMap = new java.util.HashMap<>();
            for (Map<String, Object> row : results) {
                String dateValue = row.get("date_value") != null ? row.get("date_value").toString() : "NULL";
                Long count = row.get("count") != null ? ((Number) row.get("count")).longValue() : 0L;
                resultMap.put(dateValue, count);
            }
            return resultMap;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("鎸夋棩鏈熺粺璁″璁℃棩蹇楀け璐? " + e.getMessage(), e);
        }
    }

    private Map<String, Long> countByField(String field, AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        try {
            String sql = "SELECT " + field + " as field_value, COUNT(*) as count FROM td_audit_log " +
                    "WHERE timestamp >= ? AND timestamp <= ? ";
            List<Object> params = new java.util.ArrayList<>();
            params.add(startTime);
            params.add(endTime);
            
            // 娣诲姞鏉′欢杩囨护
            if (condition != null) {
                if (condition.getUserId() != null) {
                    sql += " AND user_id = ?";
                    params.add(condition.getUserId());
                }
                if (condition.getTenantId() != null) {
                    sql += " AND tenant_id = ?";
                    params.add(condition.getTenantId());
                }
                if (condition.getResource() != null) {
                    sql += " AND resource = ?";
                    params.add(condition.getResource());
                }
                if (condition.getEventType() != null) {
                    sql += " AND event_type = ?";
                    params.add(condition.getEventType());
                }
            }
            
            sql += " GROUP BY " + field;
            
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql, params.toArray());
            
            Map<String, Long> resultMap = new java.util.HashMap<>();
            for (Map<String, Object> row : results) {
                String fieldValue = row.get("field_value") != null ? row.get("field_value").toString() : "NULL";
                Long count = row.get("count") != null ? ((Number) row.get("count")).longValue() : 0L;
                resultMap.put(fieldValue, count);
            }
            return resultMap;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("鎸夊瓧娈电粺璁″璁℃棩蹇楀け璐? " + e.getMessage(), e);
        }
    }
}


