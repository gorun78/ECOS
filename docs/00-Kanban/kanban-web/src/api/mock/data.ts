import type { UserVO } from '@/types/user'
import type { BoardVO } from '@/types/board'
import type { BoardColumnVO } from '@/types/column'
import type { CardVO, TagVO } from '@/types/card'

/** Mock 用户数据 */
export const MOCK_USERS: UserVO[] = [
  { id: 'u-001', username: 'zhangsan', realName: '张三', email: 'zhangsan@ecos.com', role: 'board_admin', status: 1, createdAt: '2026-01-01T00:00:00Z' },
  { id: 'u-002', username: 'lisi', realName: '李四', email: 'lisi@ecos.com', role: 'member', status: 1, createdAt: '2026-01-01T00:00:00Z' },
  { id: 'u-003', username: 'wangwu', realName: '王五', email: 'wangwu@ecos.com', role: 'member', status: 1, createdAt: '2026-01-01T00:00:00Z' },
  { id: 'u-004', username: 'zhaoliu', realName: '赵六', email: 'zhaoliu@ecos.com', role: 'viewer', status: 1, createdAt: '2026-01-01T00:00:00Z' },
  { id: 'u-005', username: 'admin', realName: '系统管理员', email: 'admin@ecos.com', role: 'sys_admin', status: 1, createdAt: '2025-06-01T00:00:00Z' },
  { id: 'u-006', username: 'sunqi', realName: '孙七', email: 'sunqi@ecos.com', role: 'member', status: 0, createdAt: '2026-02-01T00:00:00Z' },
]

/** Mock 标签数据 */
export const MOCK_TAGS: TagVO[] = [
  { id: 't-001', name: '紧急', color: '#F56C6C', boardId: 'b-001' },
  { id: 't-002', name: '高优', color: '#E6A23C', boardId: 'b-001' },
  { id: 't-003', name: '待确认', color: '#909399', boardId: 'b-001' },
  { id: 't-004', name: '供应商', color: '#409EFF', boardId: 'b-001' },
  { id: 't-005', name: '合同', color: '#67C23A', boardId: 'b-001' },
]

