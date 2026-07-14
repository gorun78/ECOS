package com.chinacreator.gzcm.portal.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单管理 — 从 sys-man.TB_MENU_MODULE 表读取，兼容 V1 数据结构。
 */
@RestController
@RequestMapping("/api/system/menus")
public class MenuController {

    private static final Logger log = LoggerFactory.getLogger(MenuController.class);
    private final JdbcTemplate jdbc;

    public MenuController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> tree() {
        try {
            List<Map<String, Object>> flat = loadMenus();
            List<Map<String, Object>> tree = buildTree(flat, "0");
            return ApiResponse.success(tree);
        } catch (Exception e) {
            log.error("获取菜单树失败", e);
            return ApiResponse.success(Collections.emptyList());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        try {
            return ApiResponse.success(loadMenus());
        } catch (Exception e) {
            log.error("获取菜单列表失败", e);
            return ApiResponse.success(Collections.emptyList());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        try {
            List<Map<String, Object>> all = loadMenus();
            Optional<Map<String, Object>> found = all.stream()
                .filter(m -> id.equals(m.get("module_id")))
                .findFirst();
            return found.map(ApiResponse::success)
                .orElse(ApiResponse.notFound("菜单不存在"));
        } catch (Exception e) {
            log.error("获取菜单详情失败", e);
            return ApiResponse.internalError("获取菜单详情失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> loadMenus() {
        String sql = "SELECT MODULE_ID, PARENT_ID, APP_ID, MODULE_NAME, MODEL, "
                   + "USED, ISFOLDER, ICONPATH, ITEMPATH, SEQUENCES, REMARK "
                   + "FROM TB_MENU_MODULE WHERE USED = 1 ORDER BY MODEL, PARENT_ID, SEQUENCES";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("module_id", row.get("MODULE_ID"));
                m.put("parent_id", row.get("PARENT_ID"));
                m.put("app_id", row.get("APP_ID"));
                m.put("module_name", row.get("MODULE_NAME"));
                m.put("model", row.get("MODEL"));
                m.put("used", row.get("USED"));
                m.put("isfolder", row.get("ISFOLDER"));
                m.put("icon", row.get("ICONPATH"));
                m.put("item_path", row.get("ITEMPATH"));
                m.put("seq", row.get("SEQUENCES"));
                m.put("remark", row.get("REMARK"));
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.error("从数据库加载菜单失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> flat, String parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> node : flat) {
            String pid = String.valueOf(node.getOrDefault("parent_id", "0"));
            if (parentId.equals(pid)) {
                Map<String, Object> copy = new LinkedHashMap<>(node);
                List<Map<String, Object>> children = buildTree(flat, String.valueOf(node.get("module_id")));
                if (!children.isEmpty()) {
                    copy.put("children", children);
                }
                tree.add(copy);
            }
        }
        return tree;
    }
}
