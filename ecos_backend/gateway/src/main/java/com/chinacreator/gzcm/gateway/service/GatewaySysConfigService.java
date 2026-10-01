package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.gateway.dao.SysConfigDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务 — 从 SysConfigController 下沉的 JdbcTemplate 访问层。
 * SQL 语义与原 Controller 保持一致。
 *
 * <p>B6b 数据访问收口（架构铁律 §2.5 / IR06）：裸 SQL 全量下沉至 {@link SysConfigDao}；
 * 本 Service 减薄为编排层，公共方法签名维持不变。</p>
 */
@Service
public class GatewaySysConfigService {

    private final SysConfigDao dao;

    public GatewaySysConfigService(SysConfigDao dao) {
        this.dao = dao;
    }

    /** 查询全部配置（带 group / edition 过滤） */
    public List<Map<String, Object>> queryConfigs(String sql, Object[] params) {
        return dao.queryConfigs(sql, params);
    }

    /** 配置审计查询 */
    public List<Map<String, Object>> queryConfigAudit() {
        return dao.queryConfigAudit();
    }

    /** 更新单个配置值，返回受影响行数 */
    public int updateConfigValue(String val, String configKey) {
        return dao.updateConfigValue(val, configKey);
    }

    // ── PMO-E2 通用委托方法 ──
    public List<Map<String, Object>> queryForList(String sql) {
        return dao.queryForList(sql);
    }
    public List<Map<String, Object>> queryForList(String sql, Object[] params) {
        return dao.queryForList(sql, params);
    }
    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
