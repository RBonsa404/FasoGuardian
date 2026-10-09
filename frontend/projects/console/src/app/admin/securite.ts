import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ClientConformite, EntreeAudit } from 'api';
import { FgBanniere, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const HORODATAGE = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
const JOURS_AFFICHES = 7;

/**
 * Sécurité (écran 73, US-SYS-010) : sources bloquées pour énumération de jetons QR et accès refusés par le
 * contrôle des rôles, sur les sept derniers jours. Une source n'apparaît que sous un pseudonyme : son adresse
 * n'est jamais conservée en clair.
 */
@Component({
  selector: 'app-securite',
  imports: [FgBanniere, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@securite.titre">Sécurité</h1>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (charge()) {
      <section class="flex flex-col gap-2" aria-labelledby="titre-sources">
        <h2 id="titre-sources" class="m-0 text-h3 font-semibold" i18n="@@securite.sources">Sources bloquées</h2>
        <span class="text-label text-text-2" i18n="@@securite.sources.texte">Tentatives d'énumération de QR : plus de 20 jetons invalides en une minute depuis une même source. La source est bloquée temporairement.</span>
        @if (sources().length === 0) {
          <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@securite.sources.vide">Aucune tentative d'énumération sur les sept derniers jours.</p>
        } @else {
          <ul class="m-0 flex list-none flex-col rounded-lg border border-danger bg-surface p-0">
            @for (entree of sources(); track entree.id) {
              <li class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3 text-label last:border-b-0">
                <span class="font-mono text-caption" i18n="@@securite.source">Source {{ pseudonyme(entree) }}</span>
                <span class="text-text-2 tabular-nums" i18n="@@securite.bloquee">bloquée le {{ horodatage(entree.horodatage) }}</span>
              </li>
            }
          </ul>
        }
      </section>

      <section class="flex flex-col gap-2" aria-labelledby="titre-refus">
        <h2 id="titre-refus" class="m-0 text-h3 font-semibold" i18n="@@securite.refus">Accès refusés</h2>
        <span class="text-label text-text-2" i18n="@@securite.refus.texte">Tentatives d'accès hors du périmètre d'un rôle. Chacune est journalisée.</span>
        @if (refus().length === 0) {
          <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@securite.refus.vide">Aucun accès refusé sur les sept derniers jours.</p>
        } @else {
          <div class="overflow-hidden rounded-lg border border-line bg-surface" role="table" i18n-aria-label="@@securite.refus" aria-label="Accès refusés">
            <div class="grid h-10 grid-cols-[150px_220px_1fr] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
              <span role="columnheader" i18n="@@audit.col.horodatage">Horodatage</span>
              <span role="columnheader" i18n="@@audit.col.agent">Agent</span>
              <span role="columnheader" i18n="@@securite.col.route">Route demandée</span>
            </div>
            @for (entree of refus(); track entree.id) {
              <div class="grid min-h-11 grid-cols-[150px_220px_1fr] items-center border-t border-line px-4 text-label" role="row">
                <span class="tabular-nums text-text-2" role="cell">{{ horodatage(entree.horodatage) }}</span>
                <span class="truncate" role="cell">{{ entree.role }}{{ entree.acteurId ? ' · ' + entree.acteurId.slice(0, 8) : '' }}</span>
                <span class="truncate font-mono text-caption" role="cell">{{ entree.cibleId }}</span>
              </div>
            }
          </div>
        }
      </section>
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'contents' },
})
export class Securite {
  private readonly client = inject(ClientConformite);
  private readonly router = inject(Router);

  protected readonly charge = signal(false);
  protected readonly sources = signal<readonly EntreeAudit[]>([]);
  protected readonly refus = signal<readonly EntreeAudit[]>([]);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    const depuis = new Date(Date.now() - JOURS_AFFICHES * 86_400_000).toISOString();
    forkJoin({
      sources: this.client.journal({ action: 'ENUMERATION_QR', depuis }, 0, 50),
      refus: this.client.journal({ action: 'ACCES_REFUSE', depuis }, 0, 50),
    }).subscribe({
      next: ({ sources, refus }) => {
        this.sources.set(sources.entrees);
        this.refus.set(refus.entrees);
        this.charge.set(true);
      },
      error: (cause: unknown) => (estRefus(cause) ? void this.router.navigate(['/refuse']) : this.erreur.set(erreurLisible(cause).message)),
    });
  }

  protected horodatage(iso: string): string {
    return HORODATAGE.format(new Date(iso));
  }

  /** Début du pseudonyme de la source : assez pour rapprocher deux blocages, pas pour retrouver une adresse. */
  protected pseudonyme(entree: EntreeAudit): string {
    return (entree.cibleId ?? '').slice(0, 12);
  }
}
