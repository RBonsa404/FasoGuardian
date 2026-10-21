import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { Session } from 'api';
import { FgDecorMarque, FgIcon, NomIcone } from 'ui';

import { CopieLocale } from './commun/copie-locale';
import { entreeSansSession } from './commun/introduction';

/** Pages sans session (accueil, connexion, inscription) : sur grand écran, elles s'ouvrent à côté du décor de marque. */
const SANS_SESSION = /^\/(bienvenue|connexion|mot-de-passe-oublie|session|inscription)(\/|\?|#|$)/;

@Component({
  imports: [RouterOutlet, FgDecorMarque, FgIcon],
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);

  protected readonly panneauMarque = toSignal(
    this.router.events.pipe(
      filter((evenement) => evenement instanceof NavigationEnd),
      map(() => SANS_SESSION.test(this.router.url)),
    ),
    { initialValue: false },
  );
  protected readonly accroche = $localize`:@@marque.accroche:Votre enfant porte le bracelet. Vous gardez un œil sur lui.`;
  protected readonly points: readonly { icone: NomIcone; texte: string }[] = [
    { icone: 'position', texte: $localize`:@@marque.position:Sa position, sur la carte` },
    { icone: 'safe-zone', texte: $localize`:@@marque.zones:Ses Safe Zones : maison, école, famille` },
    { icone: 'sos', texte: $localize`:@@marque.sos:Ses alertes SOS, sur votre téléphone` },
  ];

  constructor() {
    const session = inject(Session);
    const router = this.router;
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
