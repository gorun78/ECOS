package com.chinacreator.gzcm.gateway.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;

/**
 * F02-08-1 / W46（详细设计-02，P0，回填 C35）—— 数据层失败<b>禁止</b>被掩蔽为 404。
 *
 * <p>历史缺陷：治理栈三处真实数据层异常（{@code MyBatisSystemException} /
 * {@code BadSqlGrammarException}）曾被 {@link GlobalExceptionHandler} 的
 * catch-all 统一翻译为 {@code 404 "端点暂未开放或服务未就绪"}，前端据此"静默回落 legacy"
 * 掩盖缺陷（D-6 a/b/c 三处实测根因）。本测试对该不变量做<b>运行时判定</b>：</p>
 * <ol>
 *   <li>{@code org.springframework.dao.DataAccessException}（含 {@link InvalidDataAccessApiUsageException}
 *       / {@code BadSqlGrammarException} 等全部子类）→ {@code handleSpringDaoAccess}
 *       必须返回 {@code code=500}（HTTP 侧由 {@code @ResponseStatus(500)} 单独落），
 *       <b>不得</b>为 404；</li>
 *   <li>{@code org.apache.ibatis.exceptions.PersistenceException} → {@code handleMyBatisPersistence}
 *       同样 {@code code=500}，不得 404；</li>
 *   <li>兜底 {@code handleAny}（真正的未识别异常 / 路由不存在）→ 才返回 404，
 *       证明 500/404 的区分是"数据层类型精确匹配"而非一刀切。</li>
 * </ol>
 *
 * <p>判定性单测：直接实例化 {@link GlobalExceptionHandler}（无状态）调用各 handler，
 * 断言返回的 {@link ApiResponse} 码值，不依赖 Spring 容器、不触库。
 * 注：具体错误码 {@code ECOS-DATA-031} 由 handler 内部写入 {@code ApiResponse.errorCode}
 * 字段（Jackson 序列化不暴露——无 getter），本测试只判定 W46 的核心不变量=
 * <b>HTTP 语义码 500 而非 404</b>，等价于"数据层失败已不再被掩蔽为端点未就绪"。</p>
 */
@DisplayName("F02-08-1/W46 数据层异常禁掩蔽为 404（P0）")
class NoFourOhFourMaskingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NoFourOhFourMaskingTest — Spring DAO 异常 → 500（非 404）")
    void springDaoException_notMaskedAs404() {
        ApiResponse<Void> resp = handler.handleSpringDaoAccess(
                new InvalidDataAccessApiUsageException("BadSqlGrammar 缺失 FROM 子句"));

        assertEquals(500, resp.getCode(), "数据层失败必须是 500（而非掩蔽成 404）");
        assertNotEquals(404, resp.getCode(), "数据层失败严禁翻译为 404");
        // 反向：未复用 catch-all 的 404 兜底文案（D-6 掩蔽态的辨识特征）
        assertTrue(resp.getMessage() != null && !resp.getMessage().contains("端点暂未开放"),
                "数据层异常不得复用 404 兜底文案；实际 " + resp.getMessage());
    }

    @Test
    @DisplayName("NoFourOhFourMaskingTest — MyBatis PersistenceException → 500（非 404）")
    void mybatisPersistenceException_notMaskedAs404() {
        ApiResponse<Void> resp = handler.handleMyBatisPersistence(
                new PersistenceException("MapperMethod 执行失败", new RuntimeException("root")));

        assertEquals(500, resp.getCode(), "MyBatis 底层失败必须是 500");
        assertNotEquals(404, resp.getCode(), "MyBatis 底层失败严禁翻译为 404");
        assertTrue(resp.getMessage() != null && !resp.getMessage().contains("端点暂未开放"),
                "数据层异常不得复用 404 兜底文案；实际 " + resp.getMessage());
    }

    @Test
    @DisplayName("NoFourOhFourMaskingTest（反向）— 真正未识别异常才走 404，证明区分非一刀切")
    void unhandledRuntime_goto404Only() {
        ApiResponse<Void> resp = handler.handleAny(new IllegalStateException("路由未就绪"));

        assertEquals(404, resp.getCode(), "catch-all 兜底（非数据层）才是 404 的合法落点");
        assertTrue(resp.getMessage() != null && resp.getMessage().contains("端点暂未开放"),
                "catch-all 兜底复用 404 文案（与数据层 500 明确区隔）；实际 " + resp.getMessage());
    }
}
