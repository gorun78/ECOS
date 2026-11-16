package com.chinacreator.gzcm.buszhi.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时间线单写门禁（D94，2026-11-14 验证批，用户裁决：保留 controller 直写）。
 *
 * <p><b>缺陷形态（活体实测）</b>：同一对象的每个事件都落库两遍 ——
 * `ObjectController` 直写一次，`ObjectRuntimeEventListener` 收到同一事件后再写一次，
 * 两行 `created_at` 相差约 12ms、`summary`/`details` 逐字相同。</p>
 *
 * <p><b>裁决</b>：保留 controller 侧直写（语义更全：带真实 summary/actor，同步写入无延迟），
 * 事件监听器降级为纯观测（只记日志）。</p>
 *
 * <p><b>本门禁钉两件事</b>：① 监听器内不得再出现 `timelineRepo.record(`；
 * ② 直写侧仍然在位（否则删了双写变成零写，时间线全空 —— 那比双写更糟）。</p>
 */
@DisplayName("D94 — 时间线只能写一遍：事件监听器不得再落库，直写侧必须在位")
class TimelineSingleWriterGateTest {

    private static final Path REPO_ROOT = locateRepoRoot();

    private static Path locateRepoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && p != null; i++) {
            if (Files.exists(p.resolve("ecos_backend")) && Files.exists(p.resolve("docs"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("找不到仓根（要求含 ecos_backend/ 与 docs/）");
    }

    private static String read(String rel) throws IOException {
        Path f = REPO_ROOT.resolve(rel);
        assertTrue(Files.exists(f), "文件不存在: " + rel);
        return Files.readString(f, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)^\\s*//.*$", "");
    }

    @Test
    @DisplayName("监听器不得再写 timeline（重复写入的来源）")
    void listenerMustNotWriteTimeline() throws IOException {
        String src = read("ecos_backend/services/buszhi/impl/buszhi-impl/src/main/java/"
                + "com/chinacreator/gzcm/buszhi/workflow/ObjectRuntimeEventListener.java");

        assertFalse(src.contains("timelineRepo.record("),
                "监听器不得再调 timelineRepo.record —— 那正是每条事件写两遍的来源（D94）");
        assertFalse(src.contains("TimelineRepository timelineRepo"),
                "监听器不应再持有 TimelineRepository（持有即为重复写入的入口）");
        // 观测职责仍须保留：四个事件入口都在
        for (String ev : new String[]{"onObjectCreated", "onObjectUpdated", "onStatusChanged", "onActionExecuted"}) {
            assertTrue(src.contains(ev), "监听器应保留 " + ev + " 的观测入口");
            assertTrue(src.contains("@EventListener"), "必须仍是 Spring 事件监听器");
        }
    }

    @Test
    @DisplayName("直写侧必须在位（删双写不许变成零写）")
    void controllerSideWritesRemain() throws IOException {
        String objectCtl = read("ecos_backend/workspace/workspace-impl/src/main/java/"
                + "com/chinacreator/gzcm/workspace/controller/ObjectController.java");
        for (String type : new String[]{"ObjectCreated", "ObjectUpdated", "ObjectDeleted"}) {
            assertTrue(objectCtl.contains("\"" + type + "\""),
                    "ObjectController 必须仍直写 " + type + "（否则时间线变成零写）");
        }
        String actionCtl = read("ecos_backend/workspace/workspace-impl/src/main/java/"
                + "com/chinacreator/gzcm/workspace/controller/ObjectActionController.java");
        assertTrue(actionCtl.contains("\"ActionExecuted\""), "动作执行仍须直写 ActionExecuted");

        String sme = read("ecos_backend/runtime/common-api/src/main/java/"
                + "com/chinacreator/gzcm/common/engine/StateMachineEngine.java");
        assertTrue(sme.contains("\"StatusChanged\""), "状态机仍须直写 StatusChanged");
    }

    @Test
    @DisplayName("探针自检：判据能识别旧形态（监听器内含 record 调用）")
    void probeRecognizesDuplicateWriter() {
        String legacy = "timelineRepo.record(event.getObjectId(), event.getEntityCode(), "
                + "\"ObjectCreated\", \"对象创建\", \"system\", details);";
        assertTrue(legacy.contains("timelineRepo.record("),
                "旧写法的确命中禁则 —— 否则门禁恒绿");
        assertNotNull(REPO_ROOT);
    }
}
