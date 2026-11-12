import { test, expect, type Page } from '@playwright/test';

type State = 'ready' | 'missing-balance' | 'bank-error' | 'empty' | 'entries-error' | 'unconfigured';
async function mockApi(page: Page, state: State) {
  const requests: string[] = [];
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname;
    requests.push(request.method() + ' ' + path + url.search);
    if (request.method() !== 'GET') { await route.abort(); return; }
    let body: unknown;
    if (path === '/api/auth/me') body = { id: 'profile-test', name: 'Perfil de teste', email: 'test@example.test' };
    else if (path === '/api/accounts') body = [
      { id: 'internal-a', name: 'Nubank de teste', institution: 'Nubank', kind: 'CHECKING', currency: 'BRL', openingDate: '2026-10-14', balance: '0.00', archived: false, version: 0 },
      { id: 'internal-b', name: 'Reserva de teste', institution: '', kind: 'SAVINGS', currency: 'BRL', openingDate: '2026-10-14', balance: '1000.00', archived: false, version: 0 },
    ];
    else if (path === '/api/categories') body = [];
    else if (path === '/api/movements') body = { items: [], total: 0, page: 0, size: 20, pages: 0 };
    else if (path === '/api/banking/config') body = { ready: state !== 'unconfigured', userId: 'profile-test', items: ['item-test'], provider: 'Meu Pluggy' };
    else if (path === '/api/banking/links') {
      if (state === 'bank-error') { await route.fulfill({ status: 503, json: { message: 'Falha simulada' } }); return; }
      // The bank name deliberately differs from the local name: association must use accountId.
      body = [{ id: 'bank-link-b', name: 'Nubank simulado', accountId: 'internal-b', active: true, importFrom: '2026-10-15',
        reportedBalance: state === 'missing-balance' ? null : '950.25', providerUpdatedAt: '2026-11-12T01:23:39Z', lastCheckedAt: '2026-11-12T17:52:13Z' }];
    } else if (path === '/api/banking/links/bank-link-b/entries') {
      if (state === 'entries-error') { await route.fulfill({ status: 503, json: { message: 'Falha simulada' } }); return; }
      body = state === 'empty' ? [] : url.searchParams.get('page') === '1' ? [{ id: 'changed', linkId: 'bank-link-b', accountName: 'Reserva de teste',
        description: 'Registro alterado fictício', amount: '20.00', date: '2026-10-19', direction: 'CREDIT', providerStatus: 'POSTED', state: 'CHANGED', movementId: 'existing-movement', candidates: [] }]
        : Array.from({ length: 50 }, (_, i) => ({ id: 'entry-' + i, linkId: 'bank-link-b', accountName: 'Reserva de teste', description: 'Registro bancário fictício ' + i,
          amount: '10.00', date: '2026-10-18', direction: 'DEBIT', providerStatus: i === 0 ? 'PENDING' : 'POSTED', state: 'REVIEW', movementId: null, candidates: [] }));
    } else { await route.abort(); return; }
    await route.fulfill({ json: body });
  });
  return requests;
}

for (const state of ['ready', 'missing-balance', 'bank-error'] as const) {
  test(`Contas: ${state}, associação por identificador e saldo interno preservado`, async ({ page }, info) => {
    const requests = await mockApi(page, state);
    await page.goto('/#/accounts');
    const local = page.getByRole('region', { name: 'Conta Nubank de teste', exact: true });
    const linked = page.getByRole('region', { name: 'Conta Reserva de teste', exact: true });
    await expect(local.getByText('R$ 0,00', { exact: true })).toBeVisible();
    await expect(linked.getByText('R$ 1.000,00', { exact: true })).toBeVisible();
    await expect(local.getByText('Saldo informado pelo banco', { exact: true })).toHaveCount(0);
    if (state === 'bank-error') await expect(page.getByText(/Não foi possível carregar os saldos bancários/)).toBeVisible();
    else {
      await expect(linked.getByText(state === 'missing-balance' ? 'Não disponível' : 'R$ 950,25', { exact: true })).toBeVisible();
      await expect(linked.getByText('Atualização no banco: 11/11/2026, 22:23:39 (Brasília)', { exact: true })).toBeVisible();
    }
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      if (state === 'ready') await page.screenshot({ path: info.outputPath(`contas-banco-${width}.png`), fullPage: true });
    }
    if (state === 'ready') {
      await linked.getByRole('link', { name: 'Ver registros para revisão' }).click();
      await expect(page.getByRole('tab', { name: 'Aguardando revisão', exact: true })).toHaveAttribute('aria-selected', 'true');
      await expect(page.getByRole('article').first()).toContainText('Registro bancário fictício 0');
      expect(requests.some(request => request.includes('/bank-link-b/entries?page=0&pending=true'))).toBe(true);
    }
    expect(requests.every(request => request.startsWith('GET '))).toBe(true);
    expect(requests.some(request => /\/items\/|\/sync|\/import|\/confirm/.test(request))).toBe(false);
  });
}

for (const state of ['ready', 'empty', 'entries-error', 'unconfigured'] as const) {
  test(`Movimentações: ${state}, pendentes separados e revisão manual preservada`, async ({ page }, info) => {
    const requests = await mockApi(page, state);
    await page.goto('/#/movements?month=2026-10&kind=EXPENSE');
    await expect(page.getByText('Nenhuma movimentação neste período', { exact: true })).toBeVisible();
    await page.getByRole('tab', { name: 'Aguardando revisão', exact: true }).click();
    await expect(page.getByText(/Não entram no saldo interno/)).toBeVisible();
    if (state === 'ready') {
      await expect(page.getByRole('article')).toHaveCount(50);
      await expect(page.getByText('Pendente no banco', { exact: true })).toBeVisible();
      for (const width of [390, 1440]) {
        await page.setViewportSize({ width, height: 1000 });
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({ path: info.outputPath(`movimentos-revisao-${width}.png`), fullPage: false });
      }
      await page.getByRole('button', { name: 'Próxima', exact: true }).click();
      await expect(page.getByText('Registro alterado fictício', { exact: true })).toBeVisible();
      await expect(page.getByText(/já possui vínculo interno/)).toBeVisible();
      await expect(page.getByRole('button', { name: 'Próxima', exact: true })).toBeDisabled();
      await expect(page.getByRole('link', { name: 'Revisar na conexão bancária' })).toHaveAttribute('href', '#/banking?link=bank-link-b');
    } else if (state === 'empty') await expect(page.getByText('Nenhum registro aguardando revisão nesta página', { exact: true })).toBeVisible();
    else if (state === 'entries-error') await expect(page.getByText('Falha simulada', { exact: true })).toBeVisible();
    else {
      await expect(page.getByText('Nenhuma conta bancária vinculada', { exact: true })).toBeVisible();
      expect(requests.some(request => request.includes('/banking/links'))).toBe(false);
    }
    await page.getByRole('tab', { name: 'Lançamentos internos', exact: true }).click();
    await expect(page.getByLabel('Mês do filtro')).toHaveValue('2026-10');
    await expect(page.getByText('Nenhuma movimentação neste período', { exact: true })).toBeVisible();
    expect(requests.every(request => request.startsWith('GET '))).toBe(true);
    expect(requests.some(request => /\/items\/|\/sync|\/import|\/confirm/.test(request))).toBe(false);
  });
}
