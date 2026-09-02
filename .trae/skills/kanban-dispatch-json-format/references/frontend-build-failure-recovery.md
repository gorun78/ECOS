# 前端 Worker 产出代码 npm build 失败恢复

## 症状

Worker 完成前端修改（如 kanban/index.vue、workspace/index.vue），mvn compile 通过但 `npm run build` 报错：

```
[vite:vue] [vue/compiler-sfc] Unexpected token (2364:0)
    at Parser.raise
    at Parser.unexpected
```

## 诊断流程

1. `npm run build` 看是否直接成功。如果 worker 报告"构建通过"但实际失败，说明 worker 的 scratch workspace 里构建成功（可能用了不同的 node_modules 或缓存），项目目录下失败。
2. `git diff --stat` 确认哪些文件被改动，评估改动范围。
3. 看 vite error 的行号定位到 workspace/index.vue。vue/compiler-sfc 报错通常意味着 `<script>` 块有语法错误（未闭合的括号、模板字符串中的非法字符、缺少 import 等）。

## 恢复方法

1. **逐个文件回退到 HEAD**：`git checkout HEAD -- frontend/src/views/project/workspace/index.vue frontend/src/views/project/kanban/index.vue frontend/src/api/kanban.js`
2. **重新构建验证**：`npm run build` 确认干净版本通过
3. **最小化重新派发**：只改真正需要改的文件，不混合多个 feature 在一次 swarm 中

## 根因分析

- Worker 在 scratch workspace 中写的代码可能引用该 workspace 独有的依赖或配置
- Vue SFC compiler 对 `<script>` 块内的语法极其敏感，一个未闭合的括号就会导致整行报错
- Worker 没有在项目目录实际运行 `npm run build` 验证
- 多个 features（board 选择器 + 删除按钮 + 日志弹窗）混在一次 swarm 中，改动范围过大，失败后难以定位
