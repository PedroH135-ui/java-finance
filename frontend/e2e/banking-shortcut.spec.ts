import { test, expect } from '@playwright/test';
import { today } from '../src/lib/format';

// Isolated UI test: no production session, provider calls or financial writes.
for (const ready of [true, false]) {
  test(`atalho bancário: configuração ${ready ? 'pronta' : 'pendente'}`, async ({ page }, info) => {
    const requests: string[] = [];
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname;
      requests.push(route.request().method() + ' ' + path);
      let body: unknown;
      if (path === '/api/auth/me') body = { id: 'profile-test', name: 'Perfil de teste', email: 'test@example.test' };
      else if (path === '/api/reports/summary') body = {
        month: '2026-10', today: '2026-10-14', balance: '0', accountCount: 0, records: 0,
        competence: { income: '0', expenses: '0', result: '0', savingsRate: null },
        cashFlow: '0', categories: [], evolution: [], upcoming: [],
      };
      else if (path === '/api/banking/config') body = { ready, userId: 'profile-test', items: ['item-test'], provider: 'Meu Pluggy' };
      else if (path === '/api/banking/links' || path === '/api/accounts') body = [];
      else if (path === '/api/banking/items/item-test/accounts') body = { accounts: [] };
      else throw new Error('Unexpected request: ' + path);
      await route.fulfill({ json: body });
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/#/');
    const shortcut = page.getByRole('link', { name: 'Vincular meu banco', exact: true });
    await expect(shortcut).toBeVisible();
    await expect(shortcut).toHaveAttribute('href', '#/banking?new=1');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: info.outputPath('banking-shortcut-390.png'), fullPage: true });
    await shortcut.click();
    if (ready) {
      const dialog = page.getByRole('dialog', { name: 'Vincular conta bancária', exact: true });
      await expect(dialog).toBeVisible();
      await dialog.getByRole('button', { name: 'Voltar', exact: true }).click();
      await expect(dialog).toHaveCount(0);
      await expect(page).toHaveURL(/#\/banking$/);
      await page.reload();
      await expect(dialog).toHaveCount(0);
    } else {
      await expect(page.getByText('Configuração inicial pendente', { exact: true })).toBeVisible();
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Vincular conta', exact: true })).toBeDisabled();
      expect(requests.some(r => /\/items\/|\/banking\/links/.test(r))).toBe(false);
    }
    expect(requests.every(r => r.startsWith('GET '))).toBe(true);
  });
}

test('atualização automática: status, data inicial e falha sem importação automática', async ({ page }, info) => {
  const date = today(), tomorrow = new Date(date + 'T12:00:00Z'); tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
  let failed = false;
  const requests: string[] = [];
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    requests.push(route.request().method() + ' ' + path);
    let body: unknown;
    if (path === '/api/auth/me') body = { id: 'profile-test', name: 'Perfil de teste', email: 'test@example.test' };
    else if (path === '/api/banking/config') body = { ready: true, automaticRead: true, userId: 'profile-test', items: ['item-test'], provider: 'Meu Pluggy' };
    else if (path === '/api/banking/links') body = [{ id: 'link-test', name: 'Nubank de teste', accountId: 'account-test', active: true,
      importFrom: tomorrow.toISOString().slice(0, 10), lastCheckedAt: null, providerUpdatedAt: null, reportedBalance: null,
      autoCheckedThrough: null, autoErrorAt: failed ? date + 'T12:00:00Z' : null }];
    else if (path === '/api/banking/links/link-test/entries') body = [];
    else throw new Error('Unexpected request: ' + path);
    await route.fulfill({ json: body });
  });
  await page.goto('/#/banking');
  await expect(page.getByText(/Atualização automática ativa:/)).toBeVisible();
  await expect(page.getByText(/A leitura começa em/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Consultar dados', exact: true })).toBeDisabled();
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: info.outputPath(`banking-automatic-${width}.png`), fullPage: true });
  }
  failed = true; await page.reload();
  await expect(page.getByText(/A última tentativa automática não foi concluída/)).toBeVisible();
  expect(requests.every(r => r.startsWith('GET '))).toBe(true);
});
