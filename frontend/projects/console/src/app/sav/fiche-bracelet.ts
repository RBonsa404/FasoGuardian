import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { CarteActivationParc, ClientSav, EtatTechniqueBracelet, FicheBraceletParc, StatutBraceletParc } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgFeuille, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';
import { LIBELLES_MOTIF_FIN, LIBELLES_STATUT, TONS_STATUT, jour } from './libelles';

type Action = 'retour' | 'remise' | 'reforme';

const ACTIONS: Record<Action, { titre: string; texte: string; bouton: string }> = {
  retour: {
    titre: $localize`:@@fiche.retour.titre:Enregistrer le retour au SAV ?`,
    texte: $localize`:@@fiche.retour.texte:Le bracelet passe « En SAV » : il n'est plus actif pour l'enfant et sa page publique ne le désigne plus.`,
    bouton: $localize`:@@fiche.retour:Retour au SAV`,
  },
  remise: {
    titre: $localize`:@@fiche.remise.titre:Remettre en stock ?`,
    texte: $localize`:@@fiche.remise.texte:L'unité reconditionnée reçoit un nouveau code d'appairage. Il ne sera affiché qu'une fois.`,
    bouton: $localize`:@@fiche.remise:Remettre en stock`,
  },
  reforme: {
    titre: $localize`:@@fiche.reforme.titre:Retirer ce bracelet du parc ?`,
    texte: $localize`:@@fiche.reforme.texte:Le bracelet est retiré définitivement du parc et son certificat est révoqué. Cette action ne se défait pas.`,
    bouton: $localize`:@@fiche.reforme:Retirer du parc`,
  },
};

const ETAPES = [
  $localize`:@@fiche.cycle.stock:En stock`,
  $localize`:@@fiche.cycle.remis:Remis`,
  $localize`:@@fiche.cycle.actif:Actif`,
  $localize`:@@fiche.cycle.sav:En SAV`,
  $localize`:@@fiche.cycle.retire:Retiré`,
];

/** Étape du cycle de vie où se trouve l'unité ; un bracelet perdu ou volé s'est arrêté à « Actif ». */
const ETAPE: Record<StatutBraceletParc, number> = { EN_STOCK: 0, ACTIF: 2, PERDU: 2, VOLE: 2, EN_SAV: 3, REFORME: 4 };

/**
 * Fiche d'un bracelet (écran 65, US-SAV-002), dans le panneau du parc : identité matérielle, cycle de vie et
 * interventions (appairages, sans le nom des enfants). La consultation est journalisée. La maquette montre aussi
 * la SIM, le coloris et les cycles de charge, que le parc n'enregistre pas : ils ne sont pas affichés.
 */
