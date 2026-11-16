package com.chinacreator.gzcm.runtime.core.task.persistence.dao;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D115 门禁（2026-11-15）：{@link TaskPersistenceDao} 的 INSERT 必须满足
 * 「SQL 占位符数 == 绑定实参数」。
 *
 * <p>为什么需要：这些 SQL 在 {@code runtime.task.persistence.enabled} 未开启时
 * 对应 Bean 不装配，整段代码<b>从未被执行</b>。因此列/占位符/实参的漂移不会被
 * 任何活体路径发现。D115 实测三条 INSERT 全错位（少 1~2 个 {@code ?} 或实参），
 * 开关一打开立刻报 {@code 未设定参数值 21} / {@code 栏位索引超过许可范围}。</p>
 *
 * <p>不依赖 Mockito：{@link JdbcTemplate} 的子类化在 Spring 下不稳，改用
 * 「把 jdbcTemplate 字段替换成一个记录型 JdbcTemplate 子类」太脆弱 —— 这里直接把
 * SQL 与实参记录到静态列表，走真实 {@code jdbcTemplate.update} 调用路径。</p>
 */
class TaskPersistenceInsertArityTest {

    /** 记录每次 update(sql, args...) 的真实调用。 */
    static final List<String> SQLS = new ArrayList<>();
    static final List<Object[]> ARG_SETS = new ArrayList<>();

    private TaskPersistenceDao dao;

    @BeforeEach
    void setUp() throws Exception {
        SQLS.clear();
        ARG_SETS.clear();
        dao = new TaskPersistenceDao();
        RecordingJdbcTemplate recorder = new RecordingJdbcTemplate();
        Field f = TaskPersistenceDao.class.getSuperclass().getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(dao, recorder);
    }

    private void assertArity(String label, Runnable call) {
        call.run();
        String sql = null;
        Object[] args = null;
        for (int i = SQLS.size() - 1; i >= 0; i--) {
            if (SQLS.get(i).trim().startsWith("INSERT")) {
                sql = SQLS.get(i);
                args = ARG_SETS.get(i);
                break;
            }
        }
        assertTrue(sql != null, label + " 应至少有一次 INSERT 调用");
        int placeholders = countPlaceholders(sql);
        assertEquals(placeholders, args.length,
                label + " 的 ? 占位符数必须等于绑定实参数：sql=" + sql.replaceAll("\\s+", " "));
    }

    /** 只数 VALUES(...) 里的顶层 ?（跳过 NOW()/COALESCE 等函数体）。 */
    static int countPlaceholders(String sql) {
        int vi = sql.indexOf("VALUES");
        if (vi < 0) {
            return (int) sql.chars().filter(c -> c == '?').count();
        }
        int depth = 0;
        int q = 0;
        for (int i = vi; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '?' && depth == 1) {
                q++;
            } else if (c == ',' && depth == 0) {
                break;
            }
        }
        return q;
    }

    private static TaskDescription sampleTask() {
        TaskDescription t = new TaskDescription();
        t.setTaskId("t-1");
        t.setTaskName("name");
        t.setTaskType("DATA_SYNC");
        t.setDescription("d");
        t.setTaskConfig("{}");
        t.setPriority(1);
        t.setTimeout(1000L);
        t.setRetryCount(0);
        t.setAsync(false);
        t.setCreatedBy("admin");
        t.setTenantId("tenant-a");
        t.setCreateTime(new Date());
        return t;
    }

    @Test
    @DisplayName("D115: saveTask 的 ? 占位符数必须等于绑定实参数")
    void saveTaskPlaceholdersMatchBoundArgs() {
        assertArity("saveTask", () -> dao.saveTask(sampleTask()));
    }

    @Test
    @DisplayName("D115: saveStatus 的 ? 占位符数必须等于绑定实参数")
    void saveStatusPlaceholdersMatchBoundArgs() {
        TaskStatus s = new TaskStatus();
        s.setTaskId("t-1");
        s.setStatus(TaskStatus.Status.PENDING);
        s.setProgress(0);
        s.setStartTime(new Date());
        assertArity("saveStatus", () -> dao.saveStatus(s));
    }

    @Test
    @DisplayName("D115: recordExecution 的 ? 占位符数必须等于绑定实参数")
    void recordExecutionPlaceholdersMatchBoundArgs() {
        assertArity("recordExecution", () -> dao.recordExecution(
                "exec-1", "t-1", "SUCCEEDED", new Date(), new Date(), "ok", null, null, null));
    }

    @Test
    @DisplayName("D115: savePlan 的 ? 占位符数必须等于绑定实参数")
    void savePlanPlaceholdersMatchBoundArgs() {
        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId("t-1");
        assertArity("savePlan", () -> dao.savePlan(plan));
    }

    @Test
    @DisplayName("D115 反证：缺 ? 与不缺 ? 两种形态必须能被计数区分（门禁不是恒等断言）")
    void probeGateActuallyBites() {
        String missingOne = "INSERT INTO x (a,b,c) VALUES (?,?,NOW())";
        String complete = "INSERT INTO x (a,b,c) VALUES (?,?,?,?)";
        assertEquals(2, countPlaceholders(missingOne), "缺 ? 的形态应数出 2 个");
        assertEquals(4, countPlaceholders(complete), "补齐后应数出 4 个");
        assertTrue(countPlaceholders(missingOne) != countPlaceholders(complete),
                "门禁的计数必须能区分缺 ? 与不缺 ? 两种形态");
    }

    /** 记录型 JdbcTemplate：把 SQL 与实参存起来，永不真正连库。 */
    static class RecordingJdbcTemplate extends org.springframework.jdbc.core.JdbcTemplate {
        @Override
        public int update(String sql, Object... args) {
            SQLS.add(sql);
            ARG_SETS.add(args == null ? new Object[0] : args);
            return 1;
        }
    }
}
