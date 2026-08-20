package com.chinacreator.gzcm.engine.data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Pipeline 验证器（PMO-36 T3）。
 *
 * <p>三验：①结构（节点/边非空、nodeId 唯一）②依赖（dependsOn 无环、引用节点存在）③性能（节点数上限 100）</p>
 */
@Component
public class PipelineValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_NODES = 100;

    /**
     * 验证管线定义。
     *
     * @param steps 步骤列表（每项含 id, node_type, config_json, depends_on）
     * @return 错误列表（空列表 = 验证通过）
     */
    public List<String> validate(List<Map<String, Object>> steps) {
        List<String> errors = new ArrayList<>();

        // ① 结构验证
        if (steps == null || steps.isEmpty()) {
            errors.add("Pipeline has no steps");
            return errors;
        }

        Set<String> nodeIds = new HashSet<>();
        Set<String> allDependsOn = new HashSet<>();

        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String stepId = (String) step.get("id");
            if (stepId == null || stepId.isEmpty()) {
                errors.add("Step at index " + i + " has null/empty id");
                continue;
            }
            if (!nodeIds.add(stepId)) {
                errors.add("Duplicate step id: " + stepId);
            }

            // 收集依赖（depends_on 可能是 String、List 或 PGobject/jsonb）
            Object depends = step.get("depends_on");
            if (depends instanceof String) {
                String depStr = ((String) depends).trim();
                // JSON 数组格式: ["step1","step2"]
                if (depStr.startsWith("[")) {
                    depStr = depStr.replaceAll("[\\[\\]\"]", "");
                    if (!depStr.isEmpty()) {
                        for (String d : depStr.split(",")) {
                            String trimmed = d.trim();
                            if (!trimmed.isEmpty()) allDependsOn.add(trimmed);
                        }
                    }
                } else if (!depStr.isEmpty()) {
                    Collections.addAll(allDependsOn, depStr.split(","));
                }
            } else if (depends instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> depList = (List<String>) depends;
                allDependsOn.addAll(depList);
            } else if (depends != null) {
                String depStr = depends.toString();
                if (depStr.startsWith("[")) {
                    depStr = depStr.replaceAll("[\\[\\]\"]", "");
                    if (!depStr.isEmpty()) {
                        for (String d : depStr.split(",")) {
                            allDependsOn.add(d.trim());
                        }
                    }
                }
            }
        }

        // ② 依赖验证：引用的节点必须存在
        for (String dep : allDependsOn) {
            String trimmed = dep.trim();
            if (!trimmed.isEmpty() && !nodeIds.contains(trimmed)) {
                errors.add("Step depends on non-existent step: " + trimmed);
            }
        }

        // ③ 循环检测（Kahn 拓扑）
        if (hasCycle(steps, nodeIds)) {
            errors.add("Pipeline has a circular dependency (cycle detected)");
        }

        // ④ 性能验证
        if (steps.size() > MAX_NODES) {
            errors.add("Pipeline exceeds max steps: " + steps.size() + " > " + MAX_NODES);
        }

        return errors;
    }

    /**
     * Kahn 拓扑排序检测环。
     */
    private boolean hasCycle(List<Map<String, Object>> steps, Set<String> nodeIds) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (String id : nodeIds) {
            inDegree.put(id, 0);
            adj.put(id, new ArrayList<>());
        }

        for (Map<String, Object> step : steps) {
            String stepId = (String) step.get("id");
            if (stepId == null) continue;

            Object depends = step.get("depends_on");
            List<String> depList = new ArrayList<>();
            if (depends instanceof String) {
                String depStr = ((String) depends).trim();
                if (depStr.startsWith("[")) {
                    depStr = depStr.replaceAll("[\\[\\]\"]", "");
                    if (!depStr.isEmpty()) {
                        for (String d : depStr.split(",")) {
                            String trimmed = d.trim();
                            if (!trimmed.isEmpty()) depList.add(trimmed);
                        }
                    }
                } else if (!depStr.isEmpty()) {
                    Collections.addAll(depList, depStr.split(","));
                }
            } else if (depends instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> rawList = (List<String>) depends;
                depList.addAll(rawList);
            } else if (depends != null) {
                String depStr = depends.toString();
                if (depStr.startsWith("[")) {
                    depStr = depStr.replaceAll("[\\[\\]\"]", "");
                    if (!depStr.isEmpty()) {
                        for (String d : depStr.split(",")) {
                            depList.add(d.trim());
                        }
                    }
                }
            }

            for (String dep : depList) {
                String trimmed = dep.trim();
                if (nodeIds.contains(trimmed)) {
                    adj.get(trimmed).add(stepId);
                    inDegree.merge(stepId, 1, Integer::sum);
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            processed++;
            for (String next : adj.getOrDefault(current, Collections.emptyList())) {
                int newDeg = inDegree.merge(next, -1, Integer::sum);
                if (newDeg == 0) queue.add(next);
            }
        }

        return processed < nodeIds.size();
    }
}
