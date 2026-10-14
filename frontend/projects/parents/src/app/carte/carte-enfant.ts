import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import {
  ClientAlertes,
  ClientBracelet,
  ClientFamille,
  ClientZones,
  EtatModeAlerte,
  FicheEnfant,
  SafeZone,
  Situation,
} from 'api';
import { FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette } from 'ui';

import { erreurLisible } from '../commun/erreurs';
import { heure, ilYA } from '../commun/temps';
import { Carte } from './fond';
import { positionAncienne, repereDe, titrePosition } from './lecture';

const RAFRAICHISSEMENT_MS = 60_000;
/** Relectures après « Localiser maintenant » : le bracelet répond en quelques secondes en réseau nominal. */
const DELAIS_DE_RELECTURE_MS = [5_000, 15_000, 30_000];

/**
 * Carte plein écran (écran 18, US-PAR-006 et US-PAR-009) : dernière position de l'enfant, toujours avec son
 * heure, sa précision et son cercle d'incertitude, et les Safe Zones. Rafraîchie chaque minute.
 */
@Component({
  selector: 'app-carte-enfant',
  imports: [RouterLink, Carte, FgBadge, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-carte
      class="absolute inset-0"
      i18n-libelle="@@carte.libelle"
      libelle="Carte de la position de l'enfant"
      [position]="repere()"
      [zones]="zones()"
    />

    <a
      class="absolute top-6 left-4 z-1000 grid size-11 place-items-center rounded-full bg-surface shadow-e2 focus-visible:outline-2 focus-visible:outline-accent"
      [routerLink]="['/enfants', id()]"
      i18n-aria-label="@@etape.retour"
      aria-label="Retour"
    >
      <fg-icon nom="retour" [taille]="22" />
    </a>

    @if (situation(); as s) {
      @if (modeAlerte(); as m) {
        <div
          class="absolute top-5.5 right-4 z-1000 flex items-center gap-3 rounded-full bg-surface py-1.5 pr-1.5 pl-4 shadow-e2"
        >
          <span class="flex flex-col leading-tight">
            <span class="text-label font-semibold" i18n="@@carte.modeAlerte"
              >Mode alerte · 60 s</span
            >
            @if (m.alerteCritique) {
              <span class="text-caption text-text-2" i18n="@@carte.modeAlerte.alerte"
                >pendant l'alerte en cours</span
              >
            } @else if (m.jusquAu) {
              <span class="text-caption text-text-2 tabular-nums" i18n="@@carte.modeAlerte.jusqua"
                >jusqu'à {{ heure(m.jusquAu) }}</span
              >
            }
          </span>
          <button
            type="button"
            role="switch"
            class="relative h-8 w-13 flex-none rounded-full transition-colors duration-(--duration-fast) ease-standard focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-60"
            [class]="m.actif ? 'bg-accent' : 'border border-line-strong bg-surface-2'"
            [attr.aria-checked]="m.actif"
            i18n-aria-label="@@carte.modeAlerte.libelle"
            aria-label="Mode alerte : une position toutes les 60 secondes pendant 30 minutes"
            [disabled]="m.alerteCritique || basculeEnCours()"
            (click)="basculerModeAlerte(m)"
          >
            <span
              class="absolute top-0.75 size-6.5 rounded-full shadow-e1 transition-[left] duration-(--duration-fast) ease-standard"
              [class]="m.actif ? 'left-5.75 bg-white' : 'left-0.75 bg-text-3'"
            ></span>
          </button>
        </div>
      }
    }

    <div
      class="absolute inset-x-4 bottom-6 z-1000 flex flex-col items-end gap-3 lg:right-auto lg:left-6 lg:w-96"
    >
      @if (situation()?.position) {
        <button
          type="button"
          class="grid size-12 place-items-center rounded-full bg-primary text-white shadow-e2 hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          i18n-aria-label="@@carte.recentrer"
          aria-label="Recentrer sur l'enfant"
          (click)="fond().recentrer()"
        >
          <fg-icon nom="position" [taille]="22" />
        </button>
      }
      <section
        class="flex w-full flex-col gap-2 rounded-xl border border-line bg-surface p-4 shadow-e2"
        aria-live="polite"
      >
        @if (situation(); as s) {
          @if (s.position; as p) {
            <h1 class="m-0 text-h3 font-semibold">{{ titre() }}</h1>
            <div class="flex flex-wrap items-center gap-2">
              @if (p.source === 'GNSS') {
                <fg-badge ton="succes" icone="position" i18n="@@carte.precise"
                  >Précise ± {{ p.precisionM }} m</fg-badge
                >
              } @else {
                <fg-badge ton="attention" icone="info" i18n="@@carte.approximative"
                  >Approx. ± {{ p.precisionM }} m</fg-badge
                >
              }
              <span class="text-label text-text-2 tabular-nums">{{ quand() }}</span>
            </div>
            @if (p.source === 'LORA') {
              <p class="m-0 text-label text-text-2" i18n="@@carte.passerelle">
                Présence relevée par la passerelle d'un établissement partenaire : votre enfant est
                dans son enceinte.
              </p>
            } @else if (p.source !== 'GNSS') {
              <p class="m-0 text-label text-text-2" i18n="@@carte.antenne">
                Position par antenne relais : le GPS est indisponible, par exemple à l'intérieur
                d'un bâtiment.
              </p>
            }
            @if (ancienne()) {
              <p class="m-0 text-label text-text-2" i18n="@@carte.ancienne">
                Le bracelet n'a pas transmis de position plus récente.
              </p>
            }
          } @else {
            <h1 class="m-0 text-h3 font-semibold" i18n="@@carte.aucune">Pas encore de position</h1>
            <p class="m-0 text-label text-text-2" i18n="@@carte.aucune.texte">
              Le bracelet {{ s.numeroSerie }} n'a pas encore transmis de position. Vérifiez qu'il
              est allumé et chargé.
            </p>
          }
          @if (demande(); as message) {
            <p class="m-0 text-label font-medium" role="status">{{ message }}</p>
          }
          <button
            fg-button
            type="button"
            [chargement]="enDemande()"
            (click)="localiser()"
            i18n="@@carte.localiser"
          >
            Localiser maintenant
          </button>
        } @else if (sansBracelet()) {
          <h1 class="m-0 text-h3 font-semibold" i18n="@@bracelet.absent">
            Aucun bracelet n'est associé à cet enfant.
          </h1>
          <a
            class="self-start text-label font-semibold text-accent"
            routerLink="/bracelet/associer"
            [queryParams]="{ enfant: id() }"
            i18n="@@bracelet.associer"
            >Associer un bracelet</a
          >
        } @else if (erreur(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        } @else {
          <fg-skeleton forme="ligne" />
          <fg-skeleton forme="ligne" />
        }
      </section>
    </div>
  `,
  host: { class: 'relative block h-dvh w-full overflow-hidden' },
})
export class CarteEnfant {
  readonly id = input.required<string>();

  private readonly bracelets = inject(ClientBracelet);
  private readonly famille = inject(ClientFamille);
  private readonly clientZones = inject(ClientZones);
  private readonly alertes = inject(ClientAlertes);
  protected readonly fond = viewChild.required(Carte);
  protected readonly heure = heure;

  protected readonly enfant = signal<FicheEnfant | null>(null);
  protected readonly situation = signal<Situation | null>(null);
  protected readonly zones = signal<readonly SafeZone[]>([]);
  protected readonly sansBracelet = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enDemande = signal(false);
  /** Mode alerte du bracelet ; null tant qu'il n'est pas connu. */
  protected readonly modeAlerte = signal<EtatModeAlerte | null>(null);
  protected readonly basculeEnCours = signal(false);
  /** Suite donnée à « Localiser maintenant ». */
  protected readonly demande = signal<string | null>(null);
  /** Horloge de l'écran : fait vieillir « il y a 2 min » entre deux rafraîchissements. */
  private readonly maintenant = signal(new Date());

  protected readonly ancienne = computed(() =>
    positionAncienne(this.situation(), this.maintenant()),
  );
  protected readonly repere = computed(() =>
    repereDe(this.situation(), this.enfant()?.prenom, this.maintenant()),
  );
  protected readonly titre = computed(() => titrePosition(this.zones(), this.enfant()?.prenom));
  protected readonly quand = computed(() => {
    const situation = this.situation();
    if (!situation?.position) {
      return '';
    }
    const morceaux = [
      heure(situation.position.mesureeLe),
      ilYA(situation.position.mesureeLe, this.maintenant()),
    ];
    if (situation.etat?.batterie != null) {
      morceaux.push($localize`:@@carte.batterie:batterie ${situation.etat.batterie}:niveau: %`);
    }
    return morceaux.join(' · ');
  });

  constructor() {
    effect(() => this.charger(this.id()));
    const minuterie = setInterval(() => {
      this.maintenant.set(new Date());
      this.rafraichir(this.id());
    }, RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  /** Suivi rapproché : une position par minute pendant trente minutes, ou son arrêt. */
  protected basculerModeAlerte(etat: EtatModeAlerte): void {
    const id = this.id();
    this.basculeEnCours.set(true);
    this.demande.set(null);
    (etat.actif
      ? this.alertes.arreterModeAlerte(id)
      : this.alertes.activerModeAlerte(id)
    ).subscribe({
      next: (nouvel) => {
        this.basculeEnCours.set(false);
        this.modeAlerte.set(nouvel);
        if (nouvel.actif && !etat.actif) {
          this.demande.set(
            $localize`:@@carte.modeAlerte.active:Une position par minute pendant trente minutes. Le mode s'arrête de lui-même pour ménager la batterie.`,
          );
          for (const delai of DELAIS_DE_RELECTURE_MS) {
            setTimeout(() => this.rafraichir(id), delai);
          }
        }
      },
      error: (cause: unknown) => {
        this.basculeEnCours.set(false);
        this.demande.set(erreurLisible(cause).message);
      },
    });
  }

  /** Demande une position immédiate ; la carte est relue peu après, le temps que le bracelet réponde. */
  protected localiser(): void {
    const id = this.id();
    this.enDemande.set(true);
    this.demande.set(null);
    this.bracelets.localiser(id).subscribe({
      next: () => {
        this.enDemande.set(false);
        this.demande.set(
          $localize`:@@carte.localiser.envoyee:Demande envoyée au bracelet. La position arrive dans quelques instants.`,
        );
        for (const delai of DELAIS_DE_RELECTURE_MS) {
          setTimeout(() => this.rafraichir(id), delai);
        }
      },
      error: (cause: unknown) => {
        this.enDemande.set(false);
        this.demande.set(erreurLisible(cause).message);
      },
    });
  }

  private charger(id: string): void {
    this.situation.set(null);
    this.sansBracelet.set(false);
    this.erreur.set(null);
    this.famille
      .enfant(id)
      .subscribe({ next: (enfant) => this.enfant.set(enfant), error: () => undefined });
    this.rafraichir(id);
  }

  private rafraichir(id: string): void {
    forkJoin({
      situation: this.bracelets.situation(id),
      zones: this.clientZones.zones(id),
    }).subscribe({
      next: ({ situation, zones }) => {
        this.erreur.set(null);
        this.situation.set(situation);
        this.zones.set(zones.zones);
        this.alertes
          .modeAlerte(id)
          .subscribe({ next: (etat) => this.modeAlerte.set(etat), error: () => undefined });
      },
      error: (cause: unknown) => {
        const lisible = erreurLisible(cause);
        if (lisible.code === 'RESSOURCE_INTROUVABLE') {
          this.sansBracelet.set(true);
        } else if (!this.situation()) {
          // Une position déjà affichée reste à l'écran avec son heure si le réseau manque au rafraîchissement.
          this.erreur.set(lisible.message);
        }
      },
    });
  }
}
