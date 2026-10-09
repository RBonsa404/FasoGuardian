import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { FgIcon } from 'ui';

import { CopieLocale, FicheLocale } from '../commun/copie-locale';

const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
const HEURE = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' });
const TYPES: Record<string, string> = {
  ALLERGIE: $localize`:@@session.allergie:Allergie`,
  PATHOLOGIE: $localize`:@@session.pathologie:Pathologie`,
  TRAITEMENT: $localize`:@@session.traitement:Traitement`,
  AUTRE: $localize`:@@session.autre:À savoir`,
};

/** Âge en années révolues à la date donnée. */
export function age(dateNaissance: string, aujourdhui = new Date()): number {
  const naissance = new Date(dateNaissance);
  const anniversairePasse =
    aujourdhui.getMonth() > naissance.getMonth() || (aujourdhui.getMonth() === naissance.getMonth() && aujourdhui.getDate() >= naissance.getDate());
  return aujourdhui.getFullYear() - naissance.getFullYear() - (anniversairePasse ? 0 : 1);
}

/**
 * Session expirée (écran 14, US-PAR-019) : la position en direct demande de se reconnecter, mais la fiche de
 * l'enfant gardée sur l'appareil reste lisible, même sans réseau, avec la date de sa copie.
 */
@Component({
  selector: 'app-session-expiree',
  imports: [RouterLink, FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col items-center gap-3 pt-6 text-center">
      <span class="grid size-14 place-items-center rounded-full bg-attention-soft text-attention" aria-hidden="true"><fg-icon nom="cadenas" [taille]="26" /></span>
      <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@session.titre">Votre session a expiré</h1>
      <p class="m-0 text-body text-text-2">{{ texte() }}</p>
    </div>

    @for (fiche of fiches(); track fiche.id) {
      <section class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-4" [attr.aria-label]="fiche.prenom + ' ' + fiche.nom">
        <div class="flex items-center gap-3">
          <span class="grid size-11 flex-none place-items-center rounded-full bg-surface-2 font-display text-h3 font-semibold" aria-hidden="true">{{ fiche.prenom.charAt(0) }}</span>
          <div class="flex min-w-0 flex-col">
            <strong class="truncate text-body font-semibold">{{ fiche.prenom }} {{ fiche.nom }}</strong>
            <span class="text-label text-text-2">{{ resume(fiche) }}</span>
          </div>
        </div>
        <dl class="m-0 flex flex-col">
          @if (fiche.groupeSanguin) {
            <div class="flex items-baseline justify-between gap-4 border-t border-line py-2.5"><dt class="text-label text-text-2" i18n="@@session.groupe">Groupe sanguin</dt><dd class="m-0 text-label font-semibold">{{ fiche.groupeSanguin }}</dd></div>
          }
          @for (element of fiche.critiques; track element.libelle) {
            <div class="flex items-baseline justify-between gap-4 border-t border-line py-2.5"><dt class="text-label text-text-2">{{ type(element.type) }}</dt><dd class="m-0 text-right text-label font-semibold">{{ element.libelle }}</dd></div>
          }
          @if (fiche.bracelet) {
            <div class="flex items-baseline justify-between gap-4 border-t border-line py-2.5"><dt class="text-label text-text-2" i18n="@@session.bracelet">Bracelet</dt><dd class="m-0 font-mono text-label font-semibold">{{ fiche.bracelet }}</dd></div>
          }
        </dl>
        <span class="text-caption text-text-3" i18n="@@session.copie">Copie locale du {{ copie(fiche.copieLe) }}</span>
      </section>
    }

    <div class="mt-auto flex flex-col gap-2">
      <a class="grid min-h-13 place-items-center rounded-md bg-primary text-body font-semibold text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-accent" routerLink="/connexion" i18n="@@session.reconnecter">Se reconnecter</a>
      <a class="grid min-h-12 place-items-center rounded-md border border-line-strong text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" href="tel:17" i18n="@@session.appeler">Appeler le 17</a>
    </div>
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class SessionExpiree {
  protected readonly fiches = signal<readonly FicheLocale[]>([]);

  protected readonly texte = computed(() => {
    const fiches = this.fiches();
    if (fiches.length === 0) {
      return $localize`:@@session.texte.sansCopie:Reconnectez-vous pour voir la position en direct.`;
    }
    return fiches.length === 1
      ? $localize`:@@session.texte.une:Reconnectez-vous pour voir la position en direct. La fiche de ${fiches[0].prenom}:prenom: reste consultable.`
      : $localize`:@@session.texte.plusieurs:Reconnectez-vous pour voir la position en direct. Les fiches de vos enfants restent consultables.`;
  });

  constructor() {
    void inject(CopieLocale)
      .fiches()
      .then((fiches) => this.fiches.set(fiches));
  }

  protected resume(fiche: FicheLocale): string {
    const ans = age(fiche.dateNaissance);
    const lieu = [fiche.ecole, fiche.quartier].filter(Boolean).join(', ');
    return $localize`:@@session.age:${ans}:age: ans` + (lieu ? ` · ${lieu}` : '');
  }

  protected type(code: string): string {
    return TYPES[code] ?? TYPES['AUTRE'];
  }

  protected copie(iso: string): string {
    const date = new Date(iso);
    return $localize`:@@session.copie.date:${JOUR.format(date)}:jour: à ${HEURE.format(date)}:heure:`;
  }
}
