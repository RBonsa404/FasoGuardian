import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClientLitiges, DecisionLitige, FondementLitige, Litige } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';

const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
const JOUR_ET_HEURE = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

const DECISIONS: readonly { code: DecisionLitige; libelle: string; effet: string }[] = [
  { code: 'LIEN_MAINTENU', libelle: $localize`:@@litige.maintenu:Lien maintenu`, effet: $localize`:@@litige.maintenu.effet:Le gel est levé : le tuteur retrouve tous ses réglages.` },
  { code: 'LIEN_RETIRE', libelle: $localize`:@@litige.retire:Lien retiré`, effet: $localize`:@@litige.retire.effet:Le tuteur perd l'accès à l'enfant ; ses sessions sont fermées.` },
];
const FONDEMENTS: readonly { code: FondementLitige; libelle: string }[] = [
  { code: 'DECISION_DE_JUSTICE', libelle: $localize`:@@litige.justice:Décision de justice` },
  { code: 'ACCORD_ECRIT', libelle: $localize`:@@litige.accord:Accord écrit des parties` },
];

interface Mesure {
  readonly libelle: string;
  readonly valeur: string;
  readonly detail: string;
  readonly classe: string;
}

/**
 * Litige de filiation (écran 61, US-KYC-001) : mesures conservatoires en vigueur, chronologie, puis décision
 * fondée sur une décision de justice ou un accord écrit. La décision est appliquée, journalisée et notifiée.
 */
