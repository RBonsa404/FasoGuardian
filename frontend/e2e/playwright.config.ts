import { defineConfig } from '@playwright/test';

/**
 * Tests de bout en bout des parcours critiques. Ils supposent une base vierge, le serveur lancé sous le
 * profil dev avec l'adaptateur SMS bac à sable, et l'application Parents servie (voir e2e/README.md).
 */
export default defineConfig({
  testDir: '.',
  timeout: 90_000,
  // En intégration continue, un second essai distingue une lenteur passagère du runner d'une vraie régression ;
  // l'essai qui n'a réussi qu'au second passage reste signalé « flaky » dans le rapport.
  retries: process.env['CI'] ? 1 : 0,
  workers: 1,
  reporter: process.env['CI'] ? [['list'], ['github'], ['html', { outputFolder: 'rapport-e2e', open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env['FG_E2E_PARENTS'] ?? 'http://localhost:4201',
    viewport: { width: 360, height: 800 },
    locale: 'fr-FR',
    trace: 'retain-on-failure',
  },
});
