# ECOS 知识库入口

> 自动生成 + 手动维护。自动部分跑 `python3 scripts/scan_all.py` 刷新。

## 快速查询

| 你想知道 | 查什么 |
|---------|--------|
| 某个API端点 | `jq '.endpoints[] | select(.path | contains("xxx"))' api-index.json` |
| 某张表的列 | `jq '.tables[] | select(.name=="ecos_biz_xxx")' data-model.json` |
| 哪个模块负责什么 | `cat module-map.md` |
| 为什么这么设计 | `cat architecture.md` |
| 别踩这个坑 | `cat pitfalls.md` |

## 文件清单

| 文件 | 生成方式 | 用途 |
|------|---------|------|
| `api-index.json` | `scan_apis.py` 自动 | 所有REST端点索引 |
| `data-model.json` | `scan_schema.py` 自动 | 所有数据表结构 |
| `module-map.md` | `scan_modules.py` 自动 | Maven模块依赖图 |
| `architecture.md` | 手动 | 架构决策记录 |
| `pitfalls.md` | 手动 | 已知陷阱 |
| `AGENTS.md` | 本文件 | 入口 |

## 刷新命令

```bash
cd /home/guorongxiao/ecos-kb
python3 scripts/scan_all.py
```
