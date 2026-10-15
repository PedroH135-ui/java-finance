import { chromium } from '@playwright/test';
import { readFile, mkdir } from 'node:fs/promises';
const svg = await readFile(new URL('../public/icons/icon.svg', import.meta.url), 'utf8');
const browser = await chromium.launch({ headless: true, executablePath: process.env.BROWSER_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
try {
  const dir = new URL('../public/icons/', import.meta.url);
  await mkdir(dir, { recursive: true });
  for (const [name, size, maskable] of [['icon-192.png', 192, false], ['icon-512.png', 512, false], ['icon-maskable-512.png', 512, true], ['apple-touch-icon.png', 180, false]]) {
    const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 });
    const artwork = maskable ? svg.replace('rx="110"', 'rx="0"') : svg;
    await page.setContent(`<html><body style="margin:0;background:#174bbd"><img width="${size}" height="${size}" src="data:image/svg+xml;base64,${Buffer.from(artwork).toString('base64')}"/></body></html>`);
    await page.locator('img').evaluate(img => img.decode());
    await page.screenshot({ path: new URL(name, dir).pathname.replace(/^\/([A-Za-z]:)/, '$1'), omitBackground: false });
    await page.close();
  }
} finally { await browser.close(); }
console.log('Ícones PNG gerados a partir do SVG local.');
