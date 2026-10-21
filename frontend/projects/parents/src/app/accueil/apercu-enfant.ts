import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Alerte, ClientBracelet, ClientZones, FicheEnfant, SafeZone, Situation } from 'api';
import { FgBanniere, FgBouton, FgIcon, FgSquelette, NomIcone } from 'ui';

import { iconeAlerte, statutLisible, titreCourt } from '../alertes/libelles';
import { Carte } from '../carte/fond';
import { forceDuSignal, positionAncienne, repereDe } from '../carte/lecture';
import { erreurLisible } from '../commun/erreurs';
import { heure, ilYA } from '../commun/temps';
import { DePipe, de } from '../commun/francais';

const RAFRAICHISSEMENT_MS = 60_000;
/** Relectures après « Localiser maintenant », le temps que le bracelet réponde. */
const RELECTURES_MS = [5_000, 15_000, 30_000];

/** Autres écrans de l'enfant, sous les actions rapides. */
const AUTRES: readonly { chemin: string; libelle: string; icone: NomIcone }[] = [
  { chemin: 'carte', libelle: $localize`:@@apercu.carte:Carte`, icone: 'position' },
  { chemin: 'zones', libelle: $localize`:@@apercu.zones.long:Safe Zones`, icone: 'safe-zone' },
  { chemin: 'trajets', libelle: $localize`:@@apercu.trajets:Trajets`, icone: 'historique' },
  { chemin: 'bracelet', libelle: $localize`:@@apercu.bracelet:Bracelet`, icone: 'bracelet' },
];

const CARTE_ACTION =
  'group flex min-w-0 flex-col items-center gap-1.5 text-center text-[11px] leading-tight font-medium text-text-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent rounded-md disabled:opacity-60 lg:flex-row lg:gap-3 lg:rounded-lg lg:border lg:border-line lg:bg-surface lg:p-3 lg:text-left lg:text-label lg:text-text lg:transition-[transform,box-shadow,border-color] lg:duration-(--duration-base) lg:hover:-translate-y-0.5 lg:hover:border-line-strong lg:hover:shadow-e2';
const PASTILLE_ACTION =
  'grid size-13 flex-none place-items-center rounded-lg border border-line bg-surface text-text transition-colors duration-(--duration-fast) group-hover:text-accent lg:size-10 lg:rounded-md lg:border-0 lg:bg-accent-soft lg:text-accent';

/**
 * Aperçu d'un enfant sur le tableau de bord (écran 16, US-PAR-006) : carte en tête d'écran avec le choix de
 * l'enfant et la cloche, carte d'état posée dessus (lieu, précision, position, batterie, réseau), actions
 * rapides et dernière alerte. Rafraîchi chaque minute ; sans bracelet, l'aperçu invite à l'appairage.
 */
