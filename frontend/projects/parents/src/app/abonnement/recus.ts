import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ClientAbonnements, Recu } from 'api';
import { FgBanniere, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { jourCourt, mois, montant, nomMoyen } from './libelles';

/** Reçus des paiements (écran 43, US-PAR-015) : la liste, et chaque reçu en PDF. */
@Component({
  selector: 'app-recus',
  imports: [RouterLink, FgBanniere, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/abonnement" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@recus.titre">Reçus</h1>

    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (recus(); as liste) {
      @if (liste.length > 0) {
        <ul class="m-0 flex list-none flex-col rounded-lg border border-line bg-surface p-0">
          @for (recu of liste; track recu.numero) {
            <li class="border-b border-line last:border-b-0">
              <button type="button" class="flex min-h-14 w-full items-center justify-between gap-3 p-3.5 text-left focus-visible:outline-2 focus-visible:outline-accent" [disabled]="telechargement() === recu.numero" (click)="telecharger(recu)">
                <span class="flex min-w-0 flex-col gap-0.5">
                  <strong class="text-body font-semibold">{{ mois(recu.periodeDebut) }} · {{ recu.offre }}</strong>
                  <span class="text-caption text-text-3" i18n="@@recus.paye">Payé le {{ jour(recu.emisLe) }} · {{ nomMoyen(recu.moyen) }}</span>
                  <span class="text-caption text-text-3 tabular-nums">{{ recu.numero }}</span>
                </span>
                <span class="flex flex-none items-center gap-2.5 text-text-2">
                  <span class="text-body font-semibold text-text tabular-nums">{{ montant(recu.montantFcfa) }} F</span>
                  <fg-icon nom="recu" [taille]="18" />
                  <span class="sr-only" i18n="@@recus.telecharger">Télécharger le reçu en PDF</span>
                </span>
              </button>
            </li>
          }
        </ul>
      } @else {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@recus.vide">Aucun reçu pour l'instant. Chaque paiement confirmé par l'opérateur en produit un.</p>
      }
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7' },
})
export class Recus {
  private readonly client = inject(ClientAbonnements);

  protected readonly montant = montant;
  protected readonly mois = mois;
  protected readonly nomMoyen = nomMoyen;
  protected readonly recus = signal<readonly Recu[] | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly telechargement = signal<string | null>(null);

  constructor() {
    this.client.recus().subscribe({
      next: (recus) => this.recus.set(recus),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }

  protected jour(iso: string): string {
    return jourCourt(iso);
  }

  protected telecharger(recu: Recu): void {
    if (this.telechargement()) {
      return;
    }
    this.telechargement.set(recu.numero);
    this.erreur.set(null);
    this.client.recuPdf(recu.numero).subscribe({
      next: (pdf) => {
        this.telechargement.set(null);
        const adresse = URL.createObjectURL(pdf);
        const lien = document.createElement('a');
        lien.href = adresse;
        lien.download = `${recu.numero}.pdf`;
        lien.click();
        URL.revokeObjectURL(adresse);
      },
      error: (cause: unknown) => {
        this.telechargement.set(null);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }
}
