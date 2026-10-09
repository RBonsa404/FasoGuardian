import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { Alerte, ClientAlertes, ClientFamille, FicheEnfant } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { Carte, PositionSurCarte } from '../carte/fond';
import { erreurLisible } from '../commun/erreurs';
import { heure, ilYA } from '../commun/temps';
import { actionLisible, enCours, explication, iconeAlerte, statutLisible, titreComplet } from './libelles';

type Cloture = 'lever' | 'fausse';

const MOTIFS: Record<Cloture, readonly string[]> = {
  lever: [
    $localize`:@@alerte.motif.retrouve:Enfant retrouvé`,
    $localize`:@@alerte.motif.regle:Situation réglée`,
    $localize`:@@alerte.motif.medical:Urgence médicale, sangle coupée`,
  ],
  fausse: [
    $localize`:@@alerte.motif.erreur:Appui ou geste par erreur`,
    $localize`:@@alerte.motif.jeu:Jeu ou chahut`,
    $localize`:@@alerte.motif.defaut:Défaut du bracelet`,
  ],
};
const RAFRAICHISSEMENT_MS = 30_000;

/**
 * Alerte plein écran (écrans 25 et 28 ; US-ENF-001, US-ENF-002, US-PAR-010) : nature, heure, dernière position
 * connue, puis prise en charge, levée ou classement en fausse alerte avec motif. Le journal des actions est
 * affiché tel qu'enregistré.
 */
@Component({
  selector: 'app-alerte',
  imports: [ReactiveFormsModule, RouterLink, Carte, FgBadge, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './alerte.html',
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class EcranAlerte {
  readonly aid = input.required<string>();

  private readonly client = inject(ClientAlertes);
  private readonly famille = inject(ClientFamille);

  protected readonly heure = heure;
  protected readonly ilYA = ilYA;
  protected readonly action = actionLisible;
  protected readonly statut = statutLisible;

  protected readonly alerte = signal<Alerte | null>(null);
  protected readonly enfant = signal<FicheEnfant | null>(null);
  protected readonly enTraitement = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly cloture = signal<Cloture | null>(null);
  protected readonly motif = signal<string | null>(null);
  protected readonly precision = new FormControl('', { nonNullable: true });
  protected readonly erreurMotif = signal<string | null>(null);

  protected readonly enCours = computed(() => !!this.alerte() && enCours(this.alerte()!));
  protected readonly icone = computed(() => (this.alerte() ? iconeAlerte(this.alerte()!) : 'info'));
  protected readonly titre = computed(() => (this.alerte() ? titreComplet(this.alerte()!, this.enfant()?.prenom ?? $localize`:@@alerte.enfant:votre enfant`) : ''));
  protected readonly explication = computed(() => (this.alerte() ? explication(this.alerte()!) : ''));
  protected readonly motifs = computed(() => (this.cloture() ? MOTIFS[this.cloture()!] : []));
  protected readonly titreFeuille = computed(() => (this.cloture() === 'fausse' ? $localize`:@@alerte.fausse.titre:Classer en fausse alerte` : $localize`:@@alerte.lever.titre:Lever l'alerte`));
  protected readonly repere = computed((): PositionSurCarte | null => {
    const a = this.alerte();
    return a?.latitude != null && a.longitude != null
      ? { latitude: a.latitude, longitude: a.longitude, precisionM: 30, initiale: this.enfant()?.prenom.charAt(0) ?? '', attenuee: !enCours(a) }
      : null;
  });

  constructor() {
    effect(() => this.charger(this.aid()));
    // Un autre tuteur, ou le système, peut faire évoluer l'alerte pendant qu'elle est affichée.
    const minuterie = setInterval(() => {
      if (this.enCours() && !this.cloture()) {
        this.charger(this.aid());
      }
    }, RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected prendreEnCharge(): void {
    this.appeler(this.client.acquitter(this.aid()));
  }

  protected ouvrirCloture(cloture: Cloture): void {
    this.motif.set(null);
    this.precision.setValue('');
    this.erreurMotif.set(null);
    this.cloture.set(cloture);
  }

  protected confirmerCloture(): void {
    const choisi = this.motif();
    const precision = this.precision.value.trim();
    if (!choisi && !precision) {
      this.erreurMotif.set($localize`:@@alerte.motif.requis:Choisissez un motif ou décrivez ce qui s'est passé.`);
      return;
    }
    const motif = [choisi, precision].filter(Boolean).join(' · ').slice(0, 200);
    const cloture = this.cloture();
    this.cloture.set(null);
    this.appeler(cloture === 'fausse' ? this.client.classerFausseAlerte(this.aid(), motif) : this.client.lever(this.aid(), motif));
  }

  private appeler(appel: Observable<Alerte>): void {
    if (this.enTraitement()) {
      return;
    }
    this.enTraitement.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (alerte) => {
        this.enTraitement.set(false);
        this.alerte.set(alerte);
      },
      error: (cause: unknown) => {
        this.enTraitement.set(false);
        this.erreur.set(erreurLisible(cause).message);
        // L'alerte a pu changer d'état entre-temps : l'écran se remet à jour.
        this.charger(this.aid());
      },
    });
  }

  private charger(id: string): void {
    this.client.alerte(id).subscribe({
      next: (alerte) => {
        this.alerte.set(alerte);
        if (this.enfant()?.id !== alerte.enfantId) {
          this.famille.enfant(alerte.enfantId).subscribe({ next: (enfant) => this.enfant.set(enfant), error: () => undefined });
        }
      },
      error: (cause: unknown) => {
        if (!this.alerte()) {
          this.erreur.set(erreurLisible(cause).message);
        }
      },
    });
  }
}
