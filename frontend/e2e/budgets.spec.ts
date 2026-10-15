import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

async function write(page: Page, path: string, data: unknown, method = 'POST') {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const response = await page.request.fetch('/api' + path, { method, data, headers: { [csrf.headerName]: csrf.token } });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.status() === 204 ? null : response.json();
}
test('orçamentos: editar, copiar sem sobrescrever, competência e responsividade', async ({ page }, info) => {
  const email = `budgets-${randomUUID()}@example.test`, password = `Teste-${randomUUID()}`;
  await write(page, '/auth/register', { email, name: 'Orçamento fictício', password });
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const login = await page.request.post('/api/auth/login', { form: { username: email, password }, headers: { [csrf.headerName]: csrf.token } });
  expect(login.status()).toBe(204);
  const account = await write(page, '/accounts', { name: 'Conta de teste', institution: '', kind: 'CHECKING', openingBalance: '1000', openingDate: '2026-01-01' });
  const food = await write(page, '/categories', { name: 'Alimentação', kind: 'EXPENSE', essential: true });
  const transport = await write(page, '/categories', { name: 'Transporte', kind: 'EXPENSE', essential: false });
  await write(page, `/budgets/2026-09/categories/${food.id}`, { amount: '100', version: null }, 'PUT');
  await write(page, `/budgets/2026-09/categories/${transport.id}`, { amount: '80', version: null }, 'PUT');
  await write(page, `/budgets/2026-10/categories/${food.id}`, { amount: '50', version: null }, 'PUT');
  for (const [status, amount] of [['CONFIRMED', '30.25'], ['PLANNED', '70']]) {
    await write(page, '/movements', { requestKey: randomUUID(), description: 'Alimentação de teste', amount, kind: 'EXPENSE', status,
      accountId: account.id, categoryId: food.id, operationDate: '2026-10-12', competence: '2026-10-01', dueDate: '2026-10-12', settledDate: status === 'CONFIRMED' ? '2026-10-12' : null, notes: '' });
  }
  await page.goto('/#/budgets?month=2026-10');
  const foodCard = page.getByRole('region', { name: 'Alimentação', exact: true });
  await expect(foodCard.getByText('R$ 19,75', { exact: true })).toBeVisible();
  await expect(foodCard.getByText('R$ 70,00', { exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Transporte' }).getByText('Sem limite', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Copiar mês anterior' }).click();
  await page.getByRole('button', { name: 'Confirmar cópia' }).click();
  await expect(page.getByText('1 limite(s) copiado(s). 1 ignorado(s) por já existir ou estar arquivado.')).toBeVisible();
  await expect(foodCard.getByText('R$ 50,00', { exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Transporte' }).getByText('R$ 80,00', { exact: true }).first()).toBeVisible();
  await foodCard.getByRole('button', { name: 'Editar limite' }).click();
  await page.getByLabel('Limite mensal').fill('20');
  await page.getByRole('button', { name: 'Salvar limite' }).click();
  await expect(foodCard.getByText('Limite ultrapassado', { exact: true })).toBeVisible();
  await expect(foodCard.getByText('-R$ 10,25', { exact: true })).toBeVisible();
  await page.reload();
  await expect(foodCard.getByText('-R$ 10,25', { exact: true })).toBeVisible();
  for (const width of [360, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: info.outputPath(`budgets-${width}.png`), fullPage: true });
  }
  await foodCard.getByRole('link', { name: 'Ver movimentações' }).click();
  await expect(page.getByLabel('Categoria', { exact: true })).toContainText('Alimentação');
  await expect(page.getByLabel('Filtrar pela data de')).toContainText('Competência');
  await page.goto('/#/budgets?month=2026-10');
  await foodCard.getByRole('button', { name: 'Editar limite' }).click();
  await page.getByLabel('Limite mensal').fill('0');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('heading', { name: 'Descartar alterações?' })).toBeVisible();
  await page.getByRole('button', { name: 'Continuar editando' }).click();
  await page.getByRole('button', { name: 'Salvar limite' }).click();
  await expect(foodCard.getByText('Limite zero: nenhum gasto permitido neste planejamento.')).toBeVisible();
});
