import { test, expect } from '@playwright/test';

test('classificação exige revisão e categoria, sem requisição para alterar saldo', async ({ page }, info) => {
  let classified = false;
  const writes: string[] = [];
  await page.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    let body: unknown;
    if (request.method() !== 'GET') {
      writes.push(path);
      expect(path).toBe('/api/banking/entries/entry-test/import');
      expect(request.postDataJSON()).toMatchObject({ categoryId: 'category-test', confirmedNature: true, version: 0 });
      classified = true;
      await route.fulfill({ status: 204 }); return;
    }
    if (path === '/api/auth/me') body = { id: 'profile-test', name: 'Perfil de teste', email: 'test@example.test' };
    else if (path === '/api/banking/config') body = { ready: true, items: ['item-test'] };
    else if (path === '/api/banking/links') body = [{ id: 'link-test', name: 'Nubank fictício', accountId: 'account-test', active: true,
      importFrom: '2026-10-15', reportedBalance: '950.25', lastCheckedAt: '2026-11-12T17:00:00Z', providerUpdatedAt: '2026-11-12T16:00:00Z' }];
    else if (path === '/api/banking/links/link-test/entries') body = [{ id: 'entry-test', linkId: 'link-test', accountName: 'Nubank fictício',
      description: 'Mercado fictício', amount: '50.25', date: '2026-11-11', direction: 'DEBIT', providerStatus: 'POSTED',
      state: classified ? 'IMPORTED' : 'REVIEW', movementId: classified ? 'movement-test' : null, version: 0, candidates: [] }];
    else if (path === '/api/categories') body = [{ id: 'category-test', name: 'Alimentação', kind: 'EXPENSE', archived: false }];
    else if (path === '/api/auth/csrf') body = { headerName: 'X-CSRF-TOKEN', token: 'synthetic-csrf' };
    else throw new Error('Unexpected simulated endpoint: ' + path);
    await route.fulfill({ json: body });
  });
  await page.goto('/#/banking');
  await expect(page.getByText(/Este é o saldo principal da conta vinculada/)).toBeVisible();
  expect(writes).toHaveLength(0);
  await page.getByRole('button', { name: 'Revisar classificação', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Revisar classificação', exact: true });
  await expect(dialog.getByText(/sem descontar o valor novamente/)).toBeVisible();
  await dialog.getByRole('button', { name: 'Confirmar classificação', exact: true }).click();
  expect(writes).toHaveLength(0);
  await dialog.getByRole('combobox', { name: 'Categoria', exact: true }).click();
  await page.getByRole('option', { name: 'Alimentação', exact: true }).click();
  await dialog.getByLabel('Confirmei que esta operação é uma receita/despesa real.').check();
  await page.setViewportSize({ width: 390, height: 950 });
  await page.screenshot({ path: info.outputPath('classificacao-390.png'), fullPage: true });
  await dialog.getByRole('button', { name: 'Confirmar classificação', exact: true }).click();
  await expect(page.getByText('Classificado', { exact: true })).toBeVisible();
  await expect(page.getByText(/Saldo informado pelo provedor: R\$ 950,25/)).toBeVisible();
  expect(writes).toEqual(['/api/banking/entries/entry-test/import']);
});
