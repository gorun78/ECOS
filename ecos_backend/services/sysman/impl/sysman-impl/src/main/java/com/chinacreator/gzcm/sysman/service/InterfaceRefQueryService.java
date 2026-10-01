package com.chinacreator.gzcm.sysman.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chinacreator.gzcm.sysman.dao.InterfaceRefQueryDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * InterfaceRefQueryService — 场景接口引用（{@code ecos_interface_ref}，V149）数据访问层。
 * <p>
 * PMO-74 H9-T4：从 {@code InterfaceRefController} 下沉的 JdbcTemplate 访问层
 * （硬规则「Controller 禁止直用 JdbcTemplate — 必过 Service」，铁律 §1.5）。
 * SQL、结果集列→Map 键映射与 ID 生成收敛到本类，Controller 仅做入参校验、
 * {@code ApiResponse} 包装与审计事件发布。
 * <p>
 * 语义与下沉前逐字段等价：列表按 {@code id} 升序；更新走 {@code COALESCE} 局部更新
 * （null 不覆盖既有值）；删除为逻辑删除（{@code is_deleted = 1}，铁律 4.8）。
 * 数据访问异常原样抛出，由 Controller 的 try-catch 归一为 {@code ApiResponse.internalError}。
 */
@Service
public class InterfaceRefQueryService {

    private static final Logger log = LoggerFactory.getLogger(InterfaceRefQueryService.class);

    private static final String TABLE = "ecos_interface_ref";

    private final InterfaceRefQueryDao dao;

    public InterfaceRefQueryService(InterfaceRefQueryDao dao) {
        this.dao = dao;
    }

    /**
     * 全部未删除接口引用（按 id 升序）。
     *
     * @return 接口引用 Map 列表（永不为 null）
     */
    public List<Map<String, Object>> listAll() {
        return dao.listAll();
    }

    /**
     * 按 ID 查单条。
     *
     * @param id 接口引用主键
     * @return 命中行；不存在返回 {@code null}
     */
    public Map<String, Object> findById(String id) {
        return dao.findById(id);
    }

    /**
     * 存在性校验计数（未删除）。
     *
     * @param id 接口引用主键
     * @return COUNT(1) 结果（保留 {@code Integer} 以对齐下沉前的 null 判定）
     */
    public Integer countById(String id) {
        return dao.queryForObject(
                "SELECT COUNT(1) FROM " + TABLE + " WHERE id = ? AND is_deleted = 0",
                Integer.class, id);
    }

    /**
     * 新建接口引用：生成 {@code ifc_ + 8 位 UUID 片段} 主键并落库。
     *
     * @return 新生成的接口引用 ID
     */
    public String create(String name, String interfaceType, String endpoint, String method, int timeoutMs) {
        String id = "ifc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String sql = "INSERT INTO " + TABLE
                + " (id, name, interface_type, endpoint, method, timeout_ms, create_by, update_by, is_deleted, create_time, update_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, 'system', 'system', 0, NOW(), NOW())";
        dao.update(sql, id, name, interfaceType, endpoint, method, timeoutMs);
        log.debug("ecos_interface_ref insert ok: id={}, name={}", id, name);
        return id;
    }

    /**
     * 局部更新（{@code COALESCE}，null 不覆盖既有值）。
     *
     * @return 受影响行数（0 表示不存在或已删除）
     */
    public int update(String id, String name, String interfaceType, String endpoint,
                      String method, Integer timeoutMs) {
        String sql = """
                UPDATE %s SET
                    name = COALESCE(?, name),
                    interface_type = COALESCE(?, interface_type),
                    endpoint = COALESCE(?, endpoint),
                    method = COALESCE(?, method),
                    timeout_ms = COALESCE(?, timeout_ms),
                    update_time = NOW(),
                    update_by = 'system'
                WHERE id = ? AND is_deleted = 0
                """.formatted(TABLE);
        return dao.update(sql, name, interfaceType, endpoint, method, timeoutMs, id);
    }

    /**
     * 逻辑删除（{@code is_deleted = 1}）。
     *
     * @return 受影响行数（0 表示不存在或已删除）
     */
    public int logicalDelete(String id) {
        return dao.update(
                "UPDATE " + TABLE + " SET is_deleted = 1, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                id);
    }
}
