# 多波 Swarm 派发模式（FEAT-0015 / FEAT-0016 实战）

## 核心问题：Swarm 工作空间隔离

`hermes kanban swarm` 每波创建独立 scratch workspace，不是用户项目目录。

```
Wave 1 (Arch):      /home/hermes/prj/AI-Native-Factory/48/  ← 独立 workspace
Wave 2 (BE+FE):     /home/hermes/prj/AI-Native-Factory/48/  ← 可能复用同一 workspace
Wave 3 (Reviewer):  ~/.hermes/kanban/boards/.../t_xxx/      ← 又一个独立 workspace
```

**后果**：Reviewer 在自己的 workspace 里找不到 Wave 1/2 产出的代码，导致 protocol violation（agent 退出 exit_code=0 但没调用 kanban_complete）。

## L2 标准多波执行流程

```
Wave 1:  hermes kanban swarm --worker fullstack:[Arch] --verifier pm --synthesizer pm
           -> 追踪 worker->verifier->synthesizer（全部 done/archived）
           -> 产出：docs/arch/<spec>.md

Wave 2:  hermes kanban swarm --worker fullstack:[Backend] --worker fullstack:[Frontend] ...
           -> 两个 worker 并行，等全部 done
           -> 产出：backend/src/... + frontend/src/...

Wave 3:  hermes kanban swarm --worker qa:[Reviewer] ...
           -> 必须在 prompt 中给出 Wave 1/2 产出的文件路径！
           -> 第一次失败是常态（找不到源码），重派给出路径后通常成功
```

## Reviewer 重派模板

```
"所有代码在 /home/hermes/prj/AI-Native-Factory/48/ 目录下。
请先读取以下文件进行审查:
- /home/hermes/prj/AI-Native-Factory/48/docs/arch/<spec>.md
- /home/hermes/prj/AI-Native-Factory/48/backend/src/main/java/.../<File1>.java
- /home/hermes/prj/AI-Native-Factory/48/backend/src/main/java/.../<File2>.java
..."
```

## 追踪脚本的 archived 处理

任务全生命周期完成后被 GC 清理，`hermes kanban show` 返回空字符串：

```python
def check(tid):
    try:
        d = json.loads(r.stdout)
        return d.get("task",{}).get("status","unknown")
    except (json.JSONDecodeError, AttributeError):
        return "archived"  # GC 已清理 = 已完成

# 追踪时 archived == done
if ws in ("done", "archived") and vs in ("done", "archived") and ss in ("done", "archived"):
    print("All done!")
```

## 收尾清单

1. 全部 Wave 完成后，汇总所有产出物位置
2. 回写 kanban.json（t_pre_xxx -> 真实 t_xxxx，dispatch_mode=actual）
3. 回写 features_v2.json（status 字段）
4. `hermes kanban gc` 清理
5. 告知用户代码在哪个 workspace，是否需要迁移

## FEAT-0015 案例

- Wave 1 Arch: t_f697e19e -> done，ARCH_SPEC 731行
- Wave 2 BE+FE: t_0fd187f3 + t_6ed03ed8 -> done，21+7文件
- Wave 3 Reviewer: t_64e93548 -> blocked（源码不在项目3）

## FEAT-0016 案例

- Wave 1 Arch: t_8a2b7bc5 -> archived（追踪未及时捕获，但产物已生成）
- Wave 2 BE+FE: t_af0da6aa + t_e644cae2 -> archived
- Wave 3 Reviewer: t_fb73b182 -> blockedx2（protocol violation，找不到源文件）
- Wave 3b 重派: t_c86bc611 -> archived（在 prompt 中给出显式路径后成功）
