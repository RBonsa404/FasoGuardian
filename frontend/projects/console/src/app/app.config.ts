import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, LOCALE_ID, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { ESPACE_APPLICATION, intercepteurJeton } from 'api';
import { Theme } from 'ui';

import { routes } from './app.routes';

// Dates et nombres au format français.
registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // L'apparence choisie s'applique dès le démarrage et suit ensuite le système.
    provideAppInitializer(() => void inject(Theme)),
    { provide: LOCALE_ID, useValue: 'fr' },
    // Session de la console : son propre cookie, distinct de celui de l'espace parents.
    { provide: ESPACE_APPLICATION, useValue: 'console' },
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([intercepteurJeton])),
  ],
};
