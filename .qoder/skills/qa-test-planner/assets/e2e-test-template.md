## E2E 测试用例模板

### E2E-XXX: {用户路径描述}

**PRD 追溯**：
- PRD-{功能ID1}（{功能名称1}）
- PRD-{功能ID2}（{功能名称2}）

**优先级**：P0 / P1 / P2
**用户路径**：{用户操作路径描述}
**预计耗时**：{estimated_time}

**测试步骤**（使用 Playwright）：

```typescript
import { test, expect } from '@playwright/test'

test('{用例名称}', async ({ page }) => {
  // 1. {操作1} → F{功能ID1}
  await page.goto('/{page1}')
  await page.fill('#{field1}', '{value1}')
  await page.fill('#{field2}', '{value2}')
  await page.click('button[type="submit"]')
  await expect(page.locator('.toast')).toContainText('{expected_message}')

  // 2. {操作2} → F{功能ID2}
  await page.goto('/{page2}')
  await page.fill('#{field3}', '{value3}')
  await page.click('button[type="submit"]')
  await expect(page).toHaveURL('/{expected_url}')

  // 3. 验证结果
  await expect(page.locator('.{element}')).toContainText('{expected_text}')
})
```

### 冒烟测试用例

每次部署必须执行的 E2E 测试用例：

| 用例 ID | 用例名称 | PRD 追溯 | 预计耗时 |
|---------|---------|---------|---------|
| E2E-001 | 用户注册登录登出 | F1.1, F2.1 | 30s |
| E2E-002 | 商品搜索到下单 | F3.1, F4.1, F5.1 | 60s |
| E2E-003 | 用户个人信息修改 | F3.1 | 30s |

### E2E 测试最佳实践

1. **测试数据准备**：使用 beforeEach 进行数据初始化
2. **页面对象模式**：提取页面元素为 Page Object
3. **并行执行**：使用 `test.describe.parallel` 加速执行
4. **失败截图**：配置 `toHaveScreenshot` 保留失败证据
5. **视频录制**：开启 `video: 'on-first-retry'` 保留失败视频