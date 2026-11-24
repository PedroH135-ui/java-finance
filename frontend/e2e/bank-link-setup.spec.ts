import { expect, test } from '@playwright/test';

test.use({ serviceWorkers: 'block' });
test.skip(process.env.E2E_URL !== 'http://127.0.0.1:4174', 'Fictitious local preview only');

test('creates a linked account in one request and hides existing links from the picker', async ({ page }) => {
  const requests: unknown[] = [];
  const remoteAccount = { id: 'bank-new', name: 'Conta bancária fictícia', type: 'BANK', currency: 'BRL' };
  const existingLink = { id: 'old-link', itemId: 'item', externalAccountId: 'bank-linked', accountId: 'local-linked',
    name: 'Já vinculada', importFrom: '2026-01-01', active: false, reportedBalance: null };
  await page.route('**/api/**', async route => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    if (req.method() === 'POST' && path === '/api/banking/links/new-account') {
      requests.push(req.postDataJSON());
      await route.fulfill({ status: 201 });
      return;
    }
    if (req.method() !== 'GET') throw new Error('Unexpected request: ' + req.method() + ' ' + path);
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 'demo', name: 'Demo', email: 'demo@example.test' } });
    if (path === '/api/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fictitious' } });
    if (path === '/api/banking/config') return route.fulfill({ json: { ready: true, items: ['item'] } });
    if (path === '/api/banking/links') return route.fulfill({ json: [existingLink] });
    if (path === '/api/banking/links/old-link/entries') return route.fulfill({ json: [] });
    if (path === '/api/banking/products') return route.fulfill({ json: { cards: [], positions: [], connections: [] } });
    if (path === '/api/accounts') return route.fulfill({ json: [{ id: 'local-linked', name: 'Já vinculada', kind: 'CHECKING',
      currency: 'BRL', archived: false, bankLinkId: 'old-link' }] });
    if (path === '/api/banking/items/item/accounts') return route.fulfill({ json: { accounts: [
      { ...remoteAccount, id: 'bank-linked', name: 'Já vinculada' }, remoteAccount,
    ] } });
    throw new Error('Unexpected local API: ' + path);
  });

  await page.goto('/#/banking');
  await page.getByRole('button', { name: 'Vincular conta', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Vincular conta bancária' });
  await expect(dialog.getByText(remoteAccount.name)).toBeVisible();
  await expect(dialog.getByRole('combobox')).toHaveCount(0);
  await expect(dialog.getByText(/Uma nova conta será criada/)).toBeVisible();
  await dialog.getByRole('checkbox', { name: /Conferi a conta e a data/ }).check();
  await dialog.getByRole('button', { name: 'Vincular para leitura' }).click();
  await expect(dialog).toBeHidden();
  expect(requests).toEqual([{ itemId: 'item', externalAccountId: 'bank-new',
    importFrom: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), confirmedBaseline: true }]);
});
