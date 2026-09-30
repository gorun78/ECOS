import { ECOS_API } from '../playwright.config';

// 前置件：gateway :8080 与前端 :3000 由 _win_tasks/start-backend.ps1 / start-frontend.ps1 拉起，
// 本工程不自行启停服务（避免与端口白名单脚本冲突）。服务未就绪时快速失败，避免误读为用例失败。
export default async function globalSetup(): Promise<void> {
  const { ECOS_USER, ECOS_PASS } = process.env;
  if (!ECOS_USER || !ECOS_PASS) {
    throw new Error(
      '缺少 ECOS_USER / ECOS_PASS。凭据只从 ecos-tests/.env 或进程环境变量读取（禁写入用例），' +
      '本机开发凭据见 .trae/rules/开发环境登录凭据.md。参考 ecos-tests/.env.example。'
    );
  }
  try {
    const res = await fetch(`${ECOS_API}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: ECOS_USER, password: ECOS_PASS }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = (await res.json().catch(() => ({}))) as { data?: { token?: string; accessToken?: string } };
    if (!(json?.data?.token || json?.data?.accessToken)) throw new Error('响应无 token 字段');
  } catch (e) {
    throw new Error(`gateway 未就绪（${ECOS_API}）：${String(e)}。请先跑 _win_tasks/start-backend.ps1`);
  }
}
