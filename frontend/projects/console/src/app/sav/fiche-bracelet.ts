import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { CarteActivationParc, ClientSav, FicheBraceletParc } from 'api';
import { FgBadge, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';
import { LIBELLES_MOTIF_FIN, LIBELLES_STATUT, TONS_STATUT, jour, jourEtHeure } from './libelles';

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
    titre: $localize`:@@fiche.reforme.titre:Réformer ce bracelet ?`,
    texte: $localize`:@@fiche.reforme.texte:Le bracelet est retiré définitivement du parc et son certificat est révoqué. Cette action ne se défait pas.`,
    bouton: $localize`:@@fiche.reforme:Réformer`,
  },
};

/**
 * Fiche d'un bracelet (écran 65, US-SAV-002) : identité matérielle, historique des appairages sans le nom des
 * enfants, et mouvements de stock. La consultation est journalisée.
 */
@Component({
  selector: 'app-fiche-bracelet',
  imports: [RouterLink, FgBadge, FgBanniere, FgBouton, FgFeuille, FgIcon, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="flex items-center gap-1.5 self-start text-label font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" routerLink="/sav/parc">
      <fg-icon nom="retour" [taille]="16" /><ng-container i18n="@@fiche.parc">Parc de bracelets</ng-container>
    </a>
    @if (fiche(); as f) {
      <div class="flex flex-wrap items-center gap-3">
        <h1 class="m-0 font-mono text-titre-ecran font-bold tracking-tight">{{ f.bracelet.numeroSerie }}</h1>
        <fg-badge [ton]="tons[f.bracelet.statut]">{{ libelles[f.bracelet.statut] }}</fg-badge>
        @if (f.bracelet.certificatRevoque) {
          <fg-badge ton="attention" i18n="@@fiche.revoque">Certificat révoqué</fg-badge>
        }
      </div>
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
      @if (carte(); as c) {
        <section class="flex flex-col gap-2 rounded-lg border border-accent bg-surface p-4" role="status">
          <strong class="text-body font-semibold" i18n="@@fiche.carte.titre">Carte d'activation à imprimer — affichée une seule fois</strong>
          <dl class="m-0 grid grid-cols-[160px_1fr] gap-y-1.5 text-label">
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

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <section class="flex flex-col gap-2" aria-labelledby="titre-materiel">
          <h2 id="titre-materiel" class="m-0 text-h3 font-semibold" i18n="@@fiche.materiel">Matériel</h2>
          <dl class="m-0 flex flex-col rounded-lg border border-line bg-surface">
            <div class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3"><dt class="text-label font-medium" i18n="@@fiche.imei">IMEI</dt><dd class="m-0 font-mono text-caption text-text-2">{{ f.imeiMasque }}</dd></div>
            <div class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3"><dt class="text-label font-medium" i18n="@@parc.col.materiel">Matériel</dt><dd class="m-0 text-label text-text-2">{{ f.bracelet.revisionMaterielle }}</dd></div>
            <div class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3"><dt class="text-label font-medium" i18n="@@parc.col.logiciel">Logiciel</dt><dd class="m-0 text-label text-text-2 tabular-nums">{{ f.bracelet.versionLogiciel }}</dd></div>
            <div class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3"><dt class="text-label font-medium" i18n="@@parc.col.garantie">Garantie</dt><dd class="m-0 text-label text-text-2">{{ jour(f.bracelet.garantieJusquAu) }}</dd></div>
            <div class="flex items-baseline justify-between gap-4 px-4 py-3"><dt class="text-label font-medium" i18n="@@fiche.certificat">Certificat</dt><dd class="m-0 max-w-64 truncate font-mono text-caption text-text-2" [attr.title]="f.empreinteCertificat">{{ f.empreinteCertificat }}</dd></div>
          </dl>
        </section>
        <section class="flex flex-col gap-2" aria-labelledby="titre-appairages">
          <h2 id="titre-appairages" class="m-0 text-h3 font-semibold" i18n="@@fiche.appairages">Appairages</h2>
          @if (f.appairages.length === 0) {
            <p class="m-0 rounded-lg border border-line bg-surface p-4 text-body text-text-2" i18n="@@fiche.appairages.vide">Ce bracelet n'a encore été porté par aucun enfant.</p>
          } @else {
            <ol class="m-0 flex list-none flex-col rounded-lg border border-line bg-surface p-0">
              @for (periode of f.appairages; track periode.debut) {
                <li class="flex items-baseline justify-between gap-4 border-b border-line px-4 py-3 text-label last:border-b-0">
                  <span class="tabular-nums">{{ jourEtHeure(periode.debut) }} → {{ periode.fin ? jourEtHeure(periode.fin) : enCours }}</span>
                  <span class="text-text-2">{{ periode.motifFin ? motifs[periode.motifFin] : '' }}</span>
                </li>
              }
            </ol>
          }
        </section>
      </div>

      <div class="flex flex-wrap gap-2">
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

  private readonly client = inject(ClientSav);
  private readonly router = inject(Router);

  protected readonly libelles = LIBELLES_STATUT;
  protected readonly tons = TONS_STATUT;
  protected readonly motifs = LIBELLES_MOTIF_FIN;
  protected readonly actions = ACTIONS;
  protected readonly jour = jour;
  protected readonly jourEtHeure = jourEtHeure;
  protected readonly enCours = $localize`:@@fiche.enCours:en cours`;

  protected readonly fiche = signal<FicheBraceletParc | null>(null);
  protected readonly carte = signal<CarteActivationParc | null>(null);
  protected readonly action = signal<Action | null>(null);
  protected readonly enCoursDAction = signal(false);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    effect(() => this.charger(this.numero()));
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