@Component({
  selector: 'app-apercu-enfant',
  imports: [DePipe, RouterLink, Carte, FgBanniere, FgBouton, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative -mx-5 -mt-10 h-[330px] overflow-hidden lg:sticky lg:top-8 lg:row-span-8 lg:mx-0 lg:mt-0 lg:h-[min(44rem,calc(100dvh-10rem))] lg:rounded-xl lg:border lg:border-line lg:shadow-e2">
      @if (situation()) {
        <app-carte class="absolute inset-0" [libelle]="libelleCarte()" [position]="repere()" [zones]="zones()" />
      } @else {
        <div class="absolute inset-0 bg-surface-2"></div>
      }
      <div class="absolute inset-x-4 top-11 z-1000 flex items-center justify-between lg:top-4">
        <button type="button" class="flex items-center gap-2 rounded-full bg-glass py-1.5 pr-3 pl-1.5 backdrop-blur-md focus-visible:outline-2 focus-visible:outline-accent" (click)="changerEnfant.emit()" [attr.aria-label]="libelleChoix()">
          <span class="grid size-7.5 place-items-center rounded-full border-2 border-accent bg-surface-2 font-display text-[13px] font-bold" aria-hidden="true">{{ initiale() }}</span>
          <span class="text-label font-semibold">{{ enfant().prenom }}</span>
          <fg-icon nom="chevron-bas" [taille]="14" />
        </button>
        <a class="relative grid size-11 place-items-center rounded-[14px] bg-glass backdrop-blur-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/alertes" [attr.aria-label]="libelleCloche()">
          <fg-icon nom="cloche" [taille]="20" />
          @if (alertesEnCours() > 0) {
            <span class="absolute top-2.25 right-2.5 size-2 rounded-full bg-accent" aria-hidden="true"></span>
          }
        </a>
      </div>
      @if (situation()) {
        <a class="absolute right-4 bottom-10 z-1000 rounded-full bg-glass px-3 py-1.5 text-caption font-semibold backdrop-blur-md focus-visible:outline-2 focus-visible:outline-accent lg:bottom-4" [routerLink]="['/enfants', enfant().id, 'carte']" i18n="@@apercu.agrandir">Agrandir</a>
      }
    </div>

    @if (situation(); as s) {
      <section class="relative z-1000 mx-[-8px] -mt-7 flex animate-(--animate-rise) flex-col gap-3 rounded-[22px] border border-line bg-surface p-4 shadow-e2 lg:mx-0 lg:mt-0 lg:p-5" aria-live="polite">
        <div class="flex items-start justify-between gap-3">
          <div class="flex min-w-0 flex-col gap-0.75">
            <h2 class="m-0 font-display text-[18px] font-semibold">{{ titre() }}</h2>
            @if (zoneDedans(); as z) {
              <span class="flex items-center gap-1.5 text-label font-medium text-success"><span class="size-2 flex-none rounded-full bg-success" aria-hidden="true"></span><ng-container i18n="@@apercu.dedans">Dans « {{ z }} »</ng-container></span>
            } @else if (zoneSortie()) {
              <span class="flex items-center gap-1.5 text-label font-medium text-danger"><span class="size-2 flex-none rounded-full bg-danger" aria-hidden="true"></span><ng-container i18n="@@apercu.sortie">Sortie de Safe Zone en cours</ng-container></span>
            }
            @if (ancienne()) {
              <span class="text-label text-text-2" i18n="@@carte.ancienne">Le bracelet n'a pas transmis de position plus récente.</span>
            }
          </div>
          @if (s.position; as p) {
            @if (p.source === 'GNSS') {
              <span class="inline-flex h-6.5 flex-none items-center rounded-full bg-success-soft px-2.5 text-caption font-semibold whitespace-nowrap text-success" i18n="@@carte.precise">Précise ± {{ p.precisionM }} m</span>
            } @else {
              <span class="inline-flex h-6.5 flex-none items-center rounded-full bg-attention-soft px-2.5 text-caption font-semibold whitespace-nowrap text-attention-text" i18n="@@carte.approximative">Approx. ± {{ p.precisionM }} m</span>
            }
          }
        </div>
        @if (!s.position) {
          <p class="m-0 text-label text-text-2" i18n="@@carte.aucune.texte">Le bracelet {{ s.numeroSerie }} n'a pas encore transmis de position. Vérifiez qu'il est allumé et chargé.</p>
        }
        <dl class="m-0 grid grid-cols-3 gap-2">
          @for (mesure of mesures(); track mesure.cle) {
            <div class="flex min-w-0 flex-col gap-0.5 rounded-md bg-surface-2 p-2.5">
              <dt class="text-[11px] font-medium text-text-3">{{ mesure.cle }}</dt>
              <dd class="m-0 truncate text-[15px] font-semibold tabular-nums">{{ mesure.valeur }}</dd>
              <dd class="m-0 truncate text-[11px] font-medium text-text-3">{{ mesure.detail }}</dd>
            </div>
          }
        </dl>
      </section>
    } @else if (sansBracelet()) {
      <section class="relative z-1000 mx-[-8px] -mt-7 flex flex-col gap-3 rounded-[22px] border border-line bg-surface p-5 shadow-e2 lg:mx-0 lg:mt-0">
        <h2 class="m-0 text-h3 font-semibold" i18n="@@apercu.associer.titre">Associez le bracelet {{ enfant().prenom | de }}</h2>
        <p class="m-0 text-body text-text-2" i18n="@@apercu.associer.texte">Le code d'appairage est sur la carte glissée dans la boîte.</p>
        <button fg-button type="button" (click)="associer()" i18n="@@apercu.associer">Associer</button>
      </section>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
      <button fg-button variante="secondary" type="button" class="self-start" (click)="rafraichir()" i18n="@@commun.reessayer">Réessayer</button>
    } @else {
      <fg-skeleton forme="carte" />
    }

    <!-- Bandeaux du tableau de bord (alerte en cours, abonnement), sous la carte d'état. -->
    <ng-content />

    @if (situation()) {
      <nav class="grid grid-cols-4 gap-2 pt-0.5 lg:grid-cols-2" [attr.aria-label]="libelleActions()">
        <button type="button" [class]="carteAction" [disabled]="enDemande()" (click)="localiser()">
          <span [class]="pastilleAction"><fg-icon nom="localiser" [taille]="22" /></span>
          <ng-container i18n="@@apercu.localiser">Localiser maintenant</ng-container>
        </button>
        <a [class]="carteAction" [routerLink]="['/enfants', enfant().id, 'bracelet', 'retrait']">
          <span [class]="pastilleAction"><fg-icon nom="horloge" [taille]="22" /></span>
          <ng-container i18n="@@apercu.retrait.long">Autoriser un retrait</ng-container>
        </a>
        <a [class]="carteAction" [routerLink]="['/enfants', enfant().id, 'partage']">
          <span [class]="pastilleAction"><fg-icon nom="partager" [taille]="22" /></span>
          <ng-container i18n="@@apercu.partage.long">Partager la position</ng-container>
        </a>
        <a [class]="carteAction" routerLink="/alertes" [queryParams]="{ signaler: enfant().id }">
          <span [class]="pastilleAction"><fg-icon nom="drapeau" [taille]="22" /></span>
          <ng-container i18n="@@apercu.signaler">Signaler</ng-container>
        </a>
      </nav>
      @if (demande(); as message) {
        <p class="m-0 text-label text-text-2" role="status">{{ message }}</p>
      }

      <section class="flex flex-col gap-1.5">
        <div class="flex items-center justify-between px-1 pt-2">
          <h2 class="m-0 text-label font-semibold" i18n="@@apercu.dernieres">Dernières alertes</h2>
          <a class="text-label font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/enfants', enfant().id, 'journal']" i18n="@@apercu.toutVoir">Tout voir</a>
        </div>
        @if (derniereAlerte(); as a) {
          <a class="flex items-center gap-3 rounded-lg border border-line bg-surface px-3.5 py-3 focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="['/alertes', a.id]">
            <span class="grid size-9 flex-none place-items-center rounded-[10px] bg-attention-soft text-attention-text"><fg-icon [nom]="icone(a)" [taille]="18" /></span>
            <span class="flex min-w-0 flex-1 flex-col gap-px">
              <span class="truncate text-label font-semibold">{{ titreAlerte(a) }}</span>
              <span class="truncate text-caption text-text-3">{{ detailAlerte(a) }}</span>
            </span>
          </a>
        } @else {
          <p class="m-0 rounded-lg border border-line bg-surface px-3.5 py-3 text-label text-text-2" i18n="@@apercu.aucuneAlerte">Aucune alerte pour {{ enfant().prenom }}.</p>
        }
      </section>

      <nav class="flex flex-col divide-y divide-line rounded-lg border border-line bg-surface" [attr.aria-label]="libelleAutres()">
        @for (autre of autres; track autre.chemin) {
          <a class="group flex min-h-12 items-center gap-3 px-3.5 text-label font-medium transition-colors duration-(--duration-fast) first:rounded-t-lg last:rounded-b-lg hover:bg-surface-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent" [routerLink]="'/enfants/' + enfant().id + '/' + autre.chemin">
            <fg-icon class="text-accent" [nom]="autre.icone" [taille]="20" />
            <span class="flex-1">{{ autre.libelle }}</span>
            <fg-icon class="rotate-180 text-text-3 transition-transform duration-(--duration-fast) group-hover:translate-x-0.5" nom="retour" [taille]="16" />
          </a>
        }
      </nav>
    }
  `,
  // Grand écran : la carte à gauche, sur toute la hauteur ; état, actions et alertes à droite.
  host: { class: 'flex flex-col gap-3 lg:grid lg:grid-cols-[minmax(0,1fr)_23rem] lg:items-start lg:gap-5 xl:grid-cols-[minmax(0,1fr)_26rem]' },
})
export class ApercuEnfant {
  readonly enfant = input.required<FicheEnfant>();
  /** Alertes de l'enfant, les plus récentes d'abord. */
  readonly alertes = input<readonly Alerte[]>([]);
  /** Alertes en cours, tous enfants confondus : la cloche le signale. */
  readonly alertesEnCours = input(0);
  readonly changerEnfant = output<void>();

  private readonly bracelets = inject(ClientBracelet);
  private readonly clientZones = inject(ClientZones);
  private readonly router = inject(Router);

  protected readonly autres = AUTRES;
  protected readonly carteAction = CARTE_ACTION;
  protected readonly pastilleAction = PASTILLE_ACTION;
  protected readonly situation = signal<Situation | null>(null);
  protected readonly zones = signal<readonly SafeZone[]>([]);
  protected readonly sansBracelet = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enDemande = signal(false);
  protected readonly demande = signal<string | null>(null);
  private readonly maintenant = signal(new Date());

  protected readonly initiale = computed(() => this.enfant().prenom.charAt(0).toUpperCase());
  protected readonly libelleChoix = computed(() => $localize`:@@apercu.choix:Enfant affiché : ${this.enfant().prenom}:prenom:. Changer`);
  protected readonly libelleCloche = computed(() =>
    this.alertesEnCours() > 0
      ? $localize`:@@apercu.cloche.alertes:Voir les alertes : ${this.alertesEnCours()}:nombre: en cours`
      : $localize`:@@apercu.cloche:Voir les alertes`,
  );
  protected readonly libelleCarte = computed(() => $localize`:@@apercu.carte.libelle:Carte de la position ${de(this.enfant().prenom)}:dePrenom:`);
  protected readonly libelleActions = computed(() => $localize`:@@apercu.actions:Actions pour ${this.enfant().prenom}:prenom:`);
  protected readonly libelleAutres = computed(() => $localize`:@@apercu.raccourcis:Raccourcis pour ${this.enfant().prenom}:prenom:`);
  protected readonly ancienne = computed(() => positionAncienne(this.situation(), this.maintenant()));
  protected readonly repere = computed(() => repereDe(this.situation(), this.enfant().prenom, this.maintenant()));
  protected readonly zoneDedans = computed(() => this.zones().find((z) => z.enfantDedans && !z.sortieEnCours)?.nom ?? null);
  protected readonly zoneSortie = computed(() => this.zones().find((z) => z.sortieEnCours)?.nom ?? null);
  /** Le lieu d'abord (la zone où se trouve l'enfant), sinon sa position. */
  protected readonly titre = computed(() => {
    if (!this.situation()?.position) {
      return $localize`:@@carte.aucune:Pas encore de position`;
    }
    const sortie = this.zoneSortie();
    if (sortie) {
      return $localize`:@@carte.titre.sortie:Hors de « ${sortie}:zone: »`;
    }
    return this.zoneDedans() ?? $localize`:@@carte.titre.position:Position ${de(this.enfant().prenom)}:dePrenom:`;
  });
  protected readonly derniereAlerte = computed(() => this.alertes()[0] ?? null);
  protected readonly mesures = computed(() => {
    const situation = this.situation();
    const etat = situation?.etat;
    const position = situation?.position;
    return [
      {
        cle: $localize`:@@apercu.position:Position`,
        valeur: position ? ilYA(position.mesureeLe, this.maintenant()) : '—',
        detail: position ? heure(position.mesureeLe) : '',
      },
      {
        cle: $localize`:@@bracelet.batterie:Batterie`,
        valeur: etat?.batterie != null ? `${etat.batterie} %` : '—',
        detail: etat?.batterie != null && etat.batterie < 20 ? $localize`:@@bracelet.batterie.faible:batterie faible` : '',
      },
      {
        cle: $localize`:@@apercu.reseau:Réseau`,
        valeur: etat?.reseau ?? '—',
        detail: etat?.signalDbm != null ? [etat.operateur, forceDuSignal(etat.signalDbm)].filter(Boolean).join(' · ') : '',
      },
    ];
  });

  protected readonly icone = iconeAlerte;
  protected readonly titreAlerte = titreCourt;

  constructor() {
    effect(() => {
      this.enfant();
      this.situation.set(null);
      this.sansBracelet.set(false);
      this.demande.set(null);
      this.rafraichir();
    });
    const minuterie = setInterval(() => {
      this.maintenant.set(new Date());
      if (!this.sansBracelet()) {
        this.rafraichir();
      }
    }, RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected detailAlerte(alerte: Alerte): string {
    return `${statutLisible(alerte.statut)} · ${heure(alerte.ouverteLe)}`;
  }

  protected associer(): void {
    void this.router.navigate(['/bracelet/associer'], { queryParams: { enfant: this.enfant().id } });
  }

  /** Demande une position immédiate ; l'aperçu est relu peu après, le temps que le bracelet réponde. */
  protected localiser(): void {
    const id = this.enfant().id;
    this.enDemande.set(true);
    this.demande.set(null);
    this.bracelets.localiser(id).subscribe({
      next: () => {
        this.enDemande.set(false);
        this.demande.set($localize`:@@carte.localiser.envoyee:Demande envoyée au bracelet. La position arrive dans quelques instants.`);
        for (const delai of RELECTURES_MS) {
          setTimeout(() => this.enfant().id === id && this.rafraichir(), delai);
        }
      },
      error: (cause: unknown) => {
        this.enDemande.set(false);
        this.demande.set(erreurLisible(cause).message);
      },
    });
  }

  protected rafraichir(): void {
    const id = this.enfant().id;
    forkJoin({ situation: this.bracelets.situation(id), zones: this.clientZones.zones(id) }).subscribe({
      next: ({ situation, zones }) => {
        this.erreur.set(null);
        this.situation.set(situation);
        this.zones.set(zones.zones);
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
