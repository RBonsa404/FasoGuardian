import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, LOCALE_ID, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';

import { intercepteurJeton } from 'api';
import { Theme } from 'ui';

import { routes } from './app.routes';
import { intercepteurEtatReseau } from './commun/etat-reseau';
import { intercepteurHorsLigne } from './commun/file-hors-ligne';
import { intercepteurLecturesLocales } from './commun/lectures-locales';

// Dates et nombres au format français.
registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // L'apparence choisie s'applique dès le démarrage et suit ensuite le système.
    provideAppInitializer(() => void inject(Theme)),
    { provide: LOCALE_ID, useValue: 'fr' },
    // Fondu court d'un écran à l'autre (mouvement.css) ; « réduire les animations » le coupe.
    provideRouter(routes, withComponentInputBinding(), withViewTransitions({ skipInitialTransition: true })),
    // Lectures gardées et file hors ligne enveloppent le jeton : une action gardée repart avec un jeton frais.
    provideHttpClient(withInterceptors([intercepteurEtatReseau, intercepteurLecturesLocales, intercepteurHorsLigne, intercepteurJeton])),
  ],
};
