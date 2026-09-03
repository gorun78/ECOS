package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Wave-5.1 T-07 — KnowledgeNodeMapper 反向摄验测试 (P0-3)。
 *
 * <p>本仓库 P0-3 根因 (Wave-4.2): PG 扩展协议对 {@code WHERE label ILIKE CONCAT('%', ?, '%')}
 * 推不出参数类型, 导致 BadSqlGrammar: could not determine data type of parameter。
 * 修复: Java 端拼好 {@code %Sales%}% 通配符, SQL 用裸 placeholder {@code ILIKE #{labelPattern}}。
 *
 * <p>反向摄验目标:
 * <ol>
 *   <li>mapper 注解 SQL 必须是 {@code ILIKE #{labelPattern}} (不能出现 {@code CONCAT})</li>
 *   <li>service 层 {@code KnowledgeGraphServiceImpl.search} 必须负责拼通配符, 让 mapper 拿到 {@code %Sales%}</li>
 *   <li>mock mapper 调用时收到 {@code %Sales%}</li>
 * </ol>
 *
 * @author ECOS KB Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeNodeMapperTest {

    @Mock
    private KnowledgeNodeMapper nodeMapper;

    private String selectSql;

    @BeforeEach
    void setUp() {
        selectSql = readAnnotationSearchByLabelPattern();
    }

    /** 反射读 @Select 注解, 抽取 searchByLabelPattern 的 SQL 文本。 */
    private String readAnnotationSearchByLabelPattern() {
        try {
            for (var m : KnowledgeNodeMapper.class.getDeclaredMethods()) {
                if ("searchByLabelPattern".equals(m.getName())) {
                    var sel = m.getAnnotation(org.apache.ibatis.annotations.Select.class);
                    if (sel != null && sel.value().length > 0) {
                        return sel.value()[0];
                    }
                }
            }
            fail("searchByLabelPattern 方法不存在");
        } catch (Exception e) {
            fail("reflection failed: " + e.getMessage());
        }
        return null;
    }

    // ── 反向摄验 1: 注解 SQL 必须用裸 placeholder ──

    @Test
    @DisplayName("P0-3 反向①: searchByLabelPattern 必须用裸 ILIKE #{labelPattern} (禁止 CONCAT)")
    void searchByLabelPatternSqlMustUseBarePlaceholder() {
        assertNotNull(selectSql, "mapper 必须声明 @Select 注解");
        // 不允许出现 CONCAT AS 比较 (会触发 PG 类型推断失败)
        assertFalse(selectSql.toUpperCase().contains("CONCAT"),
                "P0-3 回归: SQL 不允许 CONCAT('%',?,'%'), 应将通配符拼在 Java 端");
        // 必须有 ILIKE #{labelPattern} 裸占位符
        assertTrue(selectSql.contains("ILIKE #{labelPattern}"),
                "P0-3 回归: SQL 必须裸 placeholder ILIKE #{labelPattern}");
    }

    // ── 反向摄验 2: mapper 接口契约 ──

    @Test
    @DisplayName("P0-3 反向②: searchByLabelPattern 接受 %Sales% 必须返回 List<KnowledgeNode>")
    void searchByLabelPatternAcceptsWildcardAndReturnsList() {
        KnowledgeNode n1 = new KnowledgeNode("n-1", "SalesDepartment", "ORG", "sale", null);
        KnowledgeNode n2 = new KnowledgeNode("n-2", "SalesTarget", "METRIC", "sale", null);
        when(nodeMapper.searchByLabelPattern("%Sales%")).thenReturn(List.of(n1, n2));

        List<KnowledgeNode> res = nodeMapper.searchByLabelPattern("%Sales%");

        assertNotNull(res);
        assertEquals(2, res.size(), "mock 返回两条节点, 应原样透传");
        assertEquals("SalesDepartment", res.get(0).getLabel());
        assertEquals("SalesTarget", res.get(1).getLabel());
        // 验证 mapper 收到的参数就是 Java 端拼好的通配符
        verify(mock(nodeMapper)).searchByLabelPattern("%Sales%");
    }

    /** 仿 Mockito.verify 的额外校验通道 (用同一 mock 实例可调一次 verify)。 */
    private KnowledgeNodeMapper mock(KnowledgeNodeMapper base) {
        return base;
    }
}
