import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AutorisationRetrait, ClientAlertes, ClientFamille, MotifRetrait } from 'api';
import { FgBanniere, FgBouton, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { SecondFacteur } from '../commun/second-facteur';
import { heure } from '../commun/temps';

const PREREGLAGES: readonly { motif: MotifRetrait; libelle: string; minutes: number }[] = [
  { motif: 'TOILETTE', libelle: $localize`:@@retrait.toilette:Toilette`, minutes: 30 },
  { motif: 'RECHARGE', libelle: $localize`:@@retrait.recharge:Recharge`, minutes: 120 },
  { motif: 'NUIT', libelle: $localize`:@@retrait.nuit:Nuit`, minutes: 600 },
];
const PROLONGATION_MIN = 30;
const DUREE_MAXIMALE_MIN = 720;
/** Dans les cinq dernières minutes, le compte à rebours passe au violet « attention », jamais à l'ambre. */
const SECONDES_D_ATTENTION = 300;

type Demande = { nature: 'autoriser' } | { nature: 'prolonger' };

/** « 30 min », « 2 h », « 2 h 15 ». */
export function dureeLisible(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const reste = minutes % 60;
  return reste === 0 ? `${minutes / 60} h` : `${Math.floor(minutes / 60)} h ${String(reste).padStart(2, '0')}`;
}

/**
 * Autorisation de retrait (écran 38, US-PAR-012) : durée de 15 minutes à 12 heures confirmée par code SMS,
 * puis compte à rebours, prolongation et remise du bracelet.
 */
@Component({
  selector: 'app-retrait',
  imports: [RouterLink, FgBanniere, FgBouton, FgIcon, FgSquelette, SecondFacteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './retrait.html',
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class Retrait {
  readonly id = input.required<string>();

  private readonly client = inject(ClientAlertes);
  private readonly famille = inject(ClientFamille);

  protected readonly prereglages = PREREGLAGES;
  protected readonly prolongation = PROLONGATION_MIN;
  protected readonly heure = heure;
  protected readonly duree = dureeLisible;

  protected readonly charge = signal(false);
  protected readonly autorisation = signal<AutorisationRetrait | null>(null);
  protected readonly prenom = signal('');
  protected readonly minutes = signal(30);
  protected readonly motif = signal<MotifRetrait>('TOILETTE');
  protected readonly demande = signal<Demande | null>(null);
  protected readonly enCours = signal(false);
  protected readonly remis = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurCode = signal<string | null>(null);
  private readonly maintenant = signal(Date.now());

  protected readonly secondesRestantes = computed(() => {
    const autorisation = this.autorisation();
    return autorisation ? Math.max(0, Math.floor((new Date(autorisation.fin).getTime() - this.maintenant()) / 1000)) : 0;
  });
  protected readonly decompte = computed(() => {
    const s = this.secondesRestantes();
    const deux = (valeur: number) => String(valeur).padStart(2, '0');
    return s >= 3600 ? `${Math.floor(s / 3600)}:${deux(Math.floor((s % 3600) / 60))}:${deux(s % 60)}` : `${deux(Math.floor(s / 60))}:${deux(s % 60)}`;
  });
  protected readonly bientotFini = computed(() => this.secondesRestantes() <= SECONDES_D_ATTENTION);
  protected readonly prolongeable = computed(() => {
    const a = this.autorisation();
    return !!a && (new Date(a.fin).getTime() - new Date(a.debut).getTime()) / 60_000 + PROLONGATION_MIN <= DUREE_MAXIMALE_MIN;
  });
  protected readonly explication = computed(() =>
    this.demande()?.nature === 'prolonger'
      ? $localize`:@@retrait.code.prolonger:Saisissez le code reçu par SMS pour prolonger le retrait de ${PROLONGATION_MIN}:minutes: minutes.`
      : $localize`:@@retrait.code.autoriser:Saisissez le code reçu par SMS pour autoriser le retrait pendant ${dureeLisible(this.minutes())}:duree:.`,
  );

  constructor() {
    effect(() => this.charger(this.id()));
    const minuterie = setInterval(() => {
      this.maintenant.set(Date.now());
      // À l'échéance, le serveur clôt la fenêtre : l'écran se remet à jour.
      if (this.autorisation() && this.secondesRestantes() === 0) {
        this.charger(this.id());
      }
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected choisir(prereglage: (typeof PREREGLAGES)[number]): void {
    this.motif.set(prereglage.motif);
    this.minutes.set(prereglage.minutes);
  }

  protected reglerDuree(evenement: Event): void {
    this.minutes.set(Number((evenement.target as HTMLInputElement).value));
    const prereglage = PREREGLAGES.find((p) => p.minutes === this.minutes());
    this.motif.set(prereglage?.motif ?? 'AUTRE');
  }

  protected demander(nature: Demande['nature']): void {
    this.erreur.set(null);
    this.erreurCode.set(null);
    this.demande.set({ nature });
  }

  protected confirmer(code: string): void {
    const demande = this.demande();
    if (!demande || this.enCours()) {
      return;
    }
    const appel: Observable<AutorisationRetrait> =
      demande.nature === 'prolonger'
        ? this.client.prolongerRetrait(this.id(), PROLONGATION_MIN, code)
        : this.client.autoriserRetrait(this.id(), this.motif(), this.minutes(), code);
    this.enCours.set(true);
    this.erreurCode.set(null);
    appel.subscribe({
      next: (autorisation) => {
        this.enCours.set(false);
        this.demande.set(null);
        this.remis.set(false);
        this.maintenant.set(Date.now());
        this.autorisation.set(autorisation);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        const lisible = erreurLisible(cause);
        if (lisible.code.startsWith('CODE_')) {
          this.erreurCode.set(lisible.message);
        } else {
          this.demande.set(null);
          this.erreur.set(lisible.message);
        }
      },
    });
  }

  protected terminer(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.terminerRetrait(this.id()).subscribe({
      next: () => {
        this.enCours.set(false);
        this.autorisation.set(null);
        this.remis.set(true);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreur.set(erreurLisible(cause).message);
        this.charger(this.id());
      },
    });
  }

  private charger(id: string): void {
    this.famille.enfant(id).subscribe({ next: (enfant) => this.prenom.set(enfant.prenom), error: () => undefined });
    this.client.retraitEnCours(id).subscribe({
      next: (autorisation) => {
        this.autorisation.set(autorisation);
        this.charge.set(true);
      },
      error: (cause: unknown) => {
        const lisible = erreurLisible(cause);
        this.autorisation.set(null);
        if (lisible.code !== 'RESSOURCE_INTROUVABLE') {
          this.erreur.set(lisible.message);
        }
        this.charge.set(true);
      },
    });
  }
}
