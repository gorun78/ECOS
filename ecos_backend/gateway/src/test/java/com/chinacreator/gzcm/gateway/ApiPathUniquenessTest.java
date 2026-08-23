package com.chinacreator.gzcm.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API 路径不重复断言 — 扫描所有模块 Controller 的 @RequestMapping，
 * 断言无重复路径（防止 Spring Boot Ambiguous mapping 启动失败）。
 *
 * <p>PMO-D1 P1-3: 全局 API 路径唯一性守护。
 */
public class ApiPathUniquenessTest {

    private static JavaClasses controllerClasses;

    @BeforeAll
    static void setUp() {
        // 导入所有引擎和业务模块的 Controller 类
        controllerClasses = new ClassFileImporter()
            .importPackages(
                "com.chinacreator.gzcm.engine.data..",
                "com.chinacreator.gzcm.engine.ontology..",
                "com.chinacreator.gzcm.engine.kb..",
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.engine.ai..",
                "com.chinacreator.gzcm.engine.security..",
                "com.chinacreator.gzcm.sysman.."
            );
    }

    @Test
    void noDuplicateApiPaths() {
        Map<String, String> pathToController = new HashMap<>();
        Set<String> duplicates = new HashSet<>();

        controllerClasses.stream()
            .filter(cls -> cls.isAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                        || cls.isAnnotatedWith("org.springframework.stereotype.Controller"))
            .forEach(controller -> {
                String basePath = extractRequestMappingPath(controller);
                for (JavaMethod method : controller.getMethods()) {
                    String methodPath = extractMethodMappingPath(method);
                    if (methodPath == null) continue;

                    String fullPath = normalizePath(basePath + methodPath);
                    if (fullPath.isEmpty()) continue;

                    String mapping = controller.getSimpleName() + "." + method.getName() + " → " + fullPath;
                    if (pathToController.containsKey(fullPath)) {
                        duplicates.add(fullPath + " (已注册: " + pathToController.get(fullPath) + ", 冲突: " + mapping + ")");
                    } else {
                        pathToController.put(fullPath, mapping);
                    }
                }
            });

        assertTrue(duplicates.isEmpty(),
            "发现重复 API 路径（会导致 Spring Boot Ambiguous mapping 启动失败）:\n" +
            String.join("\n", duplicates));
    }

    /** 提取类级 @RequestMapping 的 path 值 */
    private static String extractRequestMappingPath(com.tngtech.archunit.core.domain.JavaClass cls) {
        try {
            JavaAnnotation<?> annotation = cls.getAnnotationOfType(
                "org.springframework.web.bind.annotation.RequestMapping");
            if (annotation == null) return "";
            Object value = annotation.get("value").orElse(null);
            if (value == null) value = annotation.get("path").orElse(null);
            return extractFirstString(value);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** 提取方法级 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping 的 path */
    private static String extractMethodMappingPath(JavaMethod method) {
        for (String mappingAnnotation : new String[]{
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping",
                "org.springframework.web.bind.annotation.RequestMapping"
        }) {
            try {
                JavaAnnotation<?> annotation = method.getAnnotationOfType(mappingAnnotation);
                if (annotation != null) {
                    Object value = annotation.get("value").orElse(null);
                    if (value == null) value = annotation.get("path").orElse(null);
                    return extractFirstString(value);
                }
            } catch (IllegalArgumentException e) {
                // Method doesn't have this annotation, try next
            }
        }
        return null;
    }

    /** 从 annotation 属性值中提取第一个字符串路径 */
    private static String extractFirstString(Object value) {
        if (value == null) return "";
        String str = value.toString();
        // ArchUnit 返回的是 '[...]' 格式的数组字符串，或单个字符串
        if (str.startsWith("[")) {
            str = str.substring(1);
            int comma = str.indexOf(',');
            if (comma > 0) str = str.substring(0, comma);
            if (str.endsWith("]")) str = str.substring(0, str.length() - 1);
        }
        return str.trim().replace("\"", "");
    }

    /** 标准化路径：去除末尾 /，确保以 / 开头 */
    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "";
        if (!path.startsWith("/")) path = "/" + path;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
