import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ClientSupport, DemandeSupport, StatutDemandeSupport } from 'api';
import { FgBanniere, FgBouton, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const DATE = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

const FILES: readonly { statut: StatutDemandeSupport; libelle: string }[] = [
  { statut: 'OUVERTE', libelle: $localize`:@@tickets.ouverts:Ouverts` },
  { statut: 'EN_ATTENTE_PARENT', libelle: $localize`:@@tickets.attente:En attente du parent` },
  { statut: 'RESOLUE', libelle: $localize`:@@tickets.resolus:Résolus` },
];

/** Depuis combien de temps la demande attend, en heures puis en jours. */
export function attente(iso: string, maintenant = Date.now()): string {
  const heures = Math.max(0, Math.floor((maintenant - Date.parse(iso)) / 3_600_000));
  return heures < 48 ? $localize`:@@tickets.heures:${heures}:heures: h` : $localize`:@@tickets.jours:${Math.floor(heures / 24)}:jours: j`;
}

/**
 * Tickets support (écran 62, US-PAR-017) : files des demandes, fil de la demande choisie et réponse. L'opérateur
 * voit les coordonnées du parent ; il ne voit ni pièce d'identité, ni position, ni fiche santé.
 */
@Component({
  selector: 'app-tickets',
  imports: [ReactiveFormsModule, FgBanniere, FgBouton, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@tickets.titre">Tickets</h1>
    <div class="flex flex-wrap gap-2" role="group" i18n-aria-label="@@tickets.files" aria-label="File affichée">
      @for (file of files; track file.statut) {
        <button fg-button taille="sm" type="button" [variante]="statut() === file.statut ? 'primary' : 'secondary'" [attr.aria-pressed]="statut() === file.statut" (click)="montrer(file.statut)">{{ file.libelle }}</button>
      }
    </div>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    <div class="grid min-h-0 grid-cols-1 gap-4 lg:grid-cols-[360px_1fr]">
      <div class="flex flex-col gap-2">
        @if (demandes(); as liste) {
          @for (demande of liste; track demande.id) {
            <button type="button" class="flex flex-col gap-1 rounded-lg bg-surface p-3.5 text-left focus-visible:outline-2 focus-visible:outline-accent" [class]="choisie()?.id === demande.id ? 'border-2 border-accent' : 'border border-line'" (click)="ouvrir(demande)">
              <span class="flex items-baseline justify-between gap-3">
                <span class="font-mono text-caption text-text-2">{{ demande.reference }}</span>
                <span class="text-caption tabular-nums text-text-3">{{ attente(demande.modifieeLe) }}</span>
              </span>
              <strong class="text-label font-semibold">{{ demande.objet }}</strong>
              <span class="text-caption text-text-2">{{ demande.parent ?? parentInconnu }}</span>
            </button>
          } @empty {
            <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@tickets.vide">Aucune demande dans cette file.</p>
          }
        } @else if (!erreur()) {
          <fg-skeleton forme="carte" />
        }
      </div>

      @if (choisie(); as d) {
        <section class="flex min-h-0 flex-col gap-3 rounded-lg border border-line bg-surface p-5" [attr.aria-label]="d.reference">
          <div class="flex flex-col gap-1">
            <h2 class="m-0 text-h3 font-semibold">{{ d.reference }} · {{ d.objet }}</h2>
            <span class="text-label text-text-2" i18n="@@tickets.parent">{{ d.parent ?? parentInconnu }} · {{ d.telephone }} · ouvert le {{ date(d.ouverteLe) }}</span>
          </div>
          <ol class="m-0 flex list-none flex-col gap-2.5 p-0">
            @for (m of d.messages; track m.creeLe) {
              <li class="flex max-w-[78%] flex-col gap-1" [class]="m.duSupport ? 'self-end items-end' : 'self-start'">
                <span class="whitespace-pre-line rounded-lg px-3.5 py-3 text-label" [class]="m.duSupport ? 'bg-primary text-white' : 'border border-line bg-surface-2'">{{ m.texte }}</span>
                <span class="text-caption text-text-3">{{ m.duSupport ? support : parent }} · {{ date(m.creeLe) }}</span>
              </li>
            }
          </ol>
          <form class="mt-auto flex flex-col gap-2" (submit)="$event.preventDefault()">
            <label class="sr-only" for="reponse-ticket" i18n="@@tickets.reponse">Réponse au parent</label>
            <textarea id="reponse-ticket" class="min-h-24 rounded-md border border-line-strong bg-surface p-3 text-label outline-none placeholder:text-text-3 focus-visible:outline-2 focus-visible:outline-accent" maxlength="2000" [formControl]="reponse" i18n-placeholder="@@tickets.reponse.exemple" placeholder="Répondre…"></textarea>
            <div class="flex flex-wrap justify-end gap-2">
              <button fg-button variante="secondary" type="button" [chargement]="enCours()" (click)="repondre('RESOLUE')" i18n="@@tickets.resoudre">Répondre et résoudre</button>
              <button fg-button type="button" [chargement]="enCours()" (click)="repondre('EN_ATTENTE_PARENT')" i18n="@@tickets.envoyer">Répondre et attendre le parent</button>
            </div>
          </form>
        </section>
      } @else {
        <p class="m-0 hidden rounded-lg border border-dashed border-line p-6 text-center text-body text-text-3 lg:block" i18n="@@tickets.choisir">Choisissez une demande pour lire le fil et répondre.</p>
      }
    </div>
  `,
  host: { class: 'contents' },
})
export class Tickets {
  private readonly client = inject(ClientSupport);
  private readonly router = inject(Router);

  protected readonly files = FILES;
  protected readonly attente = attente;
  protected readonly support = $localize`:@@tickets.vous:Support`;
  protected readonly parent = $localize`:@@tickets.leParent:Parent`;
  protected readonly parentInconnu = $localize`:@@tickets.parentInconnu:Parent sans dossier approuvé`;

  protected readonly statut = signal<StatutDemandeSupport>('OUVERTE');
  protected readonly demandes = signal<readonly DemandeSupport[] | null>(null);
  protected readonly choisie = signal<DemandeSupport | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly reponse = new FormControl('', { nonNullable: true });

  constructor() {
    this.charger();
  }

  protected date(iso: string): string {
    return DATE.format(new Date(iso));
  }

  protected montrer(statut: StatutDemandeSupport): void {
    this.statut.set(statut);
    this.choisie.set(null);
    this.demandes.set(null);
    this.charger();
  }

  /** L'ouverture d'une demande est journalisée par le serveur : elle montre les coordonnées du parent. */
  protected ouvrir(demande: DemandeSupport): void {
    this.reponse.setValue('');
    this.client.demande(demande.id).subscribe({
      next: (complete) => this.choisie.set(complete),
      error: (cause: unknown) => this.signaler(cause),
    });
  }

  protected repondre(suite: 'EN_ATTENTE_PARENT' | 'RESOLUE'): void {
    const demande = this.choisie();
    const texte = this.reponse.value.trim();
    if (!demande || this.enCours()) {
      return;
    }
    if (!texte) {
      this.erreur.set($localize`:@@tickets.vide.reponse:Écrivez votre réponse avant de l'envoyer.`);
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.repondre(demande.id, texte, suite).subscribe({
      next: () => {
        this.enCours.set(false);
        this.reponse.setValue('');
        this.choisie.set(null);
        this.charger();
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.signaler(cause);
      },
    });
  }

  private charger(): void {
    this.client.demandes(this.statut()).subscribe({
      next: (demandes) => this.demandes.set(demandes),
      error: (cause: unknown) => this.signaler(cause),
    });
  }

  private signaler(cause: unknown): void {
    if (estRefus(cause)) {
      void this.router.navigate(['/refuse']);
    } else {
      this.erreur.set(erreurLisible(cause).message);
    }
  }
}
