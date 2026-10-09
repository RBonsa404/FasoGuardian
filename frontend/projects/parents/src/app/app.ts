import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';

import { Session } from 'api';

import { CopieLocale } from './commun/copie-locale';
import { entreeSansSession } from './commun/introduction';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  constructor() {
    const session = inject(Session);
    const router = inject(Router);
    const copie = inject(CopieLocale);
    // Session expirée ou révoquée en cours d'usage (US-PAR-019) : le parent est conduit à se reconnecter, et
    // retrouve en attendant la fiche de son enfant gardée sur l'appareil, s'il y en a une.
    effect(() => {
      if (session.reauthentificationRequise()) {
        void copie
          .fiches()
          .then((fiches) =>
            router.navigate([fiches.length > 0 ? '/session' : entreeSansSession()]),
          );
      }
    });
  }
}
