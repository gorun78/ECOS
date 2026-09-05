# 农副产品商城小程序派发参考

## 实际验证通过的 swarm 命令

```bash
hermes kanban --board app-boards swarm \
  --worker arch-1784863176702:架构设计 \
  --worker fullstack-1784863188046:前后端开发 \
  --worker qa-1784863199748:QA测试 \
  --verifier pm-1784863164619 \
  --synthesizer pm-1784863164619 \
  --created-by pm-1784863164619 \
  --json "农副产品商城小程序：连接农户与消费者，提供蔬菜、水果、粮油、禽蛋、肉类及地方特产等商品在线展示与销售。支持商品分类、搜索、购物车、在线支付、订单查询、物流跟踪、优惠活动和售后申请。展示产品产地、种植方式及质检信息，帮助消费者便捷选购安全、新鲜、可追溯的农副产品，同时拓宽农户销售渠道，促进农产品流通与乡村产业发展。"
```

## 任务图

```
[t_f625dbbd] Swarm根任务(PM) ✅已完成
    ├── [t_75ef63ba] 架构设计(架构-1) 🔄进行中
    └── [t_2b486352] 前后端开发(开发-1) 🔄进行中
            ├── [t_8fe38425] QA测试(测试-1) ⏸️阻塞
            └── [t_897ed305] 代码审查(PM) ⏳待办
                                        │
                                        ▼
                            [t_f625dbbd] PM汇合交付(PM) ⏳待办
```

## 派发结果

返回 JSON:
```json
{
  "root_id": "t_f625dbbd",
  "worker_ids": ["t_75ef63ba", "t_2b486352", "t_8fe38425"],
  "verifier_id": "t_897ed305",
  "synthesizer_id": "t_a4988893"
}
```

## 关键教训

1. **--worker 必须加 `:标题` 后缀**，不能用裸 profile code，否则任务标题变成"架构设计工作目录放到/home/hermes/prj/app/3"
2. **TERMINAL_CWD 在各 agent 的 .env 中配置**，swarm 命令本身无 --workdir 参数
3. **终端工具用 `terminal`**，execute_code 子进程调用会被拦截
4. **Python 3.8 系统** build_dispatch.py 会因 `dict[str, Any]` 语法报错，改用手动构造 kanban.json

## 服务状态

- 前端: http://10.48.1.166:3000
- 后端: http://10.48.1.166:8000
- 工作目录: /home/hermes/prj/app/3
- 数据库: /home/hermes/prj/app/3/backend/farm_market.db (SQLite)
- 产品图片: /home/hermes/prj/app/3/frontend/public/images/products/