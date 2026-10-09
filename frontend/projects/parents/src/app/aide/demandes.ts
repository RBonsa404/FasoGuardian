import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClientSupport, DemandeSupport, MessageSupport, StatutDemandeSupport } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette, TonBadge } from 'ui';

import { erreurLisible } from '../commun/erreurs';

const DATE = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

export const STATUTS: Record<StatutDemandeSupport, { libelle: string; ton: TonBadge }> = {
  OUVERTE: { libelle: $localize`:@@demande.ouverte:En cours`, ton: 'accent' },
  EN_ATTENTE_PARENT: { libelle: $localize`:@@demande.attente:Votre réponse est attendue`, ton: 'attention' },
  RESOLUE: { libelle: $localize`:@@demande.resolue:Résolue`, ton: 'succes' },
};

/**
 * Mes demandes au support (US-PAR-017) : la liste, avec leur statut, et l'ouverture d'une nouvelle demande.
 * Les réponses arrivent dans le compte ; un SMS ou une notification prévient seulement qu'il y en a une.
 */
@Component({
  selector: 'app-demandes-support',
  imports: [ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/aide" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@demandes.titre">Mes demandes</h1>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (demandes(); as liste) {
      @for (demande of liste; track demande.id) {
        <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/aide/demandes', demande.id]">
          <fg-badge class="self-start" [ton]="statuts[demande.statut].ton">{{ statuts[demande.statut].libelle }} · {{ demande.reference }}</fg-badge>
          <strong class="text-body font-semibold">{{ demande.objet }}</strong>
          <span class="text-caption text-text-3" i18n="@@demandes.messages">{{ demande.messages.length }} messages · dernier le {{ date(demande.modifieeLe) }}</span>
        </a>
      } @empty {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@demandes.vide">Vous n'avez encore rien demandé au support.</p>
      }
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
    }
    <button fg-button class="mt-auto" taille="lg" type="button" (click)="ouvrirFeuille()" i18n="@@demandes.nouvelle">Écrire au support</button>

    <fg-sheet i18n-titre="@@demandes.nouvelle" titre="Écrire au support" [ouverte]="feuille()" (fermee)="feuille.set(false)">
      <fg-input [formControl]="objet" i18n-libelle="@@demandes.objet" libelle="Objet" [longueurMax]="120" />
      <label class="flex flex-col gap-1.5 text-label font-semibold">
        <span i18n="@@demandes.message">Votre message</span>
        <textarea class="min-h-28 rounded-md border border-line-strong bg-surface p-3 text-saisie font-normal outline-none focus-visible:outline-2 focus-visible:outline-accent" [formControl]="message" maxlength="2000"></textarea>
      </label>
      <p class="m-0 text-caption text-text-3" i18n="@@demandes.conseil">N'écrivez ni mot de passe ni code reçu par SMS : le support ne vous les demandera jamais.</p>
      @if (erreurFeuille(); as texte) {
        <fg-banner ton="erreur">{{ texte }}</fg-banner>
      }
      <button fg-button type="button" [chargement]="enCours()" (click)="envoyer()" i18n="@@demandes.envoyer">Envoyer</button>
      <button fg-button variante="ghost" type="button" (click)="feuille.set(false)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class DemandesSupport {
  private readonly client = inject(ClientSupport);
  private readonly router = inject(Router);

  protected readonly statuts = STATUTS;
  protected readonly demandes = signal<readonly DemandeSupport[] | null>(null);
  protected readonly feuille = signal(false);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurFeuille = signal<string | null>(null);
  protected readonly objet = new FormControl('', { nonNullable: true });
  protected readonly message = new FormControl('', { nonNullable: true });

  constructor() {
    this.client.mesDemandes().subscribe({
      next: (demandes) => this.demandes.set(demandes),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }

  protected date(iso: string): string {
    return DATE.format(new Date(iso));
  }

  protected ouvrirFeuille(): void {
    this.objet.setValue('');
    this.message.setValue('');
    this.erreurFeuille.set(null);
    this.feuille.set(true);
  }

  protected envoyer(): void {
    if (this.enCours()) {
      return;
    }
    if (!this.objet.value.trim() || !this.message.value.trim()) {
      this.erreurFeuille.set($localize`:@@demandes.incomplet:Donnez un objet et décrivez votre demande.`);
      return;
    }
    this.enCours.set(true);
    this.erreurFeuille.set(null);
    this.client.ouvrir(this.objet.value.trim(), this.message.value.trim()).subscribe({
      next: (demande) => {
        this.enCours.set(false);
        this.feuille.set(false);
        void this.router.navigate(['/aide/demandes', demande.id]);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreurFeuille.set(erreurLisible(cause).message);
      },
    });
  }
}

/** Fil d'une demande (écran 47, US-PAR-017) : statut, messages du parent et réponses du support. */
@Component({
  selector: 'app-fil-demande',
  imports: [ReactiveFormsModule, RouterLink, FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/aide/demandes" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    @if (demande(); as d) {
      <div class="flex flex-col gap-1.5">
        <fg-badge class="self-start" [ton]="statuts[d.statut].ton">{{ statuts[d.statut].libelle }} · {{ d.reference }}</fg-badge>
        <h1 class="m-0 text-h3 font-bold tracking-tight">{{ d.objet }}</h1>
      </div>
      <ol class="m-0 flex flex-1 list-none flex-col gap-2.5 p-0" aria-live="polite">
        @for (m of d.messages; track m.creeLe) {
          <li class="flex max-w-[82%] flex-col gap-1" [class]="m.duSupport ? 'self-start' : 'self-end items-end'">
            <span class="whitespace-pre-line rounded-lg px-3.5 py-3 text-body" [class]="m.duSupport ? 'border border-line bg-surface' : 'bg-primary text-white'">{{ m.texte }}</span>
            <span class="text-caption text-text-3">{{ auteur(m) }} · {{ date(m.creeLe) }}</span>
          </li>
        }
      </ol>
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      <form class="flex items-end gap-2" (submit)="$event.preventDefault(); repondre()">
        <label class="sr-only" for="reponse" i18n="@@fil.reponse">Votre réponse</label>
        <textarea id="reponse" class="min-h-12 flex-1 rounded-md border border-line-strong bg-surface p-3 text-saisie outline-none placeholder:text-text-3 focus-visible:outline-2 focus-visible:outline-accent" rows="1" maxlength="2000" [formControl]="reponse" i18n-placeholder="@@fil.reponse.exemple" placeholder="Votre réponse…"></textarea>
        <button fg-button type="submit" [chargement]="enCours()" i18n="@@demandes.envoyer">Envoyer</button>
      </form>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class FilDemande {
  readonly tid = input.required<string>();

  private readonly client = inject(ClientSupport);

  protected readonly statuts = STATUTS;
  protected readonly demande = signal<DemandeSupport | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly reponse = new FormControl('', { nonNullable: true });

  constructor() {
    effect(() => {
      this.client.maDemande(this.tid()).subscribe({
        next: (demande) => this.demande.set(demande),
        error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
      });
    });
  }

  protected date(iso: string): string {
    return DATE.format(new Date(iso));
  }

  protected auteur(message: MessageSupport): string {
    return message.duSupport ? $localize`:@@fil.support:Support` : $localize`:@@fil.vous:Vous`;
  }

  protected repondre(): void {
    const texte = this.reponse.value.trim();
    if (!texte || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.completer(this.tid(), texte).subscribe({
      next: (demande) => {
        this.enCours.set(false);
        this.reponse.setValue('');
        this.demande.set(demande);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }
}
