import { defineConfig } from '@playwright/test';

/** Peuplement de démonstration d'une pile locale fraîche (docs/tester-en-local.md) ; ce n'est pas un test. */
export default defineConfig({
  testDir: '.',
  testMatch: 'peupler.ts',
  timeout: 180_000,
  workers: 1,
  reporter: [['line']],
});
