import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ClientAuthentification, ClientFds, ConstatSignalement } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

/**
 * Espace des forces de sécurité (écran 75, US-FDS-001). L'agent saisit la référence portée par le dossier,
 * voit le signalement et en accuse réception. Aucune liste : sans référence, rien ne s'affiche. Le dossier
 * n'est proposé que s'il a été transmis par la passerelle convenue.
 */
@Component({
  selector: 'app-signalement-fds',
  imports: [DatePipe, ReactiveFormsModule, FgBadge, FgBanniere, FgBouton, FgChamp],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@fds.titre">Espace forces de sécurité</h1>
      <span class="text-caption font-medium text-text-2" i18n="@@fds.lectureSeule">Lecture seule</span>
    </div>
    <form class="flex max-w-xl flex-wrap items-end gap-3" (submit)="$event.preventDefault(); chercher()">
      <fg-input class="min-w-60 flex-1" [formControl]="reference" i18n-libelle="@@fds.reference" libelle="Référence du signalement" i18n-aide="@@fds.reference.aide" aide="Elle figure en tête du dossier, sous la forme FG-SIG-000412." [longueurMax]="20" />
      <button fg-button variante="secondary" type="submit" [chargement]="recherche()" i18n="@@fds.chercher">Retrouver</button>
    </form>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (constat(); as c) {
      <section class="flex max-w-xl flex-col gap-3.5 rounded-lg border border-line bg-surface p-6" aria-labelledby="titre-signalement">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 id="titre-signalement" class="m-0 text-h3 font-semibold">{{ c.reference }}</h2>
          <fg-badge ton="accent"><ng-container i18n="@@fds.recu">Reçu</ng-container> {{ c.etabliLe | date: 'HH:mm' }}</fg-badge>
        </div>
        <dl class="m-0 flex flex-col divide-y divide-line">
          <div class="flex justify-between gap-4 py-2.5">
            <dt class="text-label text-text-2" i18n="@@fds.nature">Nature</dt>
            <dd class="m-0 text-right text-label font-semibold">{{ c.nature }}</dd>
          </div>
          <div class="flex justify-between gap-4 py-2.5">
            <dt class="text-label text-text-2" i18n="@@fds.etabli">Établi le</dt>
            <dd class="m-0 text-right text-label font-semibold tabular-nums">{{ c.etabliLe | date: "d MMM y 'à' HH:mm" }}</dd>
          </div>
          <div class="flex justify-between gap-4 py-2.5">
            <dt class="text-label text-text-2" i18n="@@fds.dossier">Dossier</dt>
            <dd class="m-0 text-right text-label font-semibold">{{ c.dossierConsultable ? transmis : remis }}</dd>
          </div>
        </dl>
        @if (c.dossierConsultable) {
          <button fg-button variante="secondary" type="button" [chargement]="telechargement()" (click)="ouvrirDossier(c)" i18n="@@fds.ouvrir">Ouvrir le dossier (PDF)</button>
        }
        @if (c.accuseLe) {
          <fg-banner ton="succes"><ng-container i18n="@@fds.accuse">Réception accusée le</ng-container> {{ c.accuseLe | date: "d MMM y 'à' HH:mm" }}. <ng-container i18n="@@fds.accuse.parents">Les parents en ont été informés.</ng-container></fg-banner>
        } @else {
          <button fg-button taille="lg" type="button" [chargement]="enCours()" (click)="accuser(c)"><ng-container i18n="@@fds.accuser">Accuser réception</ng-container>@if (agent(); as a) { · <ng-container i18n="@@fds.agent">agent</ng-container> {{ a }}}</button>
        }
        <p class="m-0 text-caption text-text-3" i18n="@@fds.trace">Accès limité à 30 jours · chaque consultation est tracée.</p>
      </section>
    }
  `,
  host: { class: 'contents' },
})
export class SignalementFds {
  private readonly client = inject(ClientFds);
  private readonly router = inject(Router);

  protected readonly reference = new FormControl('', { nonNullable: true });
  protected readonly constat = signal<ConstatSignalement | null>(null);
  protected readonly agent = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly recherche = signal(false);
  protected readonly enCours = signal(false);
  protected readonly telechargement = signal(false);
  protected readonly transmis = $localize`:@@fds.dossier.transmis:Transmis par la passerelle`;
  protected readonly remis = $localize`:@@fds.dossier.remis:Remis en main propre par le parent`;

  constructor() {
    inject(ClientAuthentification)
      .moi()
      .subscribe({ next: (compte) => this.agent.set(compte.identifiant ?? null), error: () => undefined });
  }

  protected chercher(): void {
    const reference = this.reference.value.trim();
    this.erreur.set(null);
    this.constat.set(null);
    if (!reference) {
      this.erreur.set($localize`:@@fds.reference.requise:Saisissez la référence du signalement.`);
      return;
    }
    this.recherche.set(true);
    this.client.constat(reference).subscribe({
      next: (constat) => {
        this.recherche.set(false);
        this.constat.set(constat);
      },
      error: (erreur: unknown) => this.echec(erreur),
    });
  }

  protected accuser(constat: ConstatSignalement): void {
    this.erreur.set(null);
    this.enCours.set(true);
    this.client.accuser(constat.reference).subscribe({
      next: (maj) => {
        this.enCours.set(false);
        this.constat.set(maj);
      },
      error: (erreur: unknown) => this.echec(erreur),
    });
  }

  protected ouvrirDossier(constat: ConstatSignalement): void {
    this.erreur.set(null);
    this.telechargement.set(true);
    this.client.dossier(constat.reference).subscribe({
      next: (pdf) => {
        this.telechargement.set(false);
        const adresse = URL.createObjectURL(pdf);
        const lien = document.createElement('a');
        lien.href = adresse;
        lien.download = `${constat.reference}.pdf`;
        lien.click();
        URL.revokeObjectURL(adresse);
      },
      error: (erreur: unknown) => this.echec(erreur),
    });
  }

  private echec(erreur: unknown): void {
    this.recherche.set(false);
    this.enCours.set(false);
    this.telechargement.set(false);
    if (estRefus(erreur)) {
      void this.router.navigate(['/refuse'], { queryParams: { role: 'FDS' } });
      return;
    }
    this.erreur.set(erreurLisible(erreur).message);
  }
}
