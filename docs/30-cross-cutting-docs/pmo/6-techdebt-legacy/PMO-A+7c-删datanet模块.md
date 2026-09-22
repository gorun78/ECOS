# PMO-A+7c: 删 datanet 模块

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置（硬门禁）**: A+7a（断反向依赖）+ A+7b（迁数据源访问簇/connector/pipeline）已完成，engine.data 不再 import datanet

## §背景

A+7a/A+7b 迁走了 datanet 的 model/dto/repository/connector/pipeline，删掉了重复接口。datanet 剩余物 = 废弃 Controller(5) + 死 impl(4) + 0 引用杂项(4) = 13 文件 + 空壳 pom。

## §剩余清单（13 文件，全删）

| 类别 | 文件 | 依据 |
|------|------|------|
| controller 5 | CategoryController/DatanetHealthController/MetadataController/CatalogController/DataSourceController | gateway excludeFilter 已排除（data-engine 接管），死副本 |
| impl 4 | DataSourceServiceImpl/CatalogServiceImpl/MetadataServiceImpl/CategoryServiceImpl | 0 外部引用 |
| 杂项 4 | PgAnalyticsService/DorisAnalyticsService/MqttBrokerConfig/DatanetApplication | 0 引用 |

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | `pom.xml` 移除 4 处 `<module>datanet</module>`（主 modules + standard/enterprise/ultimate）+ `gateway/pom.xml` 移除 datanet 依赖 | `mvn validate` 通过 |
| T2 | `GatewayApplication.java` 清理 datanet 相关：excludeFilter 5 处 + `@ComponentScan` basePackage `"com.chinacreator.gzcm.datanet"` + `@MapperScan` 的 `datanet.repository` | gateway 编译通过 |
| T3 | 全量 `mvn install -DskipTests` + 三版本 profile validate（datanet 退出构建，目录软删除） | BUILD SUCCESS |
| T4 | 全量回归 + 肖总确认后 `git rm -r datanet/` 物理删除 | 目录不存在 |

## §禁止清单

1. ❌ 不迁任何 datanet 代码（A+7a/b 已迁完，本指令纯删除）
2. ❌ 不碰 gateway excludeFilter 里非 datanet 的引用
3. ❌ 不用 `rm -rf` 手动删（用 `git rm -r` 保历史）
4. ❌ 不跳过三版本 profile 验证

## §验证门禁

```bash
# V1: 主 pom 无 datanet
grep -n "datanet" /home/guorongxiao/ECOS/ecos_backend/pom.xml
# 期望: 0 匹配

# V2: gateway 无 datanet 引用
grep -rn "datanet" /home/guorongxiao/ECOS/ecos_backend/gateway/src --include="*.java"
# 期望: 0 匹配

# V3: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V4: 三版本 profile
mvn validate -Pstandard -q && mvn validate -Penterprise -q && mvn validate -Pultimate -q

# V5: 活跃模块无 datanet 引用
grep -rn "com.chinacreator.gzcm.datanet" --include="*.java" . | grep -v target | grep -v "/datanet/"
# 期望: 0 匹配
```

## §工时

0.5 天（pom 4 处 + gateway 清理 + 删目录 + 编译验证）。

## §风险

- **gateway 清理是最大坑**：excludeFilter 5 处 datanet `.class` + basePackage + @MapperScan 的 `datanet.repository` 漏一处编译报"找不到符号"。
- **datanet-boot 的 DatanetApplication**：datanet 有 boot 模块，若 gateway 或别处引用了 datanet-boot，删前 grep 确认。
- **`.m2` 旧 JAR**：删 module 后全量 install，若报 datanet 旧类，删 `~/.m2/repository/com/chinacreator/gzcm/datanet*`。
- **软删除分两步**：T1-T3 移除 module（目录保留可回退），T4 git rm 物理删除需肖总确认（参照 dccheng 删法）。
