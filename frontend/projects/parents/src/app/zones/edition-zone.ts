import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { CategorieZone, ClientBracelet, ClientZones, FormeZone, PointGeo, SafeZone, SaisieZone } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgIcon, FgInterrupteur } from 'ui';

import { Brouillon, Carte } from '../carte/fond';
import { erreurLisible } from '../commun/erreurs';
import { SecondFacteur } from '../commun/second-facteur';
import { CATEGORIES, JOURS, distance } from './libelles';

const TOLERANCES_MIN = [0, 5, 10, 15, 30] as const;
const SOMMETS_MAXIMUM = 20;
const CHOIX = 'grid h-11 flex-1 cursor-pointer place-items-center rounded-md text-label font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-accent ';
const CHOISI = 'border-2 border-accent bg-accent-soft';
const NON_CHOISI = 'border border-line-strong text-text-2';

/**
 * Création et modification d'une Safe Zone (écran 22, US-PAR-007) : forme tracée sur la carte, jours, heures
 * et délai de tolérance. L'enregistrement est confirmé par code SMS.
 */
@Component({
  selector: 'app-edition-zone',
  imports: [ReactiveFormsModule, RouterLink, Carte, FgBanniere, FgBouton, FgChamp, FgIcon, FgInterrupteur, SecondFacteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './edition-zone.html',
  // Grand écran : le formulaire à gauche, la carte où tracer la zone sur tout le reste de la fenêtre.
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col lg:mx-0 lg:h-dvh lg:max-w-none lg:flex-row-reverse' },
})
export class EditionZone {
  readonly id = input.required<string>();
  /** Identifiant de la zone à modifier, ou « nouvelle ». */
  readonly zid = input.required<string>();

  private readonly client = inject(ClientZones);
  private readonly bracelets = inject(ClientBracelet);
  private readonly router = inject(Router);

  protected readonly categories = CATEGORIES;
  protected readonly joursDeLaSemaine = JOURS;
  protected readonly tolerances = TOLERANCES_MIN;
  protected readonly distance = distance;

  protected readonly creation = computed(() => this.zid() === 'nouvelle');
  protected readonly forme = signal<FormeZone>('CERCLE');
  protected readonly centre = signal<PointGeo | null>(null);
  protected readonly rayonM = signal(220);
  protected readonly sommets = signal<readonly PointGeo[]>([]);
  protected readonly categorie = signal<CategorieZone>('ECOLE');
  protected readonly jours = signal<readonly number[]>([1, 2, 3, 4, 5]);
  protected readonly toleranceMin = signal(5);
  protected readonly nom = new FormControl(CATEGORIES[0].libelle, { nonNullable: true });
  protected readonly debut = new FormControl('07:00', { nonNullable: true });
  protected readonly fin = new FormControl('17:30', { nonNullable: true });
  protected readonly journeeEntiere = new FormControl(false, { nonNullable: true });
  protected readonly touteLaJournee = signal(false);

  protected readonly confirmation = signal(false);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurCode = signal<string | null>(null);

  protected readonly brouillon = computed((): Brouillon => ({
    centre: this.forme() === 'CERCLE' ? this.centre() : null,
    rayonM: this.rayonM(),
    sommets: this.forme() === 'POLYGONE' ? this.sommets() : [],
  }));
  protected readonly consigne = computed(() => {
    if (this.forme() === 'CERCLE') {
      return this.centre() ? $localize`:@@zone.consigne.cercle:Touchez la carte pour déplacer le centre.` : $localize`:@@zone.consigne.centre:Touchez la carte pour placer le centre.`;
    }
    const nombre = this.sommets().length;
    return nombre < 3 ? $localize`:@@zone.consigne.polygone:Touchez la carte pour poser au moins trois sommets.` : $localize`:@@zone.consigne.sommets:${nombre}:nombre: sommets · touchez pour en ajouter`;
  });

  constructor() {
    effect(() => this.charger(this.id(), this.zid()));
    this.journeeEntiere.valueChanges.subscribe((entiere) => this.touteLaJournee.set(entiere));
  }

  protected classeChoix(choisi: boolean): string {
    return CHOIX + (choisi ? CHOISI : NON_CHOISI);
  }

  protected placer(point: PointGeo): void {
    this.erreur.set(null);
    if (this.forme() === 'CERCLE') {
      this.centre.set(point);
    } else if (this.sommets().length < SOMMETS_MAXIMUM) {
      this.sommets.update((sommets) => [...sommets, point]);
    }
  }

  protected reglerRayon(evenement: Event): void {
    this.rayonM.set(Number((evenement.target as HTMLInputElement).value));
  }

  protected choisirCategorie(categorie: (typeof CATEGORIES)[number]): void {
    // Le nom suit la catégorie tant que le parent ne l'a pas personnalisé.
    if (CATEGORIES.some((c) => c.libelle === this.nom.value) || !this.nom.value.trim()) {
      this.nom.setValue(categorie.libelle);
    }
    this.categorie.set(categorie.valeur);
  }

  protected basculerJour(jour: number): void {
    this.jours.update((jours) => (jours.includes(jour) ? jours.filter((j) => j !== jour) : [...jours, jour]));
  }

  protected retirerDernierSommet(): void {
    this.sommets.update((sommets) => sommets.slice(0, -1));
  }

  protected demanderConfirmation(): void {
    const manque = this.manque();
    this.erreur.set(manque);
    if (!manque) {
      this.erreurCode.set(null);
      this.confirmation.set(true);
    }
  }

  protected enregistrer(code: string): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreurCode.set(null);
    const saisie = this.saisie();
    const appel = this.creation() ? this.client.creer(this.id(), saisie, code) : this.client.modifier(this.id(), this.zid(), saisie, code);
    appel.subscribe({
      next: () => void this.router.navigate(['/enfants', this.id(), 'zones']),
      error: (cause: unknown) => {
        this.enCours.set(false);
        const lisible = erreurLisible(cause);
        if (lisible.code.startsWith('CODE_')) {
          this.erreurCode.set(lisible.message);
        } else {
          this.confirmation.set(false);
          this.erreur.set(lisible.message);
        }
      },
    });
  }

  private manque(): string | null {
    if (this.forme() === 'CERCLE' && !this.centre()) {
      return $localize`:@@zone.manque.centre:Touchez la carte pour placer le centre de la zone.`;
    }
    if (this.forme() === 'POLYGONE' && this.sommets().length < 3) {
      return $localize`:@@zone.manque.sommets:Posez au moins trois sommets sur la carte.`;
    }
    if (!this.nom.value.trim()) {
      return $localize`:@@zone.manque.nom:Donnez un nom à la zone.`;
    }
    if (this.jours().length === 0) {
      return $localize`:@@zone.manque.jours:Choisissez au moins un jour.`;
    }
    if (!this.touteLaJournee() && (!this.debut.value || !this.fin.value || this.debut.value === this.fin.value)) {
      return $localize`:@@zone.manque.heures:Indiquez une heure de début et une heure de fin différentes.`;
    }
    return null;
  }

  private saisie(): SaisieZone {
    const cercle = this.forme() === 'CERCLE';
    return {
      forme: this.forme(),
      nom: this.nom.value.trim(),
      categorie: this.categorie(),
      centre: cercle ? this.centre() : null,
      rayonM: cercle ? this.rayonM() : null,
      sommets: cercle ? null : this.sommets(),
      jours: [...this.jours()].sort((a, b) => a - b),
      debut: this.touteLaJournee() ? '00:00' : this.debut.value,
      fin: this.touteLaJournee() ? '00:00' : this.fin.value,
      toleranceS: this.toleranceMin() * 60,
    };
  }

  private charger(id: string, zid: string): void {
    if (zid === 'nouvelle') {
      // Une nouvelle zone circulaire se centre sur la dernière position connue de l'enfant, s'il y en a une.
      this.bracelets.situation(id).subscribe({
        next: (situation) => {
          if (situation.position && !this.centre()) {
            this.centre.set({ latitude: situation.position.latitude, longitude: situation.position.longitude });
          }
        },
        error: () => undefined,
      });
      return;
    }
    this.client.zones(id).subscribe({
      next: (donnees) => {
        const zone = donnees.zones.find((z) => z.id === zid);
        if (zone) {
          this.afficher(zone);
        } else {
          void this.router.navigate(['/enfants', id, 'zones']);
        }
      },
      error: (cause: unknown) => this.erreur.set(erreurLisible(cause).message),
    });
  }

  private afficher(zone: SafeZone): void {
    this.forme.set(zone.forme);
    this.centre.set(zone.centre);
    this.rayonM.set(zone.rayonM ?? 220);
    this.sommets.set(zone.sommets ?? []);
    this.categorie.set(zone.categorie);
    this.jours.set(zone.jours);
    this.toleranceMin.set(Math.round(zone.toleranceS / 60));
    this.nom.setValue(zone.nom);
    const entiere = zone.debut === zone.fin;
    this.journeeEntiere.setValue(entiere);
    if (!entiere) {
      this.debut.setValue(zone.debut);
      this.fin.setValue(zone.fin);
    }
  }
}
