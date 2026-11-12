import { test, expect } from '@playwright/test';

for (const state of ['linked', 'zero', 'missing', 'disabled', 'links-error', 'config-error', 'empty'] as const) {
  test(`dashboard: saldo bancário ${state} separado do saldo interno, sem importação`, async ({ page }, info) => {
    const requests: string[] = [];
    await page.route('**/api/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname;
      requests.push(request.method() + ' ' + path);
      if (request.method() !== 'GET') { await route.abort(); return; }
      let body: unknown;
      if (path === '/api/auth/me') body = { id: 'profile-test', name: 'Perfil de teste', email: 'test@example.test' };
      else if (path === '/api/reports/summary') body = {
        month: '2026-11', today: '2026-11-12', balance: '1000.00', accountCount: 1, records: 0,
        competence: { income: '0', expenses: '0', result: '0', savingsRate: null },
        cashFlow: '0', categories: [], evolution: [], upcoming: [],
      };
      else if (path === '/api/banking/config') {
        if (state === 'config-error') { await route.fulfill({ status: 503, json: { message: 'Erro simulado' } }); return; }
        body = { ready: true, userId: 'profile-test', items: ['item-test'], provider: 'Meu Pluggy' };
      } else if (path === '/api/banking/links') {
        if (state === 'links-error') { await route.fulfill({ status: 503, json: { message: 'Erro simulado' } }); return; }
        body = state === 'empty' ? [] : [{ id: 'link-test', name: 'Nubank de teste', accountId: 'account-test', active: state !== 'disabled',
          importFrom: '2026-10-15', lastCheckedAt: state === 'missing' ? null : '2026-11-12T17:52:13Z',
          providerUpdatedAt: state === 'missing' ? null : '2026-11-12T01:23:39Z',
          reportedBalance: state === 'missing' ? null : state === 'zero' ? '0.00' : '950.25' }];
      } else { await route.abort(); return; }
      await route.fulfill({ json: body });
    });
    await page.goto('/#/');
    await expect(page.getByText('Saldo interno', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'R$ 1.000,00', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Saldo informado pelo banco', exact: true })).toBeVisible();
    const bankPanel = page.getByRole('heading', { name: 'Saldo informado pelo banco', exact: true }).locator('..');
    if (state === 'links-error' || state === 'config-error') {
      await expect(page.getByText(/Não foi possível carregar o saldo bancário/)).toBeVisible({ timeout: 15000 });
    } else if (state === 'empty') {
      await expect(page.getByRole('link', { name: 'Vincular meu banco', exact: true })).toBeVisible();
    } else {
      await expect(page.getByRole('link', { name: 'Ver conexão e revisar lançamentos', exact: true })).toHaveAttribute('href', '#/banking');
      if (state === 'missing') {
        await expect(page.getByText('Saldo não disponível', { exact: true })).toBeVisible();
        await expect(page.getByText('Atualização no banco: Não informada', { exact: true })).toBeVisible();
      } else {
        await expect(bankPanel.getByText(state === 'zero' ? 'R$ 0,00' : 'R$ 950,25', { exact: true })).toBeVisible();
        // UTC -> Brasília crosses the date boundary; the provider timestamp must stay distinct from our consultation.
        await expect(page.getByText('Atualização no banco: 11/11/2026, 22:23:39 (Brasília)', { exact: true })).toBeVisible();
        await expect(page.getByText('Última consulta no sistema: 12/11/2026, 14:52:13 (Brasília)', { exact: true })).toBeVisible();
      }
      if (state === 'disabled') await expect(page.getByText(/Leitura desativada/)).toBeVisible();
    }
    await expect(page.getByText(/nada é importado automaticamente/)).toBeVisible();
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      if (state === 'linked') await page.screenshot({ path: info.outputPath(`dashboard-saldos-${width}.png`), fullPage: true });
    }
    expect(requests.every(request => request.startsWith('GET '))).toBe(true);
    expect(requests.every(request => ['GET /api/auth/me', 'GET /api/reports/summary', 'GET /api/banking/config', 'GET /api/banking/links'].includes(request))).toBe(true);
  });
}
