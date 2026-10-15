import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { today } from '../src/lib/format';

async function write(page: Page, path: string, data: unknown) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const response = await page.request.post('/api' + path, { data, headers: { [csrf.headerName]: csrf.token } });
  expect(response.ok(), await response.text()).toBeTruthy(); return response.status() === 204 ? null : response.json();
}
async function login(page: Page) {
  const email = `bank-ui-${randomUUID()}@example.test`, password = `Teste-${randomUUID()}`;
  await write(page, '/auth/register', { email, name: 'Conexão fictícia', password });
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const response = await page.request.post('/api/auth/login', { form: { username: email, password }, headers: { [csrf.headerName]: csrf.token } });
  expect(response.status()).toBe(204);
}
test('conexões: configuração ausente não ativa banco e não pede senhas', async ({ page }, info) => {
  await login(page);
  await page.goto('/#/banking');
  await expect(page.getByText('Configuração inicial pendente', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Vincular conta', exact: true })).toBeDisabled();
  await expect(page.locator('input[type=password]')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Guia oficial do Meu Pluggy' })).toHaveAttribute('href', 'https://meu.pluggy.ai/api-guide');
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('banking-setup-390.png'), fullPage: true });
});
test('conexões: interface simulada de vínculo, consulta, revisão e desativação', async ({ page }, info) => {
  // Only provider endpoints are simulated. Login, account and categories use the disposable backend.
  await login(page);
  const date = today(), opening = new Date(date + 'T12:00:00Z'); opening.setUTCDate(opening.getUTCDate() - 10);
  const account = await write(page, '/accounts', { name: 'Nubank local de teste', institution: '', kind: 'CHECKING', openingBalance: '1000', openingDate: opening.toISOString().slice(0, 10) });
  const category = await write(page, '/categories', { name: 'Alimentação teste', kind: 'EXPENSE', essential: true });
  const item = randomUUID(), external = randomUUID(), id = randomUUID(), entryId = randomUUID();
  let linked = false, active = true, consulted = false, imported = false;
  let importedCategory = '';
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/banking/**', async route => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    let body: unknown;
    if (path === '/api/banking/config') body = { ready: true, userId: randomUUID(), items: [item], provider: 'Meu Pluggy' };
    else if (path.endsWith(`/items/${item}/accounts`)) body = { accounts: [{ id: external, name: 'Nubank simulado', balance: '950.25' }] };
    else if (path === '/api/banking/links' && method === 'POST') {
      const input = route.request().postDataJSON(); expect(input.accountId).toBe(account.id); expect(input.confirmedBaseline).toBe(true); linked = true;
    } else if (path === '/api/banking/links') body = linked ? [{ id, name: 'Nubank simulado', accountId: account.id, importFrom: date, active, reportedBalance: '950.25', lastCheckedAt: consulted ? new Date().toISOString() : null, providerUpdatedAt: new Date().toISOString() }] : [];
    else if (path.endsWith('/sync')) { consulted = true; body = { read: 1 }; }
    else if (path.endsWith('/entries')) body = consulted ? [{ id: entryId, linkId: id, accountName: 'Nubank local de teste', description: 'Mercado simulado', amount: '49.75', date, direction: 'DEBIT', providerStatus: 'POSTED', state: imported ? 'IMPORTED' : 'REVIEW', movementId: imported ? randomUUID() : null, version: imported ? 1 : 0, candidates: [] }] : [];
    else if (path.endsWith('/import')) {
      const input = route.request().postDataJSON(); expect(input.confirmedNature).toBe(true); importedCategory = input.categoryId; imported = true;
    } else if (path.endsWith('/disconnect')) active = false;
    else throw new Error(`Unexpected test route: ${method} ${path}`);
    await route.fulfill(body === undefined ? { status: 204 } : { status: 200, json: body });
  });
  await page.goto('/#/banking');
  await page.getByRole('button', { name: 'Vincular conta', exact: true }).click();
  await page.getByRole('combobox', { name: 'Conta disponível no Meu Pluggy' }).click();
  await page.getByRole('option', { name: 'Nubank simulado', exact: false }).click();
  await page.getByRole('combobox', { name: 'Conta no Java Finance' }).click();
  await page.getByRole('option', { name: 'Nubank local de teste', exact: true }).click();
  await page.getByLabel('Conferi a data e o saldo de abertura para evitar duplicidade.').check();
  await page.getByRole('button', { name: 'Vincular para leitura' }).click();
  await expect(page.getByRole('heading', { name: 'Nubank simulado', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Consultar dados', exact: true }).click();
  await expect(page.getByText('Mercado simulado', { exact: true })).toBeVisible();
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: info.outputPath(`banking-review-${width}.png`), fullPage: true });
  }
  await page.getByRole('button', { name: 'Revisar importação' }).click();
  await page.getByRole('combobox', { name: 'Categoria', exact: true }).click();
  await page.getByRole('option', { name: 'Alimentação teste', exact: true }).click();
  await page.getByLabel('Confirmei que esta operação é uma receita/despesa real.').check();
  await page.getByRole('button', { name: 'Confirmar importação' }).click();
  await expect(page.getByText('Importado', { exact: true })).toBeVisible();
  expect(importedCategory).toBe(category.id);
  await page.getByRole('button', { name: 'Desativar leitura aqui' }).click();
  await expect(page.getByText('Leitura desativada aqui. Para revogar o consentimento, faça isso também no Meu Pluggy ou no banco.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Consultar dados', exact: true })).toBeDisabled();
  expect(errors).toEqual([]);
});