@Component({
  selector: 'app-litige',
  imports: [ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgChamp, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="flex items-center gap-1.5 self-start text-label font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" routerLink="/kyc/litiges">
      <fg-icon nom="retour" [taille]="16" /><ng-container i18n="@@litiges.titre">Litiges de filiation</ng-container>
    </a>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (litige(); as l) {
      <div class="flex flex-col gap-1.5">
        <span class="text-label font-semibold" [class]="l.statut === 'OUVERT' ? 'text-attention-text' : 'text-text-2'">{{ entete() }}</span>
        <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@litige.titre">Contestation de la filiation : {{ l.tuteur }} → {{ l.enfant }}</h1>
        <p class="m-0 max-w-3xl text-body text-text-2">{{ l.motif }}</p>
        @if (l.dossierKyc) {
          <span class="text-caption text-text-3" i18n="@@litige.dossier">Lien établi par le dossier {{ l.dossierKyc }}</span>
        }
      </div>

      <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
        @for (mesure of mesures(); track mesure.libelle) {
          <section class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4">
            <span class="text-caption font-medium text-text-3">{{ mesure.libelle }}</span>
            <strong class="text-h3 font-semibold" [class]="mesure.classe">{{ mesure.valeur }}</strong>
            <span class="text-caption font-medium text-text-2">{{ mesure.detail }}</span>
          </section>
        }
      </div>

      <section class="flex flex-col gap-2" aria-labelledby="titre-chronologie">
        <h2 id="titre-chronologie" class="m-0 text-h3 font-semibold" i18n="@@litige.chronologie">Chronologie</h2>
        <ol class="m-0 flex list-none flex-col rounded-lg border border-line bg-surface p-0">
          @for (etape of chronologie(); track etape.quand) {
            <li class="flex items-baseline gap-4 border-b border-line px-4 py-3 text-label last:border-b-0">
              <span class="w-36 flex-none tabular-nums text-text-2">{{ etape.quand }}</span>
              <span>{{ etape.quoi }}</span>
            </li>
          }
        </ol>
      </section>

      @if (l.statut === 'OUVERT') {
        <div class="flex flex-wrap gap-2">
          <button fg-button type="button" (click)="ouvrirDecision()" i18n="@@litige.decider">Enregistrer la décision</button>
          <button fg-button variante="secondary" type="button" [chargement]="bascule()" (click)="basculerLaGeolocalisation(l)">
            {{ l.geolocalisationSuspendue ? libelleRetablir : libelleSuspendre }}
          </button>
        </div>
      }
    } @else if (!erreur()) {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet i18n-titre="@@litige.decider" titre="Enregistrer la décision" [ouverte]="feuille()" (fermee)="feuille.set(false)">
      <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
        <legend class="pb-2 text-label font-semibold" i18n="@@litige.decision">Décision</legend>
        @for (option of decisions; track option.code) {
          <label class="flex min-h-14 cursor-pointer flex-col justify-center gap-0.5 rounded-md border px-3 py-2 focus-within:outline-2 focus-within:outline-accent" [class]="decision.value === option.code ? 'border-accent bg-accent-soft' : 'border-line'">
            <input class="sr-only" type="radio" name="decision" [formControl]="decision" [value]="option.code" />
            <strong class="text-label font-semibold">{{ option.libelle }}</strong>
            <span class="text-caption text-text-2">{{ option.effet }}</span>
          </label>
        }
      </fieldset>
      <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
        <legend class="pb-2 text-label font-semibold" i18n="@@litige.fondement">Fondée sur</legend>
        @for (option of fondements; track option.code) {
          <label class="flex min-h-11 cursor-pointer items-center rounded-md border px-3 text-label font-medium focus-within:outline-2 focus-within:outline-accent" [class]="fondement.value === option.code ? 'border-accent bg-accent-soft' : 'border-line'">
            <input class="sr-only" type="radio" name="fondement" [formControl]="fondement" [value]="option.code" />{{ option.libelle }}
          </label>
        }
      </fieldset>
      <fg-input [formControl]="reference" i18n-libelle="@@litige.piece" libelle="Référence de la pièce" autocomplete="off" [longueurMax]="120" i18n-aide="@@litige.piece.aide" aide="Juridiction et numéro du jugement, ou date de l'accord." />
      <p class="m-0 text-caption text-text-3" i18n="@@litige.notifiees">Les parties sont notifiées de la décision ; elle est inscrite au journal d'audit.</p>
      @if (erreurFeuille(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      <button fg-button type="button" [chargement]="enCours()" (click)="decider()" i18n="@@litige.appliquer">Appliquer et notifier les parties</button>
      <button fg-button variante="ghost" type="button" (click)="feuille.set(false)" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class EcranLitige {
  readonly id = input.required<string>();

  private readonly client = inject(ClientLitiges);
  private readonly router = inject(Router);

  protected readonly decisions = DECISIONS;
  protected readonly fondements = FONDEMENTS;
  protected readonly libelleSuspendre = $localize`:@@litige.suspendre:Suspendre la géolocalisation`;
  protected readonly libelleRetablir = $localize`:@@litige.retablir:Rétablir la géolocalisation`;

  protected readonly litige = signal<Litige | null>(null);
  protected readonly feuille = signal(false);
  protected readonly enCours = signal(false);
  protected readonly bascule = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurFeuille = signal<string | null>(null);

  protected readonly decision = new FormControl<DecisionLitige | null>(null);
  protected readonly fondement = new FormControl<FondementLitige | null>(null);
  protected readonly reference = new FormControl('', { nonNullable: true });

  protected readonly entete = computed(() => {
    const l = this.litige();
    if (!l) {
      return '';
    }
    return l.statut === 'OUVERT'
      ? $localize`:@@litige.ouvert:Litige ouvert · ${l.reference}:reference: · compte gelé`
      : $localize`:@@litige.clos:Litige clos · ${l.reference}:reference:`;
  });
  protected readonly mesures = computed<readonly Mesure[]>(() => {
    const l = this.litige();
    return l ? mesures(l) : [];
  });
  protected readonly chronologie = computed(() => {
    const l = this.litige();
    return l ? chronologie(l) : [];
  });

  constructor() {
    effect(() => {
      this.client.litige(this.id()).subscribe({
        next: (litige) => this.litige.set(litige),
        error: (cause: unknown) => this.signaler(cause),
      });
    });
  }

  protected ouvrirDecision(): void {
    this.decision.setValue(null);
    this.fondement.setValue(null);
    this.reference.setValue('');
    this.erreurFeuille.set(null);
    this.feuille.set(true);
  }

  protected decider(): void {
    const decision = this.decision.value;
    const fondement = this.fondement.value;
    if (this.enCours()) {
      return;
    }
    if (!decision || !fondement) {
      this.erreurFeuille.set($localize`:@@litige.incomplet:Choisissez la décision et ce sur quoi elle se fonde.`);
      return;
    }
    this.enCours.set(true);
    this.erreurFeuille.set(null);
    this.client.decider(this.id(), decision, fondement, this.reference.value.trim()).subscribe({
      next: (litige) => {
        this.enCours.set(false);
        this.feuille.set(false);
        this.litige.set(litige);
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreurFeuille.set(erreurLisible(cause).message);
      },
    });
  }

  protected basculerLaGeolocalisation(litige: Litige): void {
    this.bascule.set(true);
    this.erreur.set(null);
    this.client.suspendreLaGeolocalisation(litige.id, !litige.geolocalisationSuspendue).subscribe({
      next: (aJour) => {
        this.bascule.set(false);
        this.litige.set(aJour);
      },
      error: (cause: unknown) => {
        this.bascule.set(false);
        this.signaler(cause);
      },
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

/** Les trois mesures de l'écran : gel du compte, géolocalisation, échéance ou décision. */
export function mesures(l: Litige): readonly Mesure[] {
  const ouvert = l.statut === 'OUVERT';
  return [
    {
      libelle: $localize`:@@litige.mesure.compte:Compte du tuteur contesté`,
      valeur: ouvert ? $localize`:@@litige.mesure.gele:Gelé` : $localize`:@@litige.mesure.degele:Gel levé`,
      detail: ouvert ? $localize`:@@litige.mesure.depuis:depuis ${JOUR_ET_HEURE.format(new Date(l.ouvertLe))}:date:` : $localize`:@@litige.mesure.decision:par la décision`,
      classe: ouvert ? 'text-danger' : 'text-text',
    },
    {
      libelle: $localize`:@@litige.mesure.geolocalisation:Géolocalisation`,
      valeur: l.geolocalisationSuspendue ? $localize`:@@litige.mesure.suspendue:Suspension conservatoire` : $localize`:@@litige.mesure.maintenue:Maintenue`,
      detail: $localize`:@@litige.mesure.securite:SOS et page QR maintenus`,
      classe: l.geolocalisationSuspendue ? 'text-attention-text' : 'text-text',
    },
    ouvert
      ? {
          libelle: $localize`:@@litige.mesure.echeance:Échéance`,
          valeur: JOUR.format(new Date(l.echeanceLe)),
          detail: $localize`:@@litige.mesure.delai:décision sous 7 jours`,
          classe: Date.parse(l.echeanceLe) < Date.now() ? 'text-danger' : 'text-text',
        }
      : {
          libelle: $localize`:@@litige.mesure.issue:Décision`,
          valeur: DECISIONS.find((d) => d.code === l.decision)?.libelle ?? '',
          detail: [FONDEMENTS.find((f) => f.code === l.fondement)?.libelle, l.referenceDuFondement].filter(Boolean).join(' · '),
          classe: 'text-text',
        },
  ];
}

export function chronologie(l: Litige): readonly { quand: string; quoi: string }[] {
  const etapes = [
    {
      quand: JOUR_ET_HEURE.format(new Date(l.ouvertLe)),
      quoi: $localize`:@@litige.chrono.ouvert:Litige ouvert, réglages gelés, parties prévenues`,
    },
  ];
  if (l.decideLe) {
    etapes.push({
      quand: JOUR_ET_HEURE.format(new Date(l.decideLe)),
      quoi:
        l.decision === 'LIEN_RETIRE'
          ? $localize`:@@litige.chrono.retire:Décision : lien retiré, accès fermé, parties notifiées`
          : $localize`:@@litige.chrono.maintenu:Décision : lien maintenu, gel levé, parties notifiées`,
    });
  }
  return etapes;
}
