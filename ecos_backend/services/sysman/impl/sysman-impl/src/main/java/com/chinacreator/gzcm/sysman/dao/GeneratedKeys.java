package com.chinacreator.gzcm.sysman.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * 序列主键回填（ANSI 取法，替代 PG 私有的 {@code INSERT ... RETURNING id}）。
 *
 * <p><b>为什么要有这个类</b>：世界模型三张表（{@code ecos_wm_goal} / {@code ecos_wm_causal_link}
 * / {@code ecos_wm_scenario}）的主键是 {@code nextval(...)} 序列，原先靠 PG 方言
 * {@code RETURNING id} 取回。{@code SqlDialectDerivationTest} 的 D 族把「裸 RETURNING」
 * 判为方言面违例（一条代码三套发布，多库场景下该语句不可移植）。改走 JDBC 标准的
 * {@code Statement.RETURN_GENERATED_KEYS} 后，PG 驱动回的是 {@code RETURNING *} 等价结果，
 * 语义不变而语法面回到 ANSI。</p>
 *
 * <p><b>为什么不复用 {@code DqRuleIssueDao#resolveGeneratedKey}</b>：那是 data-engine 的
 * 包私有静态方法，跨模块不可见；且本类若直接抄一份就又成了第二份解析正则。当前只有
 * sysman 侧两处 DAO 需要，故收敛在本模块内。</p>
 */
final class GeneratedKeys {

    private GeneratedKeys() {
    }

    /**
     * 执行 INSERT 并返回首个数值型生成键。
     *
     * @param jdbcTemplate Spring JdbcTemplate（调用方 DAO 持有）
     * @param sql          不含 RETURNING 的 INSERT 语句
     * @param args         位置参数
     * @return 生成主键；驱动未回键时抛 {@link IllegalStateException}（不静默返回 0 ——
     *         0 会被上层当成合法 id 继续往下走，把「没拿到键」伪装成「建了一条 id=0 的记录」）
     */
    static long insertAndReturnId(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("INSERT 未返回生成主键: " + sql);
        }
        return key.longValue();
    }
}
