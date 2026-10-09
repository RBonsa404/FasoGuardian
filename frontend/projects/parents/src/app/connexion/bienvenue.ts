import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { FgBouton, FgIcon, NomIcone } from 'ui';

import { noterIntroductionVue } from '../commun/introduction';

interface Volet {
  readonly titre: string;
  readonly texte: string;
  readonly icone: NomIcone;
}

const VOLETS: readonly Volet[] = [
  {
    titre: $localize`:@@bienvenue.position.titre:Sachez où est votre enfant, sans le surveiller`,
    texte: $localize`:@@bienvenue.position.texte:Une position fraîche quand vous en avez besoin, des Safe Zones pour l'école et la maison.`,
    icone: 'position',
  },
  {
    titre: $localize`:@@bienvenue.sos.titre:Un SOS au poignet`,
    texte: $localize`:@@bienvenue.sos.texte:Votre enfant appuie sur le bouton de son bracelet : vous êtes alerté aussitôt, avec sa position.`,
    icone: 'bracelet',
  },
  {
    titre: $localize`:@@bienvenue.qr.titre:Un QR pour qu'on vous prévienne`,
    texte: $localize`:@@bienvenue.qr.texte:Qui trouve votre enfant scanne son bracelet et peut vous appeler, sans voir ni son nom ni sa position.`,
    icone: 'qr',
  },
];

/**
 * Démarrage et introduction (écran 9) : à la première ouverture sur un appareil, trois volets disent ce que
 * fait le service avant de proposer de créer un compte ou de se connecter. « Passer » y mène tout de suite.
 */
@Component({
  selector: 'app-bienvenue',
  imports: [FgBouton, FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex justify-end px-5 py-4">
      <button
        type="button"
        class="min-h-11 rounded-md px-2 text-label font-semibold text-text-2 focus-visible:outline-2 focus-visible:outline-accent"
        (click)="terminer('/connexion')"
        i18n="@@bienvenue.passer"
      >
        Passer
      </button>
    </div>
    <div
      class="mx-5 grid h-72 place-items-center rounded-xl border border-line bg-accent-soft text-accent"
      aria-hidden="true"
    >
      <fg-icon [nom]="volets[volet()].icone" [taille]="96" />
    </div>
    <div class="flex flex-col gap-2.5 px-6 pt-7" aria-live="polite">
      <img src="logo-sombre.svg" alt="FasoGuardian" class="h-6 w-auto self-start" />
      <h1 class="m-0 text-h2 font-bold tracking-tight">{{ volets[volet()].titre }}</h1>
      <p class="m-0 text-body text-text-2">{{ volets[volet()].texte }}</p>
    </div>
    <div class="mt-auto flex items-center justify-between gap-4 px-6 pt-5 pb-7">
      <div class="flex gap-1.5" role="img" [attr.aria-label]="progression()">
        @for (v of volets; track v.titre; let i = $index) {
          <span
            class="h-1.5 rounded-full"
            [class]="i === volet() ? 'w-5.5 bg-accent' : 'w-1.5 bg-line-strong'"
          ></span>
        }
      </div>
      @if (volet() < volets.length - 1) {
        <button
          fg-button
          taille="lg"
          type="button"
          (click)="volet.set(volet() + 1)"
          i18n="@@bienvenue.suivant"
        >
          Suivant
        </button>
      } @else {
        <button
          fg-button
          taille="lg"
          type="button"
          (click)="terminer('/inscription/numero')"
          i18n="@@bienvenue.creer"
        >
          Créer mon compte
        </button>
      }
    </div>
    @if (volet() === volets.length - 1) {
      <button
        type="button"
        class="-mt-3 mb-5 min-h-11 self-center rounded-md px-3 text-label font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent"
        (click)="terminer('/connexion')"
        i18n="@@bienvenue.connexion"
      >
        J'ai déjà un compte
      </button>
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col' },
})
export class Bienvenue {
  private readonly router = inject(Router);

  protected readonly volets = VOLETS;
  protected readonly volet = signal(0);

  protected progression(): string {
    return $localize`:@@bienvenue.progression:Écran ${this.volet() + 1}:numero: sur ${VOLETS.length}:total:`;
  }

  protected terminer(destination: string): void {
    noterIntroductionVue();
    void this.router.navigateByUrl(destination);
  }
}
