# PMO-A1-4: 清理 workspace 死代码 + 重建无循环依赖

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-BE
> **前置**: A1-3（移除 dccheng 模块）已完成。

## § 背景

A1-3 移除 dccheng 时发现 workspace-impl 有问题：
1. **pom 仍依赖已删除的 dccheng-impl**（已临时改为 ontology-engine-impl，但引出循环）
2. **sysman-impl/pom 有死 workspace-impl 依赖**（源码无 import，删除后破循环）
3. **A1-3 后 workspace-impl 编译成功**，但需正式重建依赖关系

## § 分析

```
当前依赖关系（编译成功）:
workspace-impl → ontology-engine-impl → sysman-impl → (无)
                                     ↘ (sysman 不再依赖 workspace)
```

## § 任务

| Task | 内容 | 验收 |
|:----:|------|------|
| T1 | 验证 workspace-impl 的 consumer 列表（gateway/common-api/sysman-boot/object-service） | 4 个 consumer 确认 |
| T2 | 确认无循环依赖（BUILD SUCCESS） | mvn install 0 ERROR |

## § 执行步骤

### T1: 验证 workspace-impl 消费者

```bash
# 统计 workspace-impl 的消费者（pom 依赖声明）
grep -rln 'workspace-impl' --include=pom.xml | grep -v '/workspace/' | grep -v target

# 验证各消费者确实 import workspace 类
grep -rln 'import com.chinacreator.gzcm.workspace' --include='*.java' \
  $(grep -rl 'workspace-impl' --include=pom.xml | xargs dirname) \
  | grep -v '/workspace/' | grep -v target
```

### T2: 全量构建 + 循环检测

```bash
unset HOME && cd /home/guorongxiao/ECOS/ecos_backend && \
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 && \
export PATH=$JAVA_HOME/bin:$PATH && \
mvn install -DskipTests -Dmaven.test.skip=true -q && echo "BUILD SUCCESS"
```

## § 禁止清单

1. ❌ 不删除 workspace-impl 本身（它有真实消费者）
2. ❌ 不引入新循环依赖
3. ❌ 不删除 sysman-boot 的 workspace 依赖（sysman-boot 有真实 import）
