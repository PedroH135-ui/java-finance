import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

async function select(page: Page, label: string, option: string) {
  await page.getByRole('dialog').last().getByRole('combobox', { name: label, exact: false }).click();
  await page.getByRole('option', { name: option, exact: true }).click();
}
async function post(page: Page, path: string, data: unknown) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const response = await page.request.post('/api' + path, { data, headers: { [csrf.headerName]: csrf.token } });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

test('fundação: cadastro, contas, receitas, transferência, cancelamento e telas responsivas', async ({ page }, info) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  const email = `qa-${randomUUID()}@example.test`;
  const password = `Teste-local-${randomUUID()}`;
  await page.goto('/');
  await page.getByRole('button', { name: 'Criar minha conta' }).click();
  await page.getByLabel('Seu nome').fill('Validação · dados fictícios');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha', { exact: false }).fill(password);
  await page.getByRole('button', { name: 'Criar conta', exact: true }).click();
  await expect(page.getByText('Conta criada. Entre com seu e-mail e senha.')).toBeVisible();
  await page.getByLabel('Senha', { exact: false }).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('main').getByRole('heading', { level: 1 })).toBeVisible();
  const cookie = (await page.context().cookies()).find(c => c.name === 'JSESSIONID');
  expect(cookie?.httpOnly).toBe(true);
  expect(cookie?.sameSite).toBe('Strict');
  await page.screenshot({ path: info.outputPath('dashboard-vazio.png'), fullPage: true });

  for (const [name, balance] of [['Conta principal', '1000'], ['Reserva', '0']]) {
    await page.goto('/#/accounts');
    await page.getByRole('button', { name: 'Nova conta', exact: true }).click();
    await page.getByLabel('Nome da conta').fill(name);
    await page.getByRole('textbox', { name: 'Saldo inicial *', exact: true }).fill(balance);
    await page.getByRole('button', { name: 'Salvar conta', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  }
  await page.goto('/#/categories');
  await page.getByRole('button', { name: 'Nova categoria', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Nome', { exact: false }).fill('Salário');
  await select(page, 'Natureza', 'Receita');
  await page.getByRole('button', { name: 'Salvar categoria' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);

  await page.goto('/#/movements');
  await page.getByRole('button', { name: 'Nova movimentação', exact: true }).click();
  await select(page, 'Natureza', 'Receita');
  await page.getByRole('dialog').getByLabel('Descrição', { exact: false }).fill('Salário de validação');
  await page.getByLabel('Valor', { exact: false }).fill('6000');
  await select(page, 'Conta', 'Conta principal');
  await select(page, 'Categoria', 'Salário');
  await page.getByRole('button', { name: 'Salvar movimentação' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByText('Salário de validação', { exact: true }).first()).toBeVisible();

  const accounts = await (await page.request.get('/api/accounts')).json();
  const source = accounts.find((a: { name: string }) => a.name === 'Conta principal');
  const destination = accounts.find((a: { name: string }) => a.name === 'Reserva');
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
  const transfer = await post(page, '/movements', { requestKey: randomUUID(), description: 'Transferência de validação', amount: '300.00',
    kind: 'TRANSFER', status: 'CONFIRMED', accountId: source.id, destinationId: destination.id, categoryId: null,
    operationDate: date, competence: date.slice(0, 7) + '-01', dueDate: date, settledDate: date, notes: '' });
  const balances = await (await page.request.get('/api/accounts')).json();
  expect(balances.find((a: { id: string }) => a.id === source.id).balance).toBe('6700.00');
  expect(balances.find((a: { id: string }) => a.id === destination.id).balance).toBe('300.00');
  await page.goto('/#/movements?detail=' + transfer.id);
  // Open through the rendered row if the detail URL naming changes.
  if (!await page.getByRole('dialog').count()) await page.getByText('Transferência de validação', { exact: true }).first().click();
  await page.getByRole('button', { name: 'Cancelar movimentação', exact: true }).click();
  await page.getByLabel('Motivo para o histórico').fill('Cancelamento de validação, preservando o histórico.');
  await page.getByRole('button', { name: 'Confirmar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(1);
  await expect(page.getByRole('dialog').getByText('Cancelado', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Fechar', exact: true }).click();
  const after = await (await page.request.get('/api/accounts')).json();
  expect(after.find((a: { id: string }) => a.id === source.id).balance).toBe('7000.00');
  expect(after.find((a: { id: string }) => a.id === destination.id).balance).toBe('0.00');

  for (const width of [360, 390, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 950 });
    for (const route of ['/', '/movements', '/accounts', '/categories', '/settings']) {
      await page.goto('/#' + route);
      await expect(page.getByRole('main').getByRole('heading', { level: 1 })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${route} @ ${width}`).toBeTruthy();
      if (route === '/' || route === '/movements') await page.screenshot({ path: info.outputPath(`${route === '/' ? 'dashboard' : 'movements'}-${width}.png`), fullPage: true });
    }
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/#/movements');
  await page.getByRole('button', { name: 'Nova movimentação', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Descrição', { exact: false }).fill('Rascunho não salvo');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('heading', { name: 'Descartar alterações?' })).toBeVisible();
  await page.getByRole('button', { name: 'Continuar editando' }).click();
  await expect(page.getByRole('dialog').getByLabel('Descrição', { exact: false })).toHaveValue('Rascunho não salvo');
  await page.screenshot({ path: info.outputPath('formulario-390.png'), fullPage: true });
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Descartar', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.keyboard.press('Tab');
  expect(await page.evaluate(() => document.activeElement?.tagName)).not.toBe('BODY');
  await page.goto('/#/settings');
  await page.getByRole('button', { name: 'Como instalar no celular' }).click();
  await expect(page.getByRole('heading', { name: 'Instalar no celular' })).toBeVisible();
  await page.getByRole('button', { name: 'Entendi', exact: true }).click();
  await page.getByLabel('Seu nome').fill('Perfil fictício atualizado');
  await page.getByRole('button', { name: 'Salvar perfil' }).click();
  await expect(page.getByText('Perfil atualizado.', { exact: true })).toBeVisible();
  const newPassword = `Nova-senha-${randomUUID()}`;
  await page.getByLabel(/^Senha atual/).fill(password);
  await page.getByLabel(/^Nova senha/).fill(newPassword);
  await page.getByLabel(/^Confirme a nova senha/).fill(newPassword);
  await page.getByRole('button', { name: 'Alterar senha e sair' }).click();
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible();
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel(/^Senha/).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByText('E-mail ou senha inválidos.', { exact: true })).toBeVisible();
  await page.getByLabel(/^Senha/).fill(newPassword);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('main').getByRole('heading', { level: 1 })).toBeVisible();
  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});
