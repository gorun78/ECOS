package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-21-4（X-75/X-76） —— 死 AI 界面整批删除的可判化守卫。
 *
 * <p><b>断言（源码扫描，无 DB / 无 Spring / 无 vitest）</b>：
 * 以下 12 个 AI 面死遗留文件已由本轮<b>物理删除</b>（不是标 deprecated），
 * 判据 = <b>importer 集合为空 且 无路由挂载</b>（个别文件 Javadoc 里明确写"已复验为空"）。
 * 本扫描再次验证它们已从 {@code ecos_frontend/src} 移除；若任一条目被回滚复活，本测试 FAIL。
 *
 * <p>豁免规则：{@code pages/aiworkbench/index.tsx} 属主入口（{@code main.tsx#L54
 * lazy(() => import('./pages/aiworkbench/index.tsx'))} 挂 /ai-workbench 路由），
 * 不在删除清单 —— 不属于 X-75/X-76 判死范围。
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test
 * -Dtest='FrontendAiDeadCodeTest#noUnimportedAiComponentRemains'
 * -Dsurefire.failIfNoSpecifiedTests=false}
 */
class FrontendAiDeadCodeTest {

    /** 本轮 (F06-21-4) 已删除的 AI 面死文件清单（源路径，相对 {@code ecos_frontend/src/}）。 */
    static final String[] DELETED_DEAD_FILES = {
            "pages/aiworkbench/useAgentStudio.ts",
            "pages/aiworkbench/AiGuardrailsView.tsx",
            "components/aiworkbench/agent-studio/AgentManager.tsx",
            "components/aiworkbench/agent-studio/AgentMarket.tsx",
            "components/aiworkbench/agent-studio/AgentMonitor.tsx",
            "components/aiworkbench/agent-studio/AgentDetail.tsx",
            "components/aiworkbench/agent-studio/AgentList.tsx",
            "components/aiworkbench/chatbot/ChatHistory.tsx",
            "components/aiworkbench/guardrails/GuardrailPolicyCompilerTab.tsx",
            "components/aiworkbench/guardrails/GuardrailSimulatorTab.tsx",
            "components/aiworkbench/guardrails/GuardrailWorkflowTab.tsx",
            "components/aiworkbench/ontology/ActionTypeTab.tsx",
    };

    @Test
    @DisplayName("F06-21-4/X-75/X-76：12 件 AI 面死组件已从前端物理删除")
    void noUnimportedAiComponentRemains() throws IOException {
        Path be = backendRoot();
        Path fe = be.toAbsolutePath().getParent();
        Path src = fe.resolve("ecos_frontend/src");
        assertTrue(Files.isDirectory(src), "前端 src/ 目录缺失: " + src);

        List<String> violations = new ArrayList<>();
        for (String rel : DELETED_DEAD_FILES) {
            Path p = src.resolve(rel);
            if (Files.exists(p)) {
                violations.add(String.format("revived: ecos_frontend/src/%s (F06-21-4 已判死，本轮应删除)", rel));
            }
        }
        assertTrue(violations.isEmpty(),
                "AI 面死组件已回滚复活（X-75/X-76 判定为 importer 集合为空 + 无路由挂载：应整批删除而非标 deprecated）:\n"
                        + String.join("\n", violations));
    }

    private static Path backendRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p.resolve("ecos_backend");
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new IllegalStateException("cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
    }
}
