package com.chinacreator.gzcm.workspace;

import com.chinacreator.gzcm.workspace.repository.QueryHistoryEntry;
import com.chinacreator.gzcm.workspace.repository.QueryHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卷07 F07-21（W185 / C167）—— 查询历史出堆、分页、可跨重启。
 *
 * <p>不需要真库：用一个 test 内的 {@link QueryHistoryRepository} 替身，
 * 判的是<b>责任边界</b>——服务自身是否还偷偷持有集合。
 * X-55 的病灶（进程内 {@code CopyOnWriteArrayList}）若回归，
 * 下面的"重启后历史仍在"立刻失败。</p>
 */
class QueryHistoryPersistenceTest {

    /** 记录调用轨迹的内存替身 —— 只当"外置存储"用，服务不得依赖它的行为。 */
    private static final class FakeRepo implements QueryHistoryRepository {
        final List<QueryHistoryEntry> rows = new ArrayList<>();
        final List<int[]> pageCalls = new ArrayList<>();

        @Override
        public void save(QueryHistoryEntry entry) {
            rows.add(entry);
        }

        @Override
        public List<QueryHistoryEntry> findPage(String subjectId, int limit, int offset) {
            pageCalls.add(new int[]{limit, offset});
            return rows.stream()
                    .filter(r -> subjectId == null || subjectId.equals(r.subjectId()))
                    .sorted(Comparator.comparing(QueryHistoryEntry::createdAt).reversed())
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<QueryHistoryEntry> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public boolean deleteById(String id) {
            return rows.removeIf(r -> r.id().equals(id));
        }

        @Override
        public int clearBySubject(String subjectId) {
            int before = rows.size();
            rows.removeIf(r -> subjectId == null || subjectId.equals(r.subjectId()));
            return before - rows.size();
        }
    }

    private static QueryHistoryService service(QueryHistoryRepository repo) {
        return new QueryHistoryService(providerOf(repo));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<QueryHistoryRepository> providerOf(QueryHistoryRepository repo) {
        ObjectProvider<QueryHistoryRepository> p = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(p.getIfAvailable()).thenReturn(repo);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<QueryHistoryRepository> emptyProvider() {
        ObjectProvider<QueryHistoryRepository> p = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(p.getIfAvailable()).thenReturn(null);
        return p;
    }

    @Test
    @DisplayName("历史在存储侧，服务实例换掉（= 进程重启）后记录仍在")
    void historySurvivesContextRestart() {
        FakeRepo repo = new FakeRepo();
        QueryHistoryService first = service(repo);
        first.save("安全指标近 7 天趋势", "{\"metric\":\"安全指标\"}", 3);
        first.save("孤岛场景清单", "{\"filter\":\"island\"}", 1);
        assertEquals(2, repo.rows.size(), "写入必须落到 repository");

        // 新实例 = 模拟容器重启；服务自身不持集合，所以数据只能来自外置存储
        FakeRepo durable = new FakeRepo();
        durable.rows.addAll(repo.rows);
        QueryHistoryService afterRestart = service(durable);
        List<QueryRecord> history = afterRestart.getHistory(10);
        assertEquals(2, history.size(),
                "重启后历史丢失 ⇒ QueryHistoryService 又在进程内自存状态（X-55 回潮）");
        assertEquals(3, history.get(0).getResultCount(), "新→旧排序：最近一条是 resultCount=3");
    }

    @Test
    @DisplayName("读取走 LIMIT/OFFSET 分页，服务侧无堆内有界列表")
    void historyIsPagedNotBoundedInMemory() throws IOException {
        FakeRepo repo = new FakeRepo();
        QueryHistoryService svc = service(repo);
        for (int i = 0; i < 5; i++) {
            svc.save("q" + i, "{}", i);
        }
        svc.getHistory(2);
        svc.getHistory(3, 2);

        assertEquals(2, repo.pageCalls.get(0)[0], "首页 limit 必须下推为 2");
        assertEquals(0, repo.pageCalls.get(0)[1], "首页 offset 必须是 0");
        assertEquals(3, repo.pageCalls.get(1)[0], "第二页 limit=3");
        assertEquals(2, repo.pageCalls.get(1)[1], "第二页 offset=2 —— 分页参数下推到存储");

        String src = queryHistoryServiceSource();
        assertFalse(src.contains("CopyOnWriteArrayList"),
                "X-55：QueryHistoryService 禁止再出现 CopyOnWriteArrayList");
        assertFalse(src.contains("MAX_HISTORY"),
                "X-55：截断式伪有界（MAX_HISTORY）必须删除");
        assertFalse(src.contains("new ArrayList<QueryRecord>"),
                "服务不得在堆内维护历史集合");

        String jdbc = read("repository/JdbcQueryHistoryRepository.java");
        assertTrue(jdbc.contains("LIMIT ?") && jdbc.contains("OFFSET ?"),
                "持久化实现的读取必须是 SQL 分页，而不是取全量再截断");
        assertTrue(jdbc.contains("INSERT INTO ecos_query_history"),
                "落库表必须显式命名（禁 currentSchema 兜底拼写不确定的表）");
    }

    @Test
    @DisplayName("repository 缺席时返回空分页，禁退化成堆内存列表")
    void absentRepositoryYieldsEmptyNotHeapFallback() {
        QueryHistoryService svc = new QueryHistoryService(emptyProvider());
        svc.save("q", "{}", 1);
        assertTrue(svc.getHistory(20).isEmpty(),
                "无持久化面时必须返回空——用堆内存兜底就是 X-55 的原病");
        assertFalse(svc.delete("whatever"));
    }

    private static String queryHistoryServiceSource() throws IOException {
        return read("QueryHistoryService.java");
    }

    private static String read(String relativeToWorkspacePkg) throws IOException {
        Path base = Path.of("src/main/java/com/chinacreator/gzcm/workspace");
        if (!Files.isDirectory(base)) {
            base = Path.of("workspace-impl/src/main/java/com/chinacreator/gzcm/workspace");
        }
        return Files.readString(base.resolve(relativeToWorkspacePkg), StandardCharsets.UTF_8);
    }

    /** 铁律 §2.5：JdbcTemplate 只准出现在 repository 层，本类的判据源就是这条。 */
    @Test
    @DisplayName("JdbcTemplate 依赖不出现在 service/controller 层源文件")
    void jdbcTemplateStaysBelowRepository() throws IOException {
        String svc = queryHistoryServiceSource();
        assertFalse(svc.contains("JdbcTemplate"), "服务层禁直接依赖 JdbcTemplate");
    }
}
