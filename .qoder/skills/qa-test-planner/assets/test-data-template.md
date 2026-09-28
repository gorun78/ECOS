## 测试数据策略

### Mock 数据（单元测试）

- 使用 faker 库生成随机数据
- 固定种子确保测试可重复

### Fixture 数据（集成测试）

```yaml
# tests/fixtures/users.yaml
- id: "user-001"
  email: "test@example.com"
  password: "$2a$10$..."  # BCrypt 加密后的 "password123"
  name: "测试用户"
  status: 1
  prd_ref: "F1.1"

- id: "user-002"
  email: "inactive@example.com"
  password: "$2a$10$..."
  name: "已禁用用户"
  status: 2
  prd_ref: "F2.1"
```

### 工厂模式（前端 E2E 测试）

```typescript
// tests/factories/user.factory.ts
import { faker } from '@faker-js/faker'

export const createUser = (overrides = {}) => ({
  id: faker.string.uuid(),
  email: faker.internet.email(),
  name: faker.person.fullName(),
  password: 'password123',
  status: 1,
  prd_ref: 'F1.1',  // ← PRD 追溯
  ...overrides
})

export const createAdmin = (overrides = {}) => ({
  ...createUser(overrides),
  role: 'admin',
  status: 1
})
```

### 测试数据清理策略

| 测试类型 | 清理策略 |
|----------|----------|
| 单元测试 | 每个测试方法内 Mock，不依赖外部数据 |
| 集成测试 | 使用事务回滚或测试前后清理 |
| E2E 测试 | 使用 beforeEach 创建、afterEach 清理 |