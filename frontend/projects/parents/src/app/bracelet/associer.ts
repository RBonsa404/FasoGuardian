import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Bracelet, ClientBracelet, ClientFamille, FicheEnfant } from 'api';
import { FgBanniere, FgBouton, FgCodeAppairage, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { VisuelBracelet } from './visuel';

/**
 * Appairage (écran 36, US-PAR-014) : le parent saisit le code de la carte d'activation, distinct du QR
 * gravé. Sans enfant désigné dans l'adresse, l'écran propose d'abord de choisir l'enfant à équiper.
 */
@Component({
  selector: 'app-associer-bracelet',
  imports: [ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgCodeAppairage, FgIcon, FgSquelette, VisuelBracelet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="retour()" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <div class="flex flex-col gap-1.5">
      <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@appairage.titre">Associer le bracelet</h1>
      <p class="m-0 text-body text-text-2" i18n="@@appairage.texte">Saisissez le code d'appairage imprimé sur la carte d'activation (différent du QR gravé).</p>
    </div>

    @if (enfantChoisi(); as e) {
      @if (associe(); as bracelet) {
        <app-visuel-bracelet class="mx-auto mt-7" [numero]="bracelet.numeroSerie" />
        <fg-banner class="mt-auto" ton="succes" i18n="@@appairage.succes">{{ bracelet.numeroSerie }} associé à {{ e.prenom }}</fg-banner>
        <button fg-button taille="lg" type="button" (click)="voir(e)" i18n="@@appairage.voir">Voir le bracelet</button>
      } @else {
        <fg-pairing-code i18n-libelle="@@appairage.code" libelle="Code d'appairage" [erreur]="erreur()" [formControl]="code" (complet)="associer($event)" />
        <app-visuel-bracelet class="mx-auto mt-7" numero="FG-····" [halo]="enCours()" />
        @if (enCours()) {
          <span class="text-center text-body font-semibold text-accent" role="status" i18n="@@appairage.connexion">Connexion au bracelet…</span>
        } @else {
          <span class="text-center text-body font-semibold" i18n="@@appairage.pour">Bracelet de {{ e.prenom }}</span>
        }
        <span class="-mt-3 text-center text-label text-text-3" i18n="@@appairage.conseil">Gardez le bracelet allumé, LED bleue clignotante.</span>
      }
    } @else if (enfants(); as liste) {
      <h2 class="m-0 text-label font-semibold" i18n="@@appairage.choisir">Pour quel enfant ?</h2>
      @for (enfant of liste; track enfant.id) {
        <button type="button" class="flex min-h-14 items-center gap-3 rounded-lg border border-line bg-surface px-4 text-left text-body font-semibold focus-visible:outline-2 focus-visible:outline-accent" (click)="choisi.set(enfant)">
          {{ enfant.prenom }} {{ enfant.nom }}
        </button>
      } @empty {
        <p class="m-0 rounded-lg border border-line bg-surface p-5 text-body text-text-2" i18n="@@appairage.aucunEnfant">Aucun enfant n'est encore rattaché à votre compte : l'appairage sera possible dès que votre dossier sera validé.</p>
      }
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
    }
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class AssocierBracelet {
  /** Identifiant de l'enfant à équiper, passé en paramètre d'adresse (?enfant=…). */
  readonly enfant = input<string>();

  private readonly famille = inject(ClientFamille);
  private readonly client = inject(ClientBracelet);
  private readonly router = inject(Router);

  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly enfants = signal<FicheEnfant[] | null>(null);
  protected readonly choisi = signal<FicheEnfant | null>(null);
  protected readonly associe = signal<Bracelet | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enfantChoisi = computed(() => {
    const liste = this.enfants();
    return this.choisi() ?? liste?.find((e) => e.id === this.enfant()) ?? (liste?.length === 1 ? liste[0] : null);
  });
  protected readonly retour = computed(() => {
    const enfant = this.enfantChoisi();
    return enfant ? ['/enfants', enfant.id] : ['/enfants'];
  });

  constructor() {
    this.famille.mesEnfants().subscribe({
      next: (enfants) => this.enfants.set(enfants),
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
    // Une nouvelle saisie efface le message du code précédent.
    this.code.valueChanges.subscribe(() => this.erreur.set(null));
  }

  protected voir(enfant: FicheEnfant): void {
    void this.router.navigate(['/enfants', enfant.id, 'bracelet']);
  }

  protected associer(code: string): void {
    const enfant = this.enfantChoisi();
    if (!enfant || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.associer(enfant.id, code).subscribe({
      next: (bracelet) => {
        this.enCours.set(false);
        this.associe.set(bracelet);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        // Le champ est vidé pour une nouvelle saisie ; le message reste affiché jusqu'à la première frappe.
        this.code.setValue('', { emitEvent: false });
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }
}
