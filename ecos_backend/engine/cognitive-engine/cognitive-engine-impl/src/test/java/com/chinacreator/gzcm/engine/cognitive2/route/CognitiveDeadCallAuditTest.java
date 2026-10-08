package com.chinacreator.gzcm.engine.cognitive2.route;

import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveBeliefController;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineHealthController;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineOpenHealthController;
import com.chinacreator.gzcm.engine.cognitive2.controller.DiagnosisController;
import com.chinacreator.gzcm.engine.cognitive2.controller.WorldModelController;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.ScenarioCognitionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-08 死调用审计（X-49 五组"前端在调、后端必 404"禁存续）：
 * 读前端 {@code cognitiveClient.ts} 的调用路径集合 × 后端 cognitive 契约面 mapping 集合做差集，
 * 差集必须为空（变量段以通配匹配）。
 */
class CognitiveDeadCallAuditTest {

    private static final Path FRONTEND_CLIENT = Path.of("..", "..", "..", "..").toAbsolutePath().normalize()
            .resolve("ecos_frontend/src/services/cognitiveClient.ts");

    /** 后端契约面：类级前缀 + 方法级路径展开（注解编译期常量，运行时真实值）。 */
    private static final Class<?>[] BACKEND_CONTROLLERS = {
            ScenarioCognitionController.class,
            CognitiveEngineOpenHealthController.class,
            CognitiveEngineHealthController.class,
            CognitiveBeliefController.class,
            DiagnosisController.class,
            WorldModelController.class,
    };

    @Test
    @DisplayName("前端 cognitiveClient.ts 每个调用路径都有后端 mapping（差集为空）")
    void everyFrontendCognitivePathHasBackendMapping() throws IOException {
        assertTrue(Files.exists(FRONTEND_CLIENT),
                "cognitiveClient.ts 单源缺失（F05-17 B 章）：" + FRONTEND_CLIENT);
        // 剥离 JSDoc 块注释：文档里的示例路径（/api/v1/cognitive* 等）不是调用点
        String ts = Files.readString(FRONTEND_CLIENT).replaceAll("(?s)/\\*.*?\\*/", "");

        Set<String> frontendPaths = new HashSet<>();
        Matcher m = Pattern.compile("[`\"']/((?:api/v\\d+)/[^`\"']*?)(?=[`\"'?])").matcher(ts);
        while (m.find()) {
            String raw = m.group(1);
            // 截去 querystring 已由 lookahead 控制；模板变量段 ${...} 归一为 {p}
            frontendPaths.add("/" + normalize(raw));
        }
        // 另收 template literal 中 `...${id}...` 后还有尾缀的情况
        Matcher m2 = Pattern.compile("[`\"'](/api/v\\d/[A-Za-z0-9/_{}$.\\-]+)").matcher(ts);
        while (m2.find()) {
            String raw = m2.group(1);
            int q = raw.indexOf('?');
            if (q >= 0) raw = raw.substring(0, q);
            frontendPaths.add("/" + normalize(raw.replace("${", "{").replaceAll("\\{[^}]*\\}", "{p}")));
        }

        List<String> backend = backendMappings();
        List<String> dead = new ArrayList<>();
        for (String f : frontendPaths) {
            if (f.contains("localhost") || f.contains("http")) continue;
            if (!f.startsWith("/api/v")) continue;
            if (!matchesAny(backend, f)) dead.add(f);
        }
        assertTrue(dead.isEmpty(),
                "前端认知调用路径无后端 mapping（静默死链，X-49）：\n" + String.join("\n", dead)
                        + "\n后端 mapping 集：\n" + String.join("\n", backend));
    }

    private static String normalize(String p) {
        return p.replace("${", "{").replaceAll("\\{[^}]*\\}", "{p}")
                .replaceAll("%s", "{p}");
    }

    private static List<String> backendMappings() {
        List<String> out = new ArrayList<>();
        for (Class<?> c : BACKEND_CONTROLLERS) {
            String classPrefix = "";
            RequestMapping rm = c.getAnnotation(RequestMapping.class);
            if (rm != null && rm.value().length > 0) classPrefix = rm.value()[0];
            for (Method m : c.getDeclaredMethods()) {
                for (String v : allMethodPaths(m)) {
                    out.add(classPrefix + v);
                }
            }
        }
        return out;
    }

    private static List<String> allMethodPaths(Method m) {
        List<String> vs = new ArrayList<>();
        collect(GetMapping.class, m, vs);
        collect(PostMapping.class, m, vs);
        collect(PutMapping.class, m, vs);
        collect(DeleteMapping.class, m, vs);
        collect(PatchMapping.class, m, vs);
        if (vs.isEmpty()) {
            RequestMapping rm = m.getAnnotation(RequestMapping.class);
            if (rm != null) vs.addAll(List.of(rm.value()));
        }
        return vs;
    }

    private static void collect(Class<? extends Annotation> type, Method m, List<String> sink) {
        Annotation a = m.getAnnotation(type);
        if (a == null) return;
        try {
            String[] vals = (String[]) type.getMethod("value").invoke(a);
            if (vals.length == 0) sink.add("");
            else sink.addAll(List.of(vals));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 后端 mapping 段级匹配：后端 {x} 占位与前端 {p} 互为通配；前端 *_BASE 前缀常量按前缀判定。 */
    private static boolean matchesAny(List<String> backend, String frontend) {
        String f = frontend.replaceAll("\\{[^}]*\\}", "{p}");
        while (f.endsWith("/")) f = f.substring(0, f.length() - 1);
        for (String b : backend) {
            String bb = b.replaceAll("\\{[^}]*\\}", "{p}");
            if (bb.equals(f)) return true;
            if (bb.startsWith(f + "/")) return true; // 前缀常量（调用点由模板拼接补全）
        }
        return false;
    }
}
