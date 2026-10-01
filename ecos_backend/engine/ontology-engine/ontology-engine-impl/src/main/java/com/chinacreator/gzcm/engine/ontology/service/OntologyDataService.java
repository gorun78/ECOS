package com.chinacreator.gzcm.engine.ontology.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ontology.dao.OntologyDataDao;

/**
 * 本体数据实例持久化服务 — 委托 {@link OntologyDataDao} 承载
 * {@code ecos_ontology.ecos_ontology_data} 表的全部 JDBC 访问。
 *
 * <p>由 {@code OntologyDataController} 调用：把数据记录（ObjectType 下的实例）
 * 的列表/详情/新增/更新/计数 SQL 收敛到 DAO 层
 * （R2-B3 / 架构铁律 §2.5 #1：Service 不直持 JdbcTemplate）。
 *
 * <p>所有查询带 {@code is_deleted = 0} 过滤（逻辑删除语义）；
 * 返回 {@code List<Map<String, Object>>} 原始行，row→前端契约 Map 的映射
 * （payload JSON 反序列化、字段名对齐）保留在调用侧。
 */
@Service
public class OntologyDataService {

    private static final Logger log = LoggerFactory.getLogger(OntologyDataService.class);

    private final OntologyDataDao dataDao;

    public OntologyDataService(OntologyDataDao dataDao) {
        this.dataDao = dataDao;
    }

    /**
     * 数据记录总数（可选按归属域过滤）。
     *
     * @param filterType 可选，对象类型 ID（对应 ontology_id 列），空/空白 = 全量
     * @return 匹配 is_deleted=0 的行数
     */
    public Integer countRecords(String filterType) {
        return dataDao.countRecords(filterType);
    }

    /**
     * 数据记录分页查询（可选按归属域过滤，按 create_time 倒序）。
     *
     * @param filterType 可选，对象类型 ID（对应 ontology_id 列），空/空白 = 全量
     * @param limit      每页大小
     * @param offset     偏移量
     * @return 原始行列表
     */
    public List<Map<String, Object>> listRecords(String filterType, int limit, int offset) {
        return dataDao.listRecords(filterType, limit, offset);
    }

    /**
     * 按主键查询单条数据记录（is_deleted=0 过滤）。
     *
     * @param id 记录主键（全量 UUID）
     * @return 原始行列表（命中 0 或 1 行）
     */
    public List<Map<String, Object>> findRecordById(String id) {
        return dataDao.findRecordById(id);
    }

    /**
     * 插入数据记录（全量 UUID 主键，status 固定 'ACTIVE'，is_deleted=0）。
     *
     * @return 受影响行数
     */
    public int insertRecord(String id, String objectTypeId, String objectTypeName,
                            String recordKey, String payloadJson, String createdBy) {
        return dataDao.insertRecord(id, objectTypeId, objectTypeName, recordKey, payloadJson, createdBy);
    }

    /**
     * 更新数据记录（COALESCE 风格：null 参数不覆盖已有值；objectTypeId 不可变更）。
     *
     * @return 受影响行数
     */
    public int updateRecord(String id, String objectTypeName, String createdBy, String payloadJson) {
        return dataDao.updateRecord(id, objectTypeName, createdBy, payloadJson);
    }

    /**
     * 按主键计数（存在性检查，is_deleted=0 过滤）。
     */
    public Integer countById(String id) {
        return dataDao.countById(id);
    }

    /**
     * 按归属域计数（is_deleted=0 过滤）。
     */
    public Integer countByObjectType(String objectTypeId) {
        return dataDao.countByObjectType(objectTypeId);
    }
}
