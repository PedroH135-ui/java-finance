import { test, expect, type Page } from '@playwright/test';

type State = 'ready' | 'missing-balance' | 'disabled' | 'empty' | 'entries-error';
async function mockApi(page: Page, state: State) {
  const requests: string[] = [];
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname;
    requests.push(request.method() + ' ' + path + url.search);
    if (request.method() !== 'GET') { await route.abort(); return; }
    let body: unknown;
    if (path === '/api/auth/me') body = { id: 'profile-test', name: 'Perfil de teste', email: 'test@example.test' };
    else if (path === '/api/accounts') body = [
      { id: 'manual-a', name: 'Carteira de teste', institution: '', kind: 'WALLET', currency: 'BRL', openingDate: '2026-10-14', balance: '100.00', balanceSource: 'CALCULATED', archived: false, version: 0 },
      { id: 'bank-b', name: 'Nubank de teste', institution: 'Nubank', kind: 'CHECKING', currency: 'BRL', openingDate: '2026-10-14', balance: state === 'missing-balance' ? null : '950.25', balanceSource: 'BANK', bankLinkId: 'bank-link-b', bankReadActive: state !== 'disabled', bankUpdatedAt: '2026-11-12T01:23:39Z', bankCheckedAt: '2026-11-12T17:52:13Z', archived: false, version: 0 },
    ];
    else if (path === '/api/categories') body = [];
    else if (path === '/api/movements/feed') {
      if (state === 'entries-error') { await route.fulfill({ status: 503, json: { message: 'Falha simulada' } }); return; }
      const currentPage = Number(url.searchParams.get('page') ?? '0');
      body = { total: state === 'empty' ? 0 : 22, pages: state === 'empty' ? 0 : 2, page: currentPage, size: 20,
        items: state === 'empty' ? [] : Array.from({ length: currentPage === 0 ? 20 : 2 }, (_, i) => ({
          id: 'record-' + (currentPage * 20 + i), accountId: 'bank-b', description: 'Registro bancário fictício ' + (currentPage * 20 + i),
          amount: '10.00', operationDate: '2026-11-11', competence: '2026-11-01', dueDate: '2026-11-11',
          kind: 'EXPENSE', status: i === 0 && currentPage === 0 ? 'PLANNED' : 'CONFIRMED', source: 'BANK', categoryId: null,
          bankEntryId: 'entry-' + i, bankLinkId: 'bank-link-b', bankEntryVersion: 0,
          bankProviderStatus: i === 0 && currentPage === 0 ? 'PENDING' : 'POSTED', hidden: false,
        })) };
    } else throw new Error('Unexpected request: ' + path);
    await route.fulfill({ json: body });
  });
  return requests;
}

for (const state of ['ready', 'missing-balance', 'disabled'] as const) {
  test(`Contas: ${state}, saldo principal preservado e acesso direto às movimentações`, async ({ page }, info) => {
    const requests = await mockApi(page, state);
    await page.goto('/#/accounts');
    const manual = page.getByRole('region', { name: 'Conta Carteira de teste', exact: true });
    const bank = page.getByRole('region', { name: 'Conta Nubank de teste', exact: true });
    await expect(manual.getByText('R$ 100,00', { exact: true })).toBeVisible();
    await expect(bank.getByText(state === 'missing-balance' ? 'Saldo não disponível' : 'R$ 950,25', { exact: true })).toBeVisible();
    await expect(bank.getByText('Saldo do banco', { exact: true })).toBeVisible();
    await expect(bank.getByText('Atualização no banco: 11/11/2026, 22:23:39 (Brasília)', { exact: true })).toBeVisible();
    if (state === 'disabled') await expect(bank.getByText(/Leitura desativada/)).toBeVisible();
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      if (state === 'ready') await page.screenshot({ path: info.outputPath(`contas-direto-${width}.png`), fullPage: true });
    }
    if (state === 'ready') {
      await bank.getByRole('link', { name: 'Ver movimentações', exact: true }).click();
      await expect(page.getByRole('tab', { name: 'Todas as movimentações', exact: true })).toHaveAttribute('aria-selected', 'true');
      await expect(page.getByRole('table', { name: 'Movimentações', exact: true }).getByText('Registro bancário fictício 0', { exact: true })).toBeVisible();
      expect(requests.some(request => request.includes('/movements/feed?') && request.includes('account=bank-b'))).toBe(true);
    }
    expect(requests.every(request => request.startsWith('GET '))).toBe(true);
    expect(requests.some(request => /\/items\/|\/sync|\/import|\/confirm/.test(request))).toBe(false);
  });
}

for (const state of ['ready', 'empty', 'entries-error'] as const) {
  test(`Movimentações: ${state}, registros aparecem sem aprovação e com paginação`, async ({ page }, info) => {
    const requests = await mockApi(page, state);
    await page.goto('/#/movements?month=2026-11');
    await expect(page.getByRole('tab', { name: 'Aguardando revisão', exact: true })).toHaveCount(0);
    await expect(page.getByText(/Registros efetivados do banco entram automaticamente/)).toBeVisible();
    if (state === 'ready') {
      const table = page.getByRole('table', { name: 'Movimentações', exact: true });
      await expect(table.getByText('Registro bancário fictício 0', { exact: true })).toBeVisible();
      await expect(table.getByText('Pendente no banco', { exact: true })).toBeVisible();
      for (const width of [390, 1440]) {
        await page.setViewportSize({ width, height: 1000 });
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({ path: info.outputPath(`movimentos-direto-${width}.png`), fullPage: false });
      }
      await page.getByRole('button', { name: 'Próxima', exact: true }).click();
      await expect(table.getByText('Registro bancário fictício 20', { exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Próxima', exact: true })).toBeDisabled();
    } else if (state === 'empty') await expect(page.getByText('Nenhuma movimentação neste período', { exact: true })).toBeVisible();
    else await expect(page.getByText('Falha simulada', { exact: true })).toBeVisible();
    expect(requests.every(request => request.startsWith('GET '))).toBe(true);
    expect(requests.some(request => /banking|\/import|\/confirm/.test(request))).toBe(false);
  });
}
