# ECOS 数据工作台 — 配置体系 PRD

> 版本: v1.0 | 日期: 2026-07-11 | 作者: ECOS-PMO

---

## 一、设计原则

1. **复用 `sys_config` 表**，新增 `config_group` 字段分组（不新建表）
2. **前端侧边栏底部**独立配置页（数据工作台左侧功能栏下方）
3. **所有配置有默认值**，不配置也能正常运行
4. **配置生效**：运行时注入到对应 Service，支持热刷新
5. **删除系统管理中的重复配置项**

---

## 二、配置项全量梳理

### 2.1 执行引擎配置 (`data-engine.execution`)

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dw.execution.mode` | enum | `memory` | 执行模式：memory / doris |
| `dw.execution.memory.max_rows` | int | `100000` | 内存模式最大处理行数 |
| `dw.execution.memory.threads` | int | `4` | 内存模式并行线程数 |
| `dw.execution.doris.host` | string | `localhost` | Doris FE 地址 |
| `dw.execution.doris.port` | int | `9030` | Doris MySQL 协议端口 |
| `dw.execution.doris.user` | string | `root` | Doris 用户名 |
| `dw.execution.doris.database` | string | `ecos_dw` | Doris 默认库 |
| `dw.execution.doris.batch_size` | int | `10000` | Doris 批量写入行数 |
| `dw.execution.timeout` | int | `600` | 任务超时秒数 |

### 2.2 数据湖配置 (`data-engine.data-lake`)

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dw.lake.enabled` | bool | `false` | 是否启用数据湖 |
| `dw.lake.datasource_id` | string | `""` | 数据湖目标数据源 ID（从已注册数据源中选择） |
| `dw.lake.storage_format` | enum | `parquet` | 存储格式：parquet / orc / avro |
| `dw.lake.partition_by` | string | `dt` | 默认分区字段 |
| `dw.lake.retention_days` | int | `90` | 数据保留天数 |

### 2.3 对象存储配置 (`data-engine.object-storage`)

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dw.storage.type` | enum | `minio` | 对象存储类型：minio / s3 / oss |
| `dw.storage.minio.endpoint` | string | `http://localhost:9000` | MinIO 地址 |
| `dw.storage.minio.access_key` | string | `minioadmin` | Access Key |
| `dw.storage.minio.secret_key` | string | `minioadmin` | Secret Key（加密存储） |
| `dw.storage.minio.bucket` | string | `ecos-data` | 默认 Bucket |
| `dw.storage.minio.region` | string | `us-east-1` | 区域 |
| `dw.storage.minio.ssl` | bool | `false` | 是否启用 SSL |

### 2.4 管道配置 (`data-engine.pipeline`)

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dw.pipeline.max_steps` | int | `20` | 单个管道最大步骤数 |
| `dw.pipeline.parallel_steps` | int | `4` | 允许的并行步骤数 |
| `dw.pipeline.default_chunk_size` | int | `10000` | 默认分块行数 |
| `dw.pipeline.temp_table_prefix` | string | `ecos_tmp_` | 临时表前缀 |
| `dw.pipeline.temp_table_ttl_hours` | int | `24` | 临时表过期时间（小时） |
| `dw.pipeline.retry_max` | int | `3` | 步骤默认重试次数 |
| `dw.pipeline.retry_backoff_ms` | int | `5000` | 重试间隔（毫秒） |

### 2.5 数据质量配置 (`data-engine.quality`)

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dw.quality.sample_rate` | float | `1.0` | 采样率（0.0-1.0，1.0=全量） |
| `dw.quality.sample_max_rows` | int | `1000000` | 采样最大行数 |
| `dw.quality.stale_threshold_hours` | int | `24` | 数据过期阈值（小时） |
| `dw.quality.default_alert_score` | int | `80` | 默认告警分数阈值 |
| `dw.quality.concurrent_checks` | int | `2` | 并发检查任务数 |
| `dw.quality.check_timeout` | int | `300` | 单次检查超时秒数 |

