package com.chinacreator.gzcm.sysman.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D103 门禁：WorldModelGraph 的写面必须落库，不得再用进程内 Map 冒充存储。
 *
 * <p><b>缺陷形态（2026-11-15 活体实证）</b>：五个写端点全部操作
 * {@code ConcurrentHashMap<String, GoalEntity> goalStore} / {@code linkStore}，
 * 而读面查 PostgreSQL。{@code POST /goals} 返回 {@code success:true} 加一个 UUID，
 * 但库里没有任何对应行（{@code SELECT count(*)} 不变），重启即丢；且表主键是
 * {@code bigint}，内存里的 UUID 与库里的数值 ID 是两套形态，父子校验必然错乱
 * （实测「父目标不存在: 1」而列表里就返回着 {@code id:1}）。</p>
 *
 * <p><b>为什么用源码文本断言</b>：这条缺陷的本质是「装配面选错了存储」，单测
 * mock 掉 DAO 就看不见（假绿族：{@code ObjectProvider}/{@code ConcurrentHashMap}
 * 兜底让单测通过、活体失真）。因此直接把「控制器里不许再出现内存写存储」钉成源码级门禁。</p>
 */
@DisplayName("D103：WorldModelGraph 写面必须落库（禁内存 Map 冒充存储）")
class WorldModelGraphNoMemoryStoreGateTest {

    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/chinacreator/gzcm/sysman/controller/WorldModelGraphController.java");

    private static String source() throws Exception {
        Path p = Files.exists(CONTROLLER) ? CONTROLLER
                : Path.of("ecos_backend/services/sysman/impl/sysman-impl").resolve(CONTROLLER);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("不得声明 goalStore / linkStore 内存写存储")
    void noInMemoryWriteStores() throws Exception {
        String src = source();
        assertFalse(src.contains("ConcurrentHashMap<String, GoalEntity> goalStore"),
                "goalStore 必须删除，写面改走 WorldModelGraphWriteDao");
        assertFalse(src.contains("ConcurrentHashMap<String, CausalLinkEntity> linkStore"),
                "linkStore 必须删除，写面改走 WorldModelGraphWriteDao");
        assertFalse(src.contains("initFallbackData"),
                "不得再用假数据（root-goal/child-goal）兜底冒充真实目标");
    }

    @Test
    @DisplayName("写端点必须经由 writeDao（正向断言，防止写面被改成别的东西）")
    void writeEndpointsUseWriteDao() throws Exception {
        String src = source();
        assertTrue(src.contains("writeDao.insertGoal"), "建目标必须落库");
        assertTrue(src.contains("writeDao.updateGoal"), "改目标必须落库");
        assertTrue(src.contains("writeDao.deleteGoal"), "删目标必须落库");
        assertTrue(src.contains("writeDao.insertLink"), "建因果链必须落库");
        assertTrue(src.contains("writeDao.deleteLink"), "删因果链必须落库");
        assertTrue(src.contains("writeDao.goalExists"), "父子/端点校验必须查库，不能查内存");
    }

    @Test
    @DisplayName("反向探针自检：旧实现的特征串确实能被上面的断言识别")
    void probeRecognizesLegacyShape() {
        String legacy = "private final ConcurrentHashMap<String, GoalEntity> goalStore = new ConcurrentHashMap<>();";
        assertTrue(legacy.contains("ConcurrentHashMap<String, GoalEntity> goalStore"),
                "旧形态串必须命中禁项模式，否则本门禁拦不住回归");
        String legacyWrite = "goalStore.put(id, goal);";
        assertFalse(legacyWrite.contains("writeDao."),
                "旧写面不含 writeDao 前缀 ⇒ 正向断言在回退后必然转红");
    }
}
