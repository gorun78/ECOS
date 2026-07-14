# ECOS 踩坑记录

> 每次踩坑追加一条。格式: `日期 | 现象 | 根因 | 修复方法`

---

## 环境坑

| 日期 | 现象 | 根因 | 修复 |
|------|------|------|------|
| 2026-06-17 | `mvn` 编译出现 `\\wsl.localhost\` UNC路径双写 | Microsoft JDK wrapper → /mnt/c/.../java.exe，WSL把/mnt/c/解成UNC | 卸载Microsoft JDK，用Temurin JDK（纯Linux ELF），路径 `/home/guorongxiao/.local/jdk/jdk-17.0.19+10/bin/java` |
| 2026-06-17 | Hermes terminal中 `~` 和 `/home/guorongxiao/` 不是同一个目录 | Hermes重定向 `$HOME` 到profile目录 | 所有命令使用绝对路径 `/home/guorongxiao/`，不依赖 `~` |
| 2026-06-27 | `/mnt/d/workspace/` 下 find/ls 极慢（5秒超时），git status卡顿 | WSL挂载Windows文件系统的跨文件系统瓶颈（9P协议） | 所有开发操作（编译/git/find）只在 `/home/guorongxiao/` 下执行；Windows路径仅用于文档存储 |
| 2026-06-27 | Maven下载依赖极慢 | 国内访问Maven Central受限 | 配置 `~/.m2/settings.xml` 使用阿里云镜像 |

## 代码坑

| 日期 | 现象 | 根因 | 修复 |
|------|------|------|------|
| 2026-06-17 | 新增Controller端点全部返回403 | `SecurityConfig.java` 的 `permitAll()` 白名单没加新路径 | 在 `SecurityConfig.java` 的 `requestMatchers(...).permitAll()` 列表追加路径模式 |
| 2026-06-17 | 前端页面空白，console显示API 404 | 前端 `api.ts` 中 `BASE_URL` 硬编码旧路径 | 检查 `api.ts` 中所有 `BASE` 常量与实际后端路径对齐 |
| 2026-06-27 | `mvn spring-boot:run -pl sysman-boot` 报错找不到模块 | `-pl` 参数需要完整路径 `databridge-sysman/sysman-boot` | 使用 `mvn spring-boot:run -pl databridge-sysman/sysman-boot` |
| 2026-06-27 | `mvn install -am -pl sysman-boot` 传递依赖失败 | `-am` + `spring-boot:run` 不兼容，Maven生命周期冲突 | 分两步：先 `mvn install -DskipTests`，再 `mvn spring-boot:run -pl databridge-sysman/sysman-boot` |
| 2026-06-17 | CognitiveOperatingSystem.tsx 编译错误 `')' expected` | 三元表达式嵌套少一个 `)` | 仔细检查嵌套三元表达式的括号匹配 |

## 架构坑

| 日期 | 现象 | 根因 | 修复 |
|------|------|------|------|
| 2026-06-16 | ECOS多Agent团队产出3291行文档、0行产品代码 | Agent被允许产出"审视报告"，形成元工作正反馈循环 | 铁律：Agent唯一产出 = git commit hash。禁止产出解释性文档。PMO指令必须带curl验收命令 |
| 2026-06-17 | 两个databridge-v2副本同时在WSL和/mnt/d/，git状态混乱 | Agent在Windows路径和WSL路径各clone了一份 | 统一到 `/home/guorongxiao/databridge-v2`（WSL原生），删除 `/mnt/d/JavaProjects/databridge-v2` |
| 2026-06-27 | ECOS积累了60+个md文件但没有CEO可见的交付物 | 平台能力展示优先于用户场景闭环 | 扭转：用"CEO周一晨会"场景驱动开发，只做垂直切片，不做横向补齐 |

## Agent协作坑

| 日期 | 现象 | 根因 | 修复 |
|------|------|------|------|
| 2026-06-15 | goruncoder kanban worker心跳停止 | 任务过大触发auto-decomposer，任务被重新assign给gorunkol | 任务原子化——单文件/单职责，≤200行 |
| 2026-06-15 | DeepSeek API 429限流 | 5个goruncoder worker并发导致速率限制 | 一次入队1-2个任务，dispatcher串行调度 |
| 2026-06-27 | Agent生成代码后pre-check失败但不修复 | Agent认为"功能已完成"就不再管编译错误 | `pre-check.sh` 设为提交前置条件，`ecos-commit.sh` 自动跑 |