/** Mock 卡片数据 */
function createMockCards(boardId: string, columnMap: Record<string, string>): CardVO[] {
  return [
    {
      id: 'c-001', boardId, columnId: columnMap['todo'], title: '星辰科技供应商入库审核',
      description: '星辰科技新供应商首次入库，需完成资质审核、验厂报告、风险评估、合规审查、领导审批',
      assigneeId: 'u-001', assignee: MOCK_USERS[0], dueDate: '2026-06-25',
      priority: 'high', position: 1000,
      tags: [MOCK_TAGS[0], MOCK_TAGS[3], MOCK_TAGS[1]],
      checklists: [
        { id: 'cl-01', cardId: 'c-001', content: '审核营业执照', completed: true, position: 0 },
        { id: 'cl-02', cardId: 'c-001', content: '验厂报告', completed: true, position: 1 },
        { id: 'cl-03', cardId: 'c-001', content: '风险评估报告', completed: false, position: 2 },
        { id: 'cl-04', cardId: 'c-001', content: '合规审查', completed: false, position: 3 },
        { id: 'cl-05', cardId: 'c-001', content: '领导审批', completed: false, position: 4 },
      ],
      comments: [], activities: [],
      createdBy: MOCK_USERS[0], createdAt: '2026-06-14T09:00:00Z', updatedAt: '2026-06-16T10:30:00Z',
    },
    {
      id: 'c-002', boardId, columnId: columnMap['todo'], title: '华讯科技框架协议续签',
      description: '华讯科技年度框架协议到期，需要进行续签评估',
      assigneeId: 'u-002', assignee: MOCK_USERS[1], dueDate: '2026-06-28',
      priority: 'medium', position: 2000,
      tags: [MOCK_TAGS[4]],
      checklists: [
        { id: 'cl-06', cardId: 'c-002', content: '收集历史合作数据', completed: true, position: 0 },
        { id: 'cl-07', cardId: 'c-002', content: '评估服务质量', completed: false, position: 1 },
      ],
      comments: [], activities: [],
      createdBy: MOCK_USERS[0], createdAt: '2026-06-13T14:00:00Z', updatedAt: '2026-06-15T16:00:00Z',
    },
    {
      id: 'c-003', boardId, columnId: columnMap['todo'], title: '海康威视安防设备采购',
      description: '园区安防系统升级，需采购海康威视监控设备', priority: 'urgent', position: 3000,
      tags: [], checklists: [], comments: [], activities: [],
      createdBy: MOCK_USERS[1], createdAt: '2026-06-15T08:00:00Z', updatedAt: '2026-06-15T08:00:00Z',
    },
    {
      id: 'c-004', boardId, columnId: columnMap['review'], title: '德勤审计服务采购',
      description: '年度财务报表审计服务采购，需评估三家供应商报价',
      assigneeId: 'u-003', assignee: MOCK_USERS[2], dueDate: '2026-06-22',
      priority: 'high', position: 1000,
      tags: [MOCK_TAGS[1]], checklists: [], comments: [], activities: [],
      createdBy: MOCK_USERS[0], createdAt: '2026-06-12T10:00:00Z', updatedAt: '2026-06-16T09:15:00Z',
    },
    {
      id: 'c-005', boardId, columnId: columnMap['review'], title: '深信服安全服务续约',
      description: '网络安全防护服务合同即将到期，需评审续约方案',
      assigneeId: 'u-001', assignee: MOCK_USERS[0], dueDate: '2026-06-20',
      priority: 'urgent', position: 2000,
      tags: [MOCK_TAGS[0], MOCK_TAGS[3]], checklists: [], comments: [], activities: [],
      createdBy: MOCK_USERS[1], createdAt: '2026-06-11T11:00:00Z', updatedAt: '2026-06-16T10:00:00Z',
    },
    {
      id: 'c-006', boardId, columnId: columnMap['sign'], title: '用友软件许可续费',
      description: 'ERP系统年度许可费续费，合同金额约50万元',
      assigneeId: 'u-003', assignee: MOCK_USERS[2], dueDate: '2026-06-30',
      priority: 'medium', position: 1000,
      tags: [MOCK_TAGS[4]], checklists: [], comments: [], activities: [],
      createdBy: MOCK_USERS[0], createdAt: '2026-06-10T09:00:00Z', updatedAt: '2026-06-15T14:30:00Z',
    },
    {
      id: 'c-007', boardId, columnId: columnMap['done'], title: '阿里云服务器续费',
      description: 'ECS服务器年度续费已完成',
      priority: 'medium', position: 1000,
      tags: [], checklists: [], comments: [], activities: [],
      completedAt: '2026-06-15T17:00:00Z',
      createdBy: MOCK_USERS[0], createdAt: '2026-06-01T09:00:00Z', updatedAt: '2026-06-15T17:00:00Z',
    },
    {
      id: 'c-008', boardId, columnId: columnMap['done'], title: '办公桌椅采购',
      description: '新办公区桌椅采购已完成验收',
      assigneeId: 'u-002', assignee: MOCK_USERS[1],
      priority: 'low', position: 2000,
      tags: [], checklists: [], comments: [], activities: [],
      completedAt: '2026-06-14T16:00:00Z',
      createdBy: MOCK_USERS[1], createdAt: '2026-06-05T10:00:00Z', updatedAt: '2026-06-14T16:00:00Z',
    },
  ]
}

