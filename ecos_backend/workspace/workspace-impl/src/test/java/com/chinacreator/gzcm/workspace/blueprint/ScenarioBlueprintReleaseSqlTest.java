package com.chinacreator.gzcm.workspace.blueprint;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D128 回归（卷01 V-WS-054 取证派生）：发布落库的摘要列绑定。
 *
 * <p>SUP-BP-03 要求发布行回链 context digest；修复前 {@code updateRelease} 把
 * context digest 写进 {@code release_artifact_ref}（制品仓库引用位）而 {@code context_digest}
 * 列完全不参与 SET，导致「context_digest IS NOT NULL」在发布后仍为假。
 * 本类判 DAO 生成的 SQL 与实参绑定：摘要各归其位，且旧形态（缺 context_digest、
 * 占位 release_artifact_ref）一定红。</p>
 */
class ScenarioBlueprintReleaseSqlTest {

    private JdbcTemplate injectDao(ScenarioBlueprintDao dao) throws Exception {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        when(jt.update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1);
        Field f = JdbcTemplateDaoSupport.class.getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(dao, jt);
        return jt;
    }

    @Test
    @DisplayName("D128: 发布 SET 写 context_digest 列, 不写 release_artifact_ref, 摘要按位绑定")
    void releaseBindsContextDigestToItsOwnColumn() throws Exception {
        ScenarioBlueprintDao dao = new ScenarioBlueprintDao();
        JdbcTemplate jt = injectDao(dao);

        dao.updateRelease("bp-1", "RELEASED", "art-digest", "ctx-digest", "actor-1", "APPROVED");

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(jt).update(sqlCap.capture(),
                eq("RELEASED"), eq("art-digest"), eq("ctx-digest"),
                eq("actor-1"), eq("actor-1"), eq("actor-1"), eq("actor-1"),
                eq("bp-1"), eq("APPROVED"));
        String sql = sqlCap.getValue();
        assertTrue(sql.contains("context_digest = ?"),
                "发布必须落 context_digest 列（SUP-BP-03 回链），实得 SQL: " + sql);
        assertFalse(sql.contains("release_artifact_ref"),
                "context digest 不得占用 release_artifact_ref（制品引用位），实得 SQL: " + sql);
        assertTrue(sql.contains("artifact_digest = ?"), "artifact_digest 仍须落库");
        assertTrue(sql.contains("released_time = NOW()"), "发布时间须同批落库");
        assertTrue(sql.contains("WHERE id = ? AND status = ? AND is_deleted = 0"),
                "并发双迁守卫须保留");
    }
}