@Component({
  selector: 'app-fiche-bracelet',
  imports: [FgBadge, FgBanniere, FgBouton, FgFeuille, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (fiche(); as f) {
      <div class="flex items-center justify-between gap-3">
        <h2 class="m-0 font-mono text-lg font-semibold">{{ f.bracelet.numeroSerie }}</h2>
        <div class="flex flex-wrap justify-end gap-1.5">
          @if (f.bracelet.certificatRevoque) {
            <fg-badge ton="danger" i18n="@@fiche.revoque">Certificat révoqué</fg-badge>
          }
          <fg-badge [ton]="tons[f.bracelet.statut]">{{ libelles[f.bracelet.statut] }}</fg-badge>
        </div>
      </div>
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      @if (carte(); as c) {
        <section class="flex flex-col gap-2 rounded-xl border border-accent bg-surface p-4" role="status">
          <strong class="text-label font-semibold" i18n="@@fiche.carte.titre">Carte d'activation à imprimer — affichée une seule fois</strong>
          <dl class="m-0 grid grid-cols-[130px_1fr] gap-y-1.5 text-label">
            <dt class="text-text-2" i18n="@@fiche.carte.code">Code d'appairage</dt>
            <dd class="m-0 font-mono font-semibold" data-code-appairage>{{ c.codeAppairage }}</dd>
            @if (c.jetonQr) {
              <dt class="text-text-2" i18n="@@fiche.carte.qr">Jeton du QR</dt>
              <dd class="m-0 break-all font-mono text-caption">{{ c.jetonQr }}</dd>
            }
          </dl>
          @if (!c.jetonQr) {
            <span class="text-caption text-text-3" i18n="@@fiche.carte.qrInchange">Le QR gravé sur le bracelet reste le même.</span>
          }
        </section>
      }

      <dl class="m-0 flex flex-col rounded-xl border border-line bg-surface" i18n-aria-label="@@fiche.materiel" aria-label="Matériel">
        <div class="flex h-[38px] items-center justify-between gap-4 border-b border-line px-3 text-label font-medium">
          <dt class="text-text-2" i18n="@@fiche.imei">IMEI</dt><dd class="m-0 font-mono text-caption">{{ f.imeiMasque }}</dd>
        </div>
        <div class="flex h-[38px] items-center justify-between gap-4 border-b border-line px-3 text-label font-medium">
          <dt class="text-text-2" i18n="@@fiche.revision">Révision matérielle</dt><dd class="m-0">{{ f.bracelet.revisionMaterielle }}</dd>
        </div>
        <div class="flex h-[38px] items-center justify-between gap-4 border-b border-line px-3 text-label font-medium">
          <dt class="text-text-2" i18n="@@fiche.miseEnService">Mise en service</dt><dd class="m-0 tabular-nums">{{ jour(miseEnService()) }}</dd>
        </div>
        <div class="flex h-[38px] items-center justify-between gap-4 border-b border-line px-3 text-label font-medium">
          <dt class="text-text-2" i18n="@@fiche.batterie">Batterie</dt><dd class="m-0 tabular-nums">{{ etat()?.batterie != null ? etat()!.batterie + ' %' : '—' }}</dd>
        </div>
        <div class="flex h-[38px] items-center justify-between gap-4 border-b border-line px-3 text-label font-medium">
          <dt class="text-text-2" i18n="@@parc.col.logiciel">Logiciel</dt><dd class="m-0 tabular-nums">{{ f.bracelet.versionLogiciel }}</dd>
        </div>
        <div class="flex h-[38px] items-center justify-between gap-4 border-b border-line px-3 text-label font-medium">
          <dt class="text-text-2" i18n="@@fiche.garantie">Garantie</dt><dd class="m-0 tabular-nums">{{ jour(f.bracelet.garantieJusquAu) }}</dd>
        </div>
        <div class="flex h-[38px] items-center justify-between gap-4 px-3 text-label font-medium">
          <dt class="text-text-2" i18n="@@fiche.certificat">Certificat</dt>
          <dd class="m-0 max-w-48 truncate font-mono text-caption" [attr.title]="f.empreinteCertificat">{{ f.empreinteCertificat }}</dd>
        </div>
      </dl>

      <h3 class="m-0 font-display text-sm font-semibold" i18n="@@fiche.cycle">Cycle de vie</h3>
      <ol class="m-0 flex list-none gap-1 p-0" i18n-aria-label="@@fiche.cycle" aria-label="Cycle de vie">
        @for (etape of etapes; track etape; let i = $index) {
          <li class="flex flex-1 flex-col gap-1.5" [attr.aria-current]="i === etape_() ? 'step' : null">
            <span class="h-1.5 rounded-full" [class]="i < etape_() ? 'bg-success' : i === etape_() ? 'bg-accent' : 'bg-surface-2'"></span>
            <span class="text-[11px] font-medium text-text-2">{{ etape }}</span>
          </li>
        }
      </ol>

      <h3 class="m-0 font-display text-sm font-semibold" i18n="@@fiche.interventions">Interventions</h3>
      @if (interventions().length === 0) {
        <p class="m-0 text-caption text-text-2" i18n="@@fiche.appairages.vide">Ce bracelet n'a encore été porté par aucun enfant.</p>
      } @else {
        <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-caption font-medium text-text-2">
          @for (ligne of interventions(); track $index) {
            <li class="tabular-nums">{{ ligne }}</li>
          }
        </ul>
      }

      <div class="mt-auto flex flex-wrap gap-2 pt-2">
        @if (f.bracelet.statut === 'ACTIF' || f.bracelet.statut === 'PERDU') {
          <button fg-button variante="secondary" type="button" (click)="action.set('retour')">{{ actions.retour.bouton }}</button>
        }
        @if (f.bracelet.statut === 'EN_SAV') {
          <button fg-button type="button" (click)="action.set('remise')">{{ actions.remise.bouton }}</button>
        }
        @if (f.bracelet.statut !== 'REFORME' && f.bracelet.statut !== 'ACTIF') {
          <button fg-button variante="danger" type="button" (click)="action.set('reforme')">{{ actions.reforme.bouton }}</button>
        }
      </div>
    } @else if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    } @else {
      <fg-skeleton forme="carte" />
      <fg-skeleton forme="carte" />
    }

    <fg-sheet [titre]="action() ? actions[action()!].titre : ''" [ouverte]="action() !== null" (fermee)="action.set(null)">
      @if (action(); as a) {
        <p class="m-0 text-body text-text-2">{{ actions[a].texte }}</p>
        <button fg-button type="button" [variante]="a === 'reforme' ? 'danger' : 'primary'" [chargement]="enCoursDAction()" (click)="executer(a)">{{ actions[a].bouton }}</button>
        <button fg-button variante="ghost" type="button" (click)="action.set(null)" i18n="@@commun.annuler">Annuler</button>
      }
    </fg-sheet>
  `,
  host: { class: 'contents' },
})
export class FicheBracelet {
  readonly numero = input.required<string>();
  /** Dernier état technique connu, s'il est en service. */
  readonly etat = input<EtatTechniqueBracelet | null>(null);
  /** Émis après un retour, une remise en stock ou un retrait, pour que le parc se mette à jour. */
  readonly modifie = output<void>();

  private readonly client = inject(ClientSav);
  private readonly router = inject(Router);

  protected readonly libelles = LIBELLES_STATUT;
  protected readonly tons = TONS_STATUT;
  protected readonly actions = ACTIONS;
  protected readonly etapes = ETAPES;
  protected readonly jour = jour;

  protected readonly fiche = signal<FicheBraceletParc | null>(null);
  protected readonly carte = signal<CarteActivationParc | null>(null);
  protected readonly action = signal<Action | null>(null);
  protected readonly enCoursDAction = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly etape_ = computed(() => ETAPE[this.fiche()?.bracelet.statut ?? 'EN_STOCK']);
  /** Premier appairage : la mise en service de l'unité. */
  protected readonly miseEnService = computed(() => this.fiche()?.appairages.at(-1)?.debut ?? null);
  /** Appairages du plus récent au plus ancien, une ligne par événement, sans l'enfant. */
  protected readonly interventions = computed(() =>
    (this.fiche()?.appairages ?? []).flatMap((periode) => [
      ...(periode.fin
        ? [`${jour(periode.fin)} · ${$localize`:@@fiche.intervention.fin:fin d'appairage`}${periode.motifFin ? ` · ${LIBELLES_MOTIF_FIN[periode.motifFin]}` : ''}`]
        : []),
      `${jour(periode.debut)} · ${$localize`:@@fiche.intervention.appairage:appairage`}${periode.fin ? '' : ` · ${$localize`:@@fiche.enCours:en cours`}`}`,
    ]),
  );

  constructor() {
    effect(() => {
      this.carte.set(null);
      this.fiche.set(null);
      this.erreur.set(null);
      this.charger(this.numero());
    });
  }

  protected executer(action: Action): void {
    if (this.enCoursDAction()) {
      return;
    }
    this.enCoursDAction.set(true);
    this.erreur.set(null);
    const numero = this.numero();
    const appel: Observable<unknown> =
      action === 'retour' ? this.client.retourner(numero) : action === 'remise' ? this.client.remettreEnStock(numero) : this.client.reformer(numero);
    appel.subscribe({
      next: (reponse) => {
        this.enCoursDAction.set(false);
        this.action.set(null);
        this.carte.set(action === 'remise' ? (reponse as CarteActivationParc) : null);
        this.charger(numero);
        this.modifie.emit();
      },
      error: (cause: unknown) => {
        this.enCoursDAction.set(false);
        this.action.set(null);
        this.signaler(cause);
      },
    });
  }

  private charger(numero: string): void {
    this.client.fiche(numero).subscribe({
      next: (fiche) => this.fiche.set(fiche),
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