### 2.6 血缘配置 (`data-engine.lineage`)

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dw.lineage.enabled` | bool | `true` | 是否启血缘采集 |
| `dw.lineage.parser` | enum | `sql` | 血缘解析引擎：sql / spark / dbt |
| `dw.lineage.max_depth` | int | `10` | 最大追溯深度 |
| `dw.lineage.cache_ttl_minutes` | int | `30` | 血缘缓存时间（分钟） |
| `dw.lineage.neo4j_enabled` | bool | `false` | 是否启用 Neo4j 图存储（enterprise+） |

### 2.7 数据引擎自身配置 (`data-engine.general`)

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dw.sync.batch_size` | int | `5000` | 同步任务默认批次大小 |
| `dw.sync.max_retries` | int | `3` | 同步任务最大重试 |
| `dw.query.max_rows` | int | `10000` | SQL 查询最大返回行数 |
| `dw.query.timeout` | int | `30` | SQL 查询超时秒数 |
| `dw.cache.ttl_seconds` | int | `300` | 元数据缓存时间 |
| `dw.engine.auto_start` | bool | `true` | 启动时自动启动数据引擎 |

### 2.8 系统管理中需迁移的配置（来源 sys_config）

| 原 config_key | 新 config_key | 迁移后从系统管理删除 |
|---------------|---------------|---------------------|
| `datasource.page_size` | `dw.datasource.page_size` | ✅ 删除 |
| `datasource.conn_timeout` | `dw.datasource.conn_timeout` | ✅ 删除 |
| `metadata.collect_timeout` | `dw.metadata.collect_timeout` | ✅ 删除 |
| `catalog.search_limit` | `dw.catalog.search_limit` | ✅ 删除 |

---

## 三、DB Schema（扩展现有 sys_config）

```sql
-- 给 sys_config 表新增 config_group 和 description 字段
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_group VARCHAR(50) DEFAULT 'general';
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_type VARCHAR(20) DEFAULT 'string';  -- string/int/float/bool/enum

-- 批量插入/更新数据工作台配置
INSERT INTO sys_config (config_key, config_value, config_group, config_type, description, status) VALUES
-- 执行引擎
('dw.execution.mode','memory','data-engine','enum','执行模式 memory/doris','active'),
('dw.execution.memory.max_rows','100000','data-engine','int','内存最大处理行数','active'),
('dw.execution.memory.threads','4','data-engine','int','并行线程数','active'),
('dw.execution.doris.host','localhost','data-engine','string','Doris FE地址','active'),
('dw.execution.doris.port','9030','data-engine','int','Doris端口','active'),
('dw.execution.timeout','600','data-engine','int','任务超时秒','active'),
-- 数据湖
('dw.lake.enabled','false','data-engine','bool','启用数据湖','active'),
('dw.lake.datasource_id','','data-engine','string','数据湖目标数据源ID','active'),
('dw.lake.storage_format','parquet','data-engine','enum','存储格式','active'),
('dw.lake.retention_days','90','data-engine','int','保留天数','active'),
-- 对象存储
('dw.storage.type','minio','data-engine','enum','对象存储类型','active'),
('dw.storage.minio.endpoint','http://localhost:9000','data-engine','string','MinIO地址','active'),
('dw.storage.minio.access_key','minioadmin','data-engine','string','AccessKey','active'),
('dw.storage.minio.bucket','ecos-data','data-engine','string','默认Bucket','active'),
-- 管道
('dw.pipeline.max_steps','20','data-engine','int','最大步骤数','active'),
('dw.pipeline.parallel_steps','4','data-engine','int','并行步骤数','active'),
('dw.pipeline.default_chunk_size','10000','data-engine','int','分块行数','active'),
('dw.pipeline.retry_max','3','data-engine','int','重试次数','active'),
-- 数据质量
('dw.quality.sample_rate','1.0','data-engine','float','采样率','active'),
('dw.quality.sample_max_rows','1000000','data-engine','int','采样最大行数','active'),
('dw.quality.default_alert_score','80','data-engine','int','告警分数阈值','active'),
-- 血缘
('dw.lineage.enabled','true','data-engine','bool','启血缘采集','active'),
('dw.lineage.parser','sql','data-engine','enum','血缘解析引擎','active'),
('dw.lineage.max_depth','10','data-engine','int','最大追溯深度','active'),
-- 数据引擎
('dw.sync.batch_size','5000','data-engine','int','同步批次大小','active'),
('dw.query.max_rows','10000','data-engine','int','查询最大行数','active'),
('dw.query.timeout','30','data-engine','int','查询超时秒','active'),
('dw.cache.ttl_seconds','300','data-engine','int','缓存时间','active'),
('dw.engine.auto_start','true','data-engine','bool','自动启动引擎','active')
ON CONFLICT (config_key) DO UPDATE SET config_value=EXCLUDED.config_value, description=EXCLUDED.description;
```

