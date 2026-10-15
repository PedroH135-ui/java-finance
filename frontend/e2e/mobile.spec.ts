import { test, expect } from '@playwright/test';
test('PWA: ícones, instalação, modo offline e APIs fora do cache', async ({ page, context }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  const manifest = await (await page.request.get('/manifest.webmanifest')).json();
  expect(manifest.display).toBe('standalone');
  expect(manifest.scope).toBe('/');
  for (const icon of manifest.icons) {
    const response = await page.request.get(icon.src);
    expect(response.ok()).toBeTruthy();
    const png = await response.body();
    expect(png.readUInt32BE(16)).toBe(Number(icon.sizes.split('x')[0]));
  }
  await page.evaluate(async () => { await navigator.serviceWorker.ready; });
  await page.reload();
  await expect.poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller))).toBe(true);
  await page.evaluate(async () => { await fetch('/api/auth/csrf', { credentials: 'include' }); });
  const keys = await page.evaluate(async () => {
    const result: string[] = [];
    for (const name of await caches.keys()) for (const request of await (await caches.open(name)).keys()) result.push(new URL(request.url).pathname);
    return result;
  });
  expect(keys).toContain('/offline.html');
  expect(keys.some(path => path.startsWith('/api/'))).toBe(false);
  await context.setOffline(true);
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Você está sem conexão' })).toBeVisible();
  await context.setOffline(false);
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible();
});
