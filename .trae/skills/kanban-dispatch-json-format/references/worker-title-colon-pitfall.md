# Swarm Worker 标题冒号陷阱

## 症状

arch worker 反复 crash（exit code 1），事件日志仅有 `spawned → crashed → gave_up`，无 `commented` 事件，无任何输出。

## 根因

`--worker` 参数中的冒号被 swarm CLI 解析为 `profile:title:skill` 分隔符。

```bash
# ❌ 错误：第二个冒号被解析为 skill 名
--worker arch-1785224485752:设计:概要设计
# → profile=arch-1785224485752, title=设计, skill=概要设计
# → "Unknown skill(s): 概要设计" → crash

# ✅ 正确：只有一个冒号
--worker arch-1785224485752:概要设计
# → profile=arch-1785224485752, title=概要设计
```

## 修复

worker 标题中不要出现第二个冒号。如果需要描述性标题，用短横线或空格替代冒号：

```
--worker arch-1785224485752:概要设计          ✅
--worker arch-1785224485752:详细设计-模块D001  ✅
--worker arch-1785224485752:模块划分           ✅
--worker arch-1785224485752:设计_概要设计      ❌ (仍有风险)
```

## 检测

派发后如果 worker 30秒内从 ready → running → blocked，且无 `commented` 事件 → 大概率是标题冒号引起。立即检查 swarm 命令中的 `--worker` 格式。
