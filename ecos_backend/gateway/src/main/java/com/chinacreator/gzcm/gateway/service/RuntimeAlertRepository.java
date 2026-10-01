package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.gateway.dao.RuntimeAlertDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 §1.6.4 铁律：Controller 不直接访问 JdbcTemplate，走 Service 层。
 * 该 Service 承担 F00-09 告警中心与 DLQ 重放的持久化（表由 V242/V243 建立）。
 *
 * <p>B6b 数据访问收口（架构铁律 §2.5 / IR06）：裸 SQL 全量下沉至
 * {@link RuntimeAlertDao}；本 Service 减薄为编排层，公共方法签名维持不变。</p>
 */
@Service
public class RuntimeAlertRepository {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAlertRepository.class);
    private final RuntimeAlertDao dao;

    public RuntimeAlertRepository(RuntimeAlertDao dao) { this.dao = dao; }

    public List<Map<String, Object>> listAlerts(String severity, String status, int size, int offset) {
        try {
            return dao.listAlerts(severity, status, size, offset);
        } catch (Exception ex) {
            log.error("listAlerts: {}", ex.getMessage()); throw ex;
        }
    }

    public int countAlerts(String severity, String status) {
        return dao.countAlerts(severity, status);
    }

    public List<Map<String, Object>> findAlertById(String id) {
        try {
            return dao.findAlertById(id);
        } catch (Exception ex) {
            log.error("findAlertById: {}", ex.getMessage()); throw ex;
        }
    }

    public int ackAlert(String id, String by) {
        try {
            return dao.ackAlert(id, by);
        } catch (Exception ex) {
            log.error("ackAlert: {}", ex.getMessage()); throw ex;
        }
    }

    public List<Map<String, Object>> listDlq(String status, int size, int offset) {
        try {
            return dao.listDlq(status, size, offset);
        } catch (Exception ex) {
            log.error("listDlq: {}", ex.getMessage()); throw ex;
        }
    }

    public int countDlq(String status) {
        return dao.countDlq(status);
    }

    public int replayDlq(String id) {
        try {
            return dao.replayDlq(id);
        } catch (Exception ex) {
            log.error("replayDlq: {}", ex.getMessage()); throw ex;
        }
    }
}
