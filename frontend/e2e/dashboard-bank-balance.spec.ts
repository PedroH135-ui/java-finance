import { test, expect } from '@playwright/test';

for (const state of ['linked', 'zero', 'missing', 'manual'] as const) {
  test(`dashboard: total principal ${state} sem segundo saldo`, async ({ page }, info) => {
    const requests: string[] = [];
    await page.route('**/api/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname;
      requests.push(request.method() + ' ' + path);
      if (request.method() !== 'GET') { await route.abort(); return; }
      let body: unknown;
      if (path === '/api/auth/me') body = { id: 'profile-test', name: 'Perfil de teste', email: 'test@example.test' };
      else if (path === '/api/reports/summary') body = {
        month: '2026-11', today: '2026-11-12', balance: state === 'missing' ? null : state === 'zero' ? '0.00' : state === 'manual' ? '1000.00' : '1030.25',
        balanceComplete: state !== 'missing', accountCount: 2, records: 0,
        competence: { income: '0', expenses: '0', result: '0', savingsRate: null },
        unclassifiedBankRecords: state === 'linked' ? 2 : 0, cashFlow: '0', categories: state === 'linked' ? [{ id: null, name: 'Sem categoria', total: '50.25' }] : [], evolution: [], upcoming: [],
      };
      else { await route.abort(); return; }
      await route.fulfill({ json: body });
    });
    await page.goto('/#/');
    await expect(page.getByText('Saldo das contas', { exact: true })).toBeVisible();
    const balance = page.locator('a[href="#/accounts"]').filter({ hasText: state === 'missing' ? 'Não disponível' : state === 'zero' ? 'R$ 0,00' : state === 'manual' ? 'R$ 1.000,00' : 'R$ 1.030,25' });
    await expect(balance).toBeVisible();
    await expect(page.getByText('Saldo interno', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Saldo informado pelo banco', exact: true })).toHaveCount(0);
    if (state === 'linked') {
      await expect(page.getByText(/Os totais incluem 2 registro/)).toBeVisible();
      await expect(page.getByRole('link', { name: 'Sem categoria', exact: true })).toHaveAttribute('href', /category=uncategorized/);
    }
    if (state === 'missing') await expect(page.getByText(/Há uma conta vinculada sem saldo bancário disponível/)).toBeVisible();
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      if (state === 'linked') await page.screenshot({ path: info.outputPath(`dashboard-principal-${width}.png`), fullPage: true });
    }
    expect(requests.every(request => ['GET /api/auth/me', 'GET /api/reports/summary'].includes(request))).toBe(true);
  });
}
