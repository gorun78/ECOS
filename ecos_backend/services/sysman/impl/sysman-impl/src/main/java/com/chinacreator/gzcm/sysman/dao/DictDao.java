package com.chinacreator.gzcm.sysman.dao;

import com.chinacreator.gzcm.sysman.dict.entity.SysDict;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * 数据字典（sys_dict）数据访问 DAO — 承接 DictService 中的 JdbcTemplate 操作。
 */
@Repository
public class DictDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    private static final String COLS =
            "id, dict_type, dict_code, dict_label, dict_label_en, sort_order, " +
            "status, parent_code, ext_value, subsystem, created_at, updated_at";

    private static final RowMapper<SysDict> ROW_MAPPER = new SysDictRowMapper();

    /** 自动计算 sort_order：取该类型的最大值（缺省 0）。 */
    public Integer maxSortOrder(String dictType) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(sort_order), 0) FROM sys_dict WHERE dict_type = ?",
            Integer.class, dictType);
    }

    /** 插入字典项。 */
    public void insert(SysDict dict) {
        jdbcTemplate.update(
            "INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order, status, parent_code, ext_value, subsystem, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            dict.getId(), dict.getDictType(), dict.getDictCode(), dict.getDictLabel(),
            dict.getDictLabelEn(), dict.getSortOrder(),
            dict.getStatus() != null ? dict.getStatus() : "active",
            dict.getParentCode(), dict.getExtValue(),
            dict.getSubsystem());
    }

    /** 更新字典项。 */
    public void update(SysDict dict, String dictType, String dictCode) {
        jdbcTemplate.update(
            "UPDATE sys_dict SET dict_label = ?, dict_label_en = ?, sort_order = ?, parent_code = ?, ext_value = ?, subsystem = ?, updated_at = NOW() " +
            "WHERE dict_type = ? AND dict_code = ?",
            dict.getDictLabel(), dict.getDictLabelEn(), dict.getSortOrder(),
            dict.getParentCode(), dict.getExtValue(), dict.getSubsystem(),
            dictType, dictCode);
    }

    /** 逻辑删除字典项。 */
    public void deactivate(String dictType, String dictCode) {
        jdbcTemplate.update(
            "UPDATE sys_dict SET status = 'inactive', updated_at = NOW() WHERE dict_type = ? AND dict_code = ?",
            dictType, dictCode);
    }

    /** 全量加载 active 字典项。 */
    public List<SysDict> listAllActive() {
        return jdbcTemplate.query(
            "SELECT " + COLS
            + " FROM sys_dict WHERE status = 'active' ORDER BY dict_type, sort_order",
            ROW_MAPPER);
    }

    /** 按类型加载 active 字典项。 */
    public List<SysDict> listByType(String dictType) {
        return jdbcTemplate.query(
            "SELECT " + COLS
            + " FROM sys_dict WHERE dict_type = ? AND status = 'active' ORDER BY sort_order",
            ROW_MAPPER, dictType);
    }

    /** RowMapper 实现 */
    private static class SysDictRowMapper implements RowMapper<SysDict> {
        @Override
        public SysDict mapRow(ResultSet rs, int rowNum) throws SQLException {
            SysDict d = new SysDict();
            d.setId(rs.getString("id"));
            d.setDictType(rs.getString("dict_type"));
            d.setDictCode(rs.getString("dict_code"));
            d.setDictLabel(rs.getString("dict_label"));
            d.setDictLabelEn(rs.getString("dict_label_en"));
            d.setSortOrder(rs.getInt("sort_order"));
            d.setStatus(rs.getString("status"));
            d.setParentCode(rs.getString("parent_code"));
            d.setExtValue(rs.getString("ext_value"));
            d.setSubsystem(rs.getString("subsystem"));

            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) d.setCreatedAt(ca.toLocalDateTime());
            Timestamp ua = rs.getTimestamp("updated_at");
            if (ua != null) d.setUpdatedAt(ua.toLocalDateTime());

            return d;
        }
    }
}
