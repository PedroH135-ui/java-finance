import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e', timeout: 120000, fullyParallel: false, workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_URL ?? 'http://127.0.0.1:8081', headless: true, actionTimeout: 15000,
    launchOptions: { executablePath: process.env.BROWSER_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe' },
    screenshot: 'only-on-failure', trace: 'retain-on-failure',
  },
});
