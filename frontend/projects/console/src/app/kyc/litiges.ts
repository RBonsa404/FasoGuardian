import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClientLitiges, Litige } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgInterrupteur, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });

/**
 * Litiges de filiation (US-KYC-001) : file des signalements instruits par les agents KYC, et ouverture d'un
 * litige sur le lien établi par un dossier approuvé. L'ouverture gèle aussitôt les réglages du tuteur contesté.
 */
@Component({
  selector: 'app-litiges',
  imports: [ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgInterrupteur, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@litiges.titre">Litiges de filiation</h1>
        <span class="text-label text-text-2" i18n="@@litiges.objectif">Décision sous 7 jours · SOS et page QR jamais suspendus</span>
      </div>
      <button fg-button type="button" (click)="ouvrirFeuille()" i18n="@@litiges.ouvrir">Ouvrir un litige</button>
    </div>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (litiges(); as liste) {
      @if (liste.length === 0) {
        <p class="m-0 rounded-lg border border-line bg-surface p-6 text-center text-body text-text-2" i18n="@@litiges.vide">Aucun litige signalé.</p>
      } @else {
        <div class="overflow-hidden rounded-lg border border-line bg-surface" role="table" i18n-aria-label="@@litiges.titre" aria-label="Litiges de filiation">
          <div class="grid h-10 grid-cols-[130px_1.6fr_130px_130px_170px] items-center bg-surface-2 px-4 text-caption font-semibold text-text-2" role="row">
            <span role="columnheader" i18n="@@litiges.col.reference">Litige</span>
            <span role="columnheader" i18n="@@litiges.col.lien">Lien contesté</span>
            <span role="columnheader" i18n="@@litiges.col.ouvert">Ouvert le</span>
            <span role="columnheader" i18n="@@litiges.col.echeance">Échéance</span>
            <span role="columnheader" i18n="@@litiges.col.statut">État</span>
          </div>
          @for (litige of liste; track litige.id) {
            <a class="grid h-11 grid-cols-[130px_1.6fr_130px_130px_170px] items-center border-t border-line px-4 text-label font-medium hover:bg-surface-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent" role="row" [routerLink]="['/kyc/litiges', litige.id]">
              <span class="font-mono text-caption" role="cell">{{ litige.reference }}</span>
              <span class="truncate" role="cell">{{ litige.tuteur }} → {{ litige.enfant }}</span>
              <span class="text-text-2" role="cell">{{ jour(litige.ouvertLe) }}</span>
              <span role="cell" [class]="enRetard(litige) ? 'font-semibold text-danger' : 'text-text-2'">{{ jour(litige.echeanceLe) }}</span>
              <span role="cell">
                @if (litige.statut === 'OUVERT') {
                  <fg-badge ton="attention" i18n="@@litiges.gele">Ouvert · compte gelé</fg-badge>
                } @else {
                  <fg-badge ton="neutre" i18n="@@litiges.clos">Clos</fg-badge>
                }
              </span>
            </a>
          }
        </div>
      }
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
    }

    <fg-sheet i18n-titre="@@litiges.ouvrir" titre="Ouvrir un litige" [ouverte]="feuille()" (fermee)="feuille.set(false)">
      <fg-input [formControl]="dossier" i18n-libelle="@@litiges.dossier" libelle="Dossier KYC du lien contesté" autocomplete="off" [longueurMax]="24" i18n-aide="@@litiges.dossier.aide" aide="Référence du dossier approuvé, par exemple KYC-000412." />
      <label class="flex flex-col gap-1.5 text-label font-semibold">
        <span i18n="@@litiges.motif">Signalement reçu</span>
        <textarea class="min-h-24 rounded-md border border-line-strong bg-surface p-3 text-label font-normal outline-none focus-visible:outline-2 focus-visible:outline-accent" [formControl]="motif" maxlength="500"></textarea>
      </label>
      <fg-switch [formControl]="suspendre" i18n-libelle="@@litiges.suspendre" libelle="Suspendre la géolocalisation">
        <strong class="text-body font-semibold" i18n="@@litiges.suspendre">Suspendre la géolocalisation</strong>
        <span class="text-label text-text-2" i18n="@@litiges.suspendre.texte">À titre conservatoire : le tuteur contesté ne voit plus la position.</span>
      </fg-switch>
      <p class="m-0 text-caption text-text-3" i18n="@@litiges.effet">Dès l'ouverture, ce tuteur ne peut plus rien modifier de ce qui concerne l'enfant. Les parties sont prévenues.</p>
      @if (erreurFeuille(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      <button fg-button type="button" [chargement]="enCours()" (click)="ouvrir()" i18n="@@litiges.confirmer">Ouvrir le litige et geler le compte</button>
      <button fg-button variante="ghost" type="button" (click)="feuille.set(false)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class Litiges {
  private readonly client = inject(ClientLitiges);
  private readonly router = inject(Router);

  protected readonly litiges = signal<readonly Litige[] | null>(null);
  protected readonly feuille = signal(false);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurFeuille = signal<string | null>(null);

  protected readonly dossier = new FormControl('', { nonNullable: true });
  protected readonly motif = new FormControl('', { nonNullable: true });
  protected readonly suspendre = new FormControl(false, { nonNullable: true });

  constructor() {
    this.client.litiges().subscribe({
      next: (litiges) => this.litiges.set(litiges),
      error: (cause: unknown) => (estRefus(cause) ? void this.router.navigate(['/refuse']) : this.erreur.set(erreurLisible(cause).message)),
    });
  }

  protected jour(iso: string): string {
    return JOUR.format(new Date(iso));
  }

  protected enRetard(litige: Litige): boolean {
    return litige.statut === 'OUVERT' && Date.parse(litige.echeanceLe) < Date.now();
  }

  protected ouvrirFeuille(): void {
    this.dossier.setValue('');
    this.motif.setValue('');
    this.suspendre.setValue(false);
    this.erreurFeuille.set(null);
    this.feuille.set(true);
  }

  protected ouvrir(): void {
    if (this.enCours()) {
      return;
    }
    if (!this.dossier.value.trim() || !this.motif.value.trim()) {
      this.erreurFeuille.set($localize`:@@litiges.incomplet:Indiquez le dossier KYC et le signalement reçu.`);
      return;
    }
    this.enCours.set(true);
    this.erreurFeuille.set(null);
    this.client.ouvrir(this.dossier.value.trim(), this.motif.value.trim(), this.suspendre.value).subscribe({
      next: (litige) => {
        this.enCours.set(false);
        this.feuille.set(false);
        void this.router.navigate(['/kyc/litiges', litige.id]);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreurFeuille.set(erreurLisible(cause).message);
      },
    });
  }
}
