import { defineConfig, devices } from '@playwright/test';

const externalUrl = process.env.E2E_BASE_URL;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: externalUrl || 'http://127.0.0.1:15173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'pt-BR',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  webServer: externalUrl
    ? undefined
    : [
        {
          command: 'bash ../scripts/e2e-backend.sh',
          url: 'http://127.0.0.1:18080/actuator/health',
          reuseExistingServer: false,
          timeout: 90_000,
          gracefulShutdown: { signal: 'SIGTERM', timeout: 10_000 },
        },
        {
          command: 'npm run dev -- --port 15173',
          env: { API_PROXY_TARGET: 'http://127.0.0.1:18080' },
          url: 'http://127.0.0.1:15173',
          reuseExistingServer: false,
          timeout: 30_000,
          gracefulShutdown: { signal: 'SIGTERM', timeout: 5_000 },
        },
      ],
});