/** Mock 看板数据 */
export function createMockBoards(): BoardVO[] {
  const columns: BoardColumnVO[] = [
    { id: 'col-01', boardId: 'b-001', name: '待处理', position: 0, color: '#E2E8F0', isDoneColumn: false, createdAt: '2026-06-01T00:00:00Z' },
    { id: 'col-02', boardId: 'b-001', name: '审核中', position: 1, color: '#FEF3C7', wipLimit: 3, isDoneColumn: false, createdAt: '2026-06-01T00:00:00Z' },
    { id: 'col-03', boardId: 'b-001', name: '待签约', position: 2, color: '#DBEAFE', isDoneColumn: false, createdAt: '2026-06-01T00:00:00Z' },
    { id: 'col-04', boardId: 'b-001', name: '已完成', position: 3, color: '#D1FAE5', isDoneColumn: true, createdAt: '2026-06-01T00:00:00Z' },
  ]

  const columnMap: Record<string, string> = {
    todo: 'col-01', review: 'col-02', sign: 'col-03', done: 'col-04',
  }

  const cards = createMockCards('b-001', columnMap)

  const columnsWithCards = columns.map(col => ({
    ...col,
    cards: cards.filter(c => c.columnId === col.id),
  }))

  return [
    {
      id: 'b-001', name: '供应商准入看板',
      description: '管理供应商准入全流程，从提交申请到签约完成',
      template: '4col', createdBy: 'u-001',
      createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-16T10:00:00Z',
      isArchived: false, ownerTeam: '采购部',
      columns: columnsWithCards,
      members: [
        { userId: 'u-001', user: MOCK_USERS[0], role: 'admin', joinedAt: '2026-06-01T00:00:00Z' },
        { userId: 'u-002', user: MOCK_USERS[1], role: 'admin', joinedAt: '2026-06-01T00:00:00Z' },
        { userId: 'u-003', user: MOCK_USERS[2], role: 'member', joinedAt: '2026-06-01T00:00:00Z' },
        { userId: 'u-004', user: MOCK_USERS[3], role: 'viewer', joinedAt: '2026-06-01T00:00:00Z' },
      ],
    },
    {
      id: 'b-002', name: '研发任务看板',
      description: '研发团队Sprint任务跟踪',
      template: '3col', createdBy: 'u-002',
      createdAt: '2026-06-05T00:00:00Z', updatedAt: '2026-06-15T09:00:00Z',
      isArchived: false, ownerTeam: '研发部',
      columns: [
        { id: 'col-11', boardId: 'b-002', name: '待开发', position: 0, color: '#E2E8F0', isDoneColumn: false, createdAt: '2026-06-05T00:00:00Z', cards: [
          { id: 'c-101', boardId: 'b-002', columnId: 'col-11', title: '用户权限模块重构', priority: 'high', position: 1000, tags: [], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[1], createdAt: '2026-06-05T00:00:00Z', updatedAt: '2026-06-05T00:00:00Z' },
          { id: 'c-102', boardId: 'b-002', columnId: 'col-11', title: 'API网关限流实现', priority: 'medium', position: 2000, tags: [], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[2], createdAt: '2026-06-06T00:00:00Z', updatedAt: '2026-06-06T00:00:00Z' },
        ]},
        { id: 'col-12', boardId: 'b-002', name: '开发中', position: 1, color: '#FEF3C7', wipLimit: 3, isDoneColumn: false, createdAt: '2026-06-05T00:00:00Z', cards: [
          { id: 'c-103', boardId: 'b-002', columnId: 'col-12', title: '数据导入导出功能', assigneeId: 'u-001', assignee: MOCK_USERS[0], priority: 'high', position: 1000, tags: [], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[0], createdAt: '2026-06-07T00:00:00Z', updatedAt: '2026-06-15T00:00:00Z' },
        ]},
        { id: 'col-13', boardId: 'b-002', name: '已完成', position: 2, color: '#D1FAE5', isDoneColumn: true, createdAt: '2026-06-05T00:00:00Z', cards: [
          { id: 'c-104', boardId: 'b-002', columnId: 'col-13', title: '登录页面优化', priority: 'low', position: 1000, tags: [], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[1], createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z', completedAt: '2026-06-10T00:00:00Z' },
        ]},
      ],
      members: [
        { userId: 'u-002', user: MOCK_USERS[1], role: 'admin', joinedAt: '2026-06-05T00:00:00Z' },
        { userId: 'u-001', user: MOCK_USERS[0], role: 'member', joinedAt: '2026-06-05T00:00:00Z' },
        { userId: 'u-003', user: MOCK_USERS[2], role: 'member', joinedAt: '2026-06-05T00:00:00Z' },
      ],
    },
    {
      id: 'b-004', name: 'ECOS 项目总看板',
      description: 'ECOS 认知操作系统项目 — P1骨架加固阶段已完成，准备进入下一阶段',
      template: '5col', createdBy: 'u-005',
      createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-16T18:00:00Z',
      isArchived: false, ownerTeam: 'ECOS 研发团队',
      columns: [
        {
          id: 'col-31', boardId: 'b-004', name: '📋 TODO', position: 0, color: '#E2E8F0', isDoneColumn: false, createdAt: '2026-06-01T00:00:00Z',
          cards: [
            {
              id: 'c-301', boardId: 'b-004', columnId: 'col-31', title: '🔥 后端部署修复 BUG-005',
              description: 'TASK-NP04: 修复后端部署问题，需 BE 修复 + QA 验证', priority: 'high', position: 1000,
              assigneeId: 'u-001', assignee: MOCK_USERS[0], dueDate: '2026-06-20',
              tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }],
              checklists: [], comments: [], activities: [],
              createdBy: MOCK_USERS[4], createdAt: '2026-06-16T00:00:00Z', updatedAt: '2026-06-16T12:00:00Z',
            },
            {
              id: 'c-302', boardId: 'b-004', columnId: 'col-31', title: '📐 差距分析 — 设计与实现差异梳理',
              description: 'RISK-001: 系统梳理现有实现与设计文档的差距，为下一阶段规划提供基础', priority: 'high', position: 2000,
              assigneeId: 'u-003', assignee: MOCK_USERS[2], dueDate: '2026-06-23',
              tags: [{ id: 't-ecos-02', name: '风险', color: '#E6A23C', boardId: 'b-004' }],
              checklists: [], comments: [], activities: [],
              createdBy: MOCK_USERS[4], createdAt: '2026-06-16T00:00:00Z', updatedAt: '2026-06-16T12:00:00Z',
            },
          ],
        },
        {
          id: 'col-32', boardId: 'b-004', name: '🔧 DOING', position: 1, color: '#FEF3C7', isDoneColumn: false, createdAt: '2026-06-01T00:00:00Z',
          cards: [],
        },
        {
          id: 'col-33', boardId: 'b-004', name: '👁️ REVIEW', position: 2, color: '#DBEAFE', isDoneColumn: false, createdAt: '2026-06-01T00:00:00Z',
          cards: [],
        },
        {
          id: 'col-34', boardId: 'b-004', name: '✅ DONE', position: 3, color: '#D1FAE5', isDoneColumn: true, createdAt: '2026-06-01T00:00:00Z',
          cards: [
            { id: 'c-311', boardId: 'b-004', columnId: 'col-34', title: 'TASK-001: P0-安全修复方案设计', priority: 'high', position: 1000, tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[0], createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-15T00:00:00Z', completedAt: '2026-06-15T00:00:00Z' },
            { id: 'c-312', boardId: 'b-004', columnId: 'col-34', title: 'TASK-NP01: 修复SQL注入', priority: 'high', position: 2000, tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[0], createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-15T00:00:00Z', completedAt: '2026-06-15T00:00:00Z' },
            { id: 'c-313', boardId: 'b-004', columnId: 'col-34', title: 'TASK-P103: 统一响应格式+全局异常处理', priority: 'medium', position: 3000, tags: [{ id: 't-ecos-03', name: 'P1', color: '#409EFF', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[0], createdAt: '2026-06-08T00:00:00Z', updatedAt: '2026-06-14T00:00:00Z', completedAt: '2026-06-14T00:00:00Z' },
            { id: 'c-314', boardId: 'b-004', columnId: 'col-34', title: 'TASK-P108: Flyway Schema管理', priority: 'medium', position: 4000, tags: [{ id: 't-ecos-03', name: 'P1', color: '#409EFF', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[0], createdAt: '2026-06-08T00:00:00Z', updatedAt: '2026-06-14T00:00:00Z', completedAt: '2026-06-14T00:00:00Z' },
            { id: 'c-315', boardId: 'b-004', columnId: 'col-34', title: 'TASK-P105: 前端API层合并', priority: 'medium', position: 5000, tags: [{ id: 't-ecos-03', name: 'P1', color: '#409EFF', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[0], createdAt: '2026-06-08T00:00:00Z', updatedAt: '2026-06-14T00:00:00Z', completedAt: '2026-06-14T00:00:00Z' },
            { id: 'c-316', boardId: 'b-004', columnId: 'col-34', title: 'P0-1: Object Runtime 全链路', priority: 'medium', position: 6000, tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z', completedAt: '2026-06-13T00:00:00Z' },
            { id: 'c-317', boardId: 'b-004', columnId: 'col-34', title: 'P0-2: Ontology Designer', priority: 'medium', position: 7000, tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-12T00:00:00Z', completedAt: '2026-06-12T00:00:00Z' },
            { id: 'c-318', boardId: 'b-004', columnId: 'col-34', title: 'P0-3: Workflow Designer', priority: 'medium', position: 8000, tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-12T00:00:00Z', completedAt: '2026-06-12T00:00:00Z' },
            { id: 'c-319', boardId: 'b-004', columnId: 'col-34', title: 'P0-4: World Model 后端', priority: 'medium', position: 9000, tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z', completedAt: '2026-06-13T00:00:00Z' },
            { id: 'c-320', boardId: 'b-004', columnId: 'col-34', title: 'P0-5: Data Quality Dashboard', priority: 'medium', position: 10000, tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z', completedAt: '2026-06-13T00:00:00Z' },
            { id: 'c-321', boardId: 'b-004', columnId: 'col-34', title: 'P0-6: IAM 完善', priority: 'medium', position: 11000, tags: [{ id: 't-ecos-01', name: 'P0', color: '#F56C6C', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z', completedAt: '2026-06-13T00:00:00Z' },
            { id: 'c-322', boardId: 'b-004', columnId: 'col-34', title: 'P1-1: Agent Builder', priority: 'low', position: 12000, tags: [{ id: 't-ecos-03', name: 'P1', color: '#409EFF', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-05T00:00:00Z', updatedAt: '2026-06-14T00:00:00Z', completedAt: '2026-06-14T00:00:00Z' },
            { id: 'c-323', boardId: 'b-004', columnId: 'col-34', title: 'P1-2: Knowledge Graph (Neo4j)', priority: 'low', position: 13000, tags: [{ id: 't-ecos-03', name: 'P1', color: '#409EFF', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-05T00:00:00Z', updatedAt: '2026-06-14T00:00:00Z', completedAt: '2026-06-14T00:00:00Z' },
            { id: 'c-324', boardId: 'b-004', columnId: 'col-34', title: 'P1-3: Pipeline 节点库', priority: 'low', position: 14000, tags: [{ id: 't-ecos-03', name: 'P1', color: '#409EFF', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-05T00:00:00Z', updatedAt: '2026-06-14T00:00:00Z', completedAt: '2026-06-14T00:00:00Z' },
            { id: 'c-325', boardId: 'b-004', columnId: 'col-34', title: 'P1-4: Agent Mesh MVP', priority: 'low', position: 15000, tags: [{ id: 't-ecos-03', name: 'P1', color: '#409EFF', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-05T00:00:00Z', updatedAt: '2026-06-14T00:00:00Z', completedAt: '2026-06-14T00:00:00Z' },
            { id: 'c-326', boardId: 'b-004', columnId: 'col-34', title: 'P1-5: Data Catalog 完善', priority: 'low', position: 16000, tags: [{ id: 't-ecos-03', name: 'P1', color: '#409EFF', boardId: 'b-004' }], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-05T00:00:00Z', updatedAt: '2026-06-14T00:00:00Z', completedAt: '2026-06-14T00:00:00Z' },
          ],
        },
        {
          id: 'col-35', boardId: 'b-004', name: '🚫 BLOCKED', position: 4, color: '#FCE4EC', isDoneColumn: false, createdAt: '2026-06-01T00:00:00Z',
          cards: [],
        },
      ],
      members: [
        { userId: 'u-005', user: MOCK_USERS[4], role: 'admin', joinedAt: '2026-06-01T00:00:00Z' },
        { userId: 'u-001', user: MOCK_USERS[0], role: 'admin', joinedAt: '2026-06-01T00:00:00Z' },
        { userId: 'u-002', user: MOCK_USERS[1], role: 'member', joinedAt: '2026-06-01T00:00:00Z' },
        { userId: 'u-003', user: MOCK_USERS[2], role: 'member', joinedAt: '2026-06-01T00:00:00Z' },
        { userId: 'u-004', user: MOCK_USERS[3], role: 'viewer', joinedAt: '2026-06-01T00:00:00Z' },
      ],
    },
    {
      id: 'b-003', name: '合同审批看板',
      description: '法务团队合同审批流程跟踪',
      template: '5col', createdBy: 'u-005',
      createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-15T11:00:00Z',
      isArchived: false, ownerTeam: '法务部',
      columns: [
        { id: 'col-21', boardId: 'b-003', name: '待提交', position: 0, color: '#E2E8F0', isDoneColumn: false, createdAt: '2026-06-10T00:00:00Z', cards: [] },
        { id: 'col-22', boardId: 'b-003', name: '法务初审', position: 1, color: '#FEF3C7', isDoneColumn: false, createdAt: '2026-06-10T00:00:00Z', cards: [
          { id: 'c-201', boardId: 'b-003', columnId: 'col-22', title: '供应商采购合同（星辰科技）', priority: 'high', position: 1000, tags: [], checklists: [], comments: [], activities: [], createdBy: MOCK_USERS[4], createdAt: '2026-06-12T00:00:00Z', updatedAt: '2026-06-12T00:00:00Z' },
        ]},
        { id: 'col-23', boardId: 'b-003', name: '部门会签', position: 2, color: '#DBEAFE', isDoneColumn: false, createdAt: '2026-06-10T00:00:00Z', cards: [] },
        { id: 'col-24', boardId: 'b-003', name: '领导审批', position: 3, color: '#FCE4EC', isDoneColumn: false, createdAt: '2026-06-10T00:00:00Z', cards: [] },
        { id: 'col-25', boardId: 'b-003', name: '已签署', position: 4, color: '#D1FAE5', isDoneColumn: true, createdAt: '2026-06-10T00:00:00Z', cards: [] },
      ],
      members: [
        { userId: 'u-005', user: MOCK_USERS[4], role: 'admin', joinedAt: '2026-06-10T00:00:00Z' },
      ],
    },
  ]
}