---

## 四、API 设计

```
GET    /api/v1/engine/data/config                         — 获取所有数据引擎配置（分组返回）
GET    /api/v1/engine/data/config/{group}                 — 获取指定分组配置
PUT    /api/v1/engine/data/config                         — 批量更新配置 [{config_key, config_value}]
POST   /api/v1/engine/data/config/refresh                 — 热刷新配置缓存
GET    /api/v1/engine/data/config/defaults                — 获取所有默认值
```

返回格式：
```json
{
  "code": 0,
  "data": {
    "data-engine": {
      "execution": { "mode": "memory", "memory.max_rows": 100000, ... },
      "pipeline": { "max_steps": 20, ... },
      "quality": { "sample_rate": 1.0, ... },
      "lineage": { "enabled": true, ... }
    }
  }
}
```

---

## 五、前端页面设计

**位置**：数据工作台左侧功能栏底部，在"SQL 查询"按钮下方新增"⚙ 引擎配置"

**页面布局**：
- 左侧：配置分组导航（执行引擎 / 数据湖 / 对象存储 / 管道 / 数据质量 / 血缘 / 通用）
- 右侧：表单（标签+输入框+默认值提示+保存按钮）
- 底部：全局操作栏（恢复默认 / 全部保存 / 刷新缓存）

**交互**：
- 修改任意配置后分组标题显示 ● 标记
- Ctrl+S 保存当前分组
- 敏感字段（密钥）显示为 `****`
- 每个字段旁边 tooltip 显示说明

---

## 六、配置注入机制

采用 **Spring @ConfigurationProperties + SysConfigService 桥接** 模式：

```java
@Component
@ConfigurationProperties(prefix = "ecos.data-engine")
public class DataEngineConfig {
    // 启动时从 application.yml 读默认值
    // 运行时通过 SysConfigService 覆盖（sys_config 优先级 > yml）
}
```

配置优先级：**sys_config（DB）> application.yml > 代码硬编码默认值**

---

## 七、系统管理清理清单

从 `SysConfigController` 或系统管理前端页面中移除以下配置项入口：

| 配置项 | 原因 |
|--------|------|
| `datasource.page_size` | 迁移到 `dw.datasource.page_size` |
| `datasource.conn_timeout` | 迁移到 `dw.datasource.conn_timeout` |
| `metadata.collect_timeout` | 迁移到 `dw.metadata.collect_timeout` |
| `catalog.search_limit` | 迁移到 `dw.catalog.search_limit` |

**清理原则**：只删除前端入口，不做物理表删除（保留数据迁移痕迹）。

---

## 八、Sprint 分期

| Sprint | 内容 | |
|--------|------|---|
| 3.1 | BE: sys_config 扩展 + DataEngineConfigController | |
| 3.2 | FE: 数据工作台配置页面 | |
| 3.3 | 配置注入管道/质量/同步/查询功能 | |
| 3.4 | 配置注入 MinIO/数据湖/Doris | |
| 3.5 | 系统管理清理 + 端到端验证 | |

---

## 九、下一步

- [ ] ARCH: 确认 sys_config 扩展方案 + 配置注入架构
- [ ] BE: 实现 DataEngineConfigController + 扩展 SysConfigService
- [ ] FE: 实现配置页面并接入数据工作台侧边栏
