import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ClientTableau, GroupeIndicateurs, RoleInterne } from 'api';
import { FgBanniere, FgSquelette } from 'ui';

import { erreurLisible, estRefus } from '../commun/acces';
import { libeller } from '../commun/indicateurs';

interface Raccourci {
  readonly lien: string;
  readonly libelle: string;
  readonly texte: string;
}

interface Presentation {
  readonly titre: string;
  readonly sousTitre: string;
  readonly raccourcis: readonly Raccourci[];
}

const PRESENTATIONS: Record<RoleInterne, Presentation> = {
  KYC: {
    titre: $localize`:@@tableau.kyc:Dossiers KYC`,
    sousTitre: $localize`:@@tableau.kyc.texte:Objectif : décision sous 48 h ouvrées.`,
    raccourcis: [
      { lien: '/kyc', libelle: $localize`:@@nav.kyc:File KYC`, texte: $localize`:@@tableau.fileKyc.texte:Dossiers à instruire, les plus anciens d'abord.` },
      { lien: '/kyc/litiges', libelle: $localize`:@@nav.litiges:Litiges de filiation`, texte: $localize`:@@tableau.litiges.texte:Contestations de filiation et gels conservatoires.` },
    ],
  },
  SUPPORT: {
    titre: $localize`:@@tableau.support:Support`,
    sousTitre: $localize`:@@tableau.support.texte:Demandes des parents et base de connaissances.`,
    raccourcis: [
      { lien: '/support/tickets', libelle: $localize`:@@nav.tickets:Tickets support`, texte: $localize`:@@tableau.tickets.texte:Demandes des parents, à lire et auxquelles répondre.` },
      { lien: '/support/faq', libelle: $localize`:@@nav.faq:Base de connaissances`, texte: $localize`:@@tableau.faq.texte:Réponses aux questions fréquentes, publiées pour les parents.` },
    ],
  },
  SAV: {
    titre: $localize`:@@tableau.sav:Service après-vente`,
    sousTitre: $localize`:@@tableau.sav.texte:État des bracelets seulement : ni l'enfant ni sa famille.`,
    raccourcis: [
      { lien: '/sav/muets', libelle: $localize`:@@nav.muets:Bracelets muets`, texte: $localize`:@@tableau.muets.texte:Tickets ouverts par la supervision, à prendre en charge.` },
      { lien: '/sav/parc', libelle: $localize`:@@nav.parc:Parc de bracelets`, texte: $localize`:@@tableau.parc.texte:Cycle de vie de chaque unité, retours et remises en stock.` },
      { lien: '/sav/ota', libelle: $localize`:@@nav.ota:Campagnes OTA`, texte: $localize`:@@tableau.ota.texte:Mises à jour signées, déployées par vagues.` },
    ],
  },
  FDS: {
    titre: $localize`:@@tableau.fds:Forces de sécurité`,
    sousTitre: $localize`:@@tableau.fds.texte:Un signalement se retrouve par sa référence ; il n'en existe aucune liste.`,
    raccourcis: [{ lien: '/fds', libelle: $localize`:@@nav.fds:Signalements`, texte: $localize`:@@tableau.fds.lien:Retrouver un signalement et en accuser réception.` }],
  },
  ADMIN: {
    titre: $localize`:@@tableau.admin:Administration`,
    sousTitre: $localize`:@@tableau.admin.texte:Habilitations, sécurité, journal d'audit et conformité.`,
    raccourcis: [
      { lien: '/admin/agents', libelle: $localize`:@@nav.agents:Agents et rôles`, texte: $localize`:@@tableau.agents.texte:Périmètre de chaque agent, création et suspension.` },
      { lien: '/admin/securite', libelle: $localize`:@@nav.securite:Sécurité`, texte: $localize`:@@tableau.securite.texte:Sources bloquées pour énumération de QR, accès refusés.` },
      { lien: '/admin/audit', libelle: $localize`:@@nav.audit:Journal d'audit`, texte: $localize`:@@tableau.audit.texte:Accès sensibles, refus et état de la chaîne d'empreintes.` },
      { lien: '/conformite', libelle: $localize`:@@nav.conformite:Conformité CIL`, texte: $localize`:@@tableau.conformite.texte:Durées de conservation, purges, demandes d'effacement, rapport mensuel.` },
    ],
  },
};

const JOUR = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

/**
 * Tableau de bord (écran 57) : pour chacun des rôles de l'agent, les indicateurs de sa file de travail, puis les
 * accès à ses écrans. Les indicateurs ne sont que des décomptes : aucune donnée personnelle.
 */
@Component({
  selector: 'app-tableau',
  imports: [RouterLink, FgBanniere, FgSquelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-1">
      <h1 class="m-0 font-display text-[26px] font-bold tracking-tight" i18n="@@tableau.titre">Tableau de bord</h1>
      <span class="text-body text-text-2 first-letter:uppercase">{{ jour }}</span>
    </div>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    @if (groupes(); as liste) {
      @for (groupe of liste; track groupe.role) {
        <section class="flex flex-col gap-3" [attr.aria-labelledby]="'groupe-' + groupe.role">
          <div class="flex flex-col gap-0.5">
            <h2 class="m-0 font-display text-h3 font-semibold" [id]="'groupe-' + groupe.role">{{ presentations[groupe.role].titre }}</h2>
            <span class="text-label text-text-2">{{ presentations[groupe.role].sousTitre }}</span>
          </div>
          <dl class="m-0 grid grid-cols-2 gap-3 xl:grid-cols-4">
            @for (i of groupe.indicateurs; track i.code) {
              @let l = libeller(i);
              <div class="flex flex-col gap-1.5 rounded-[14px] border bg-surface p-4" [class]="i.alerte ? 'border-attention' : 'border-line'">
                <dt class="text-caption font-medium text-text-3">{{ l.libelle }}</dt>
                <dd class="m-0 font-display text-[28px] font-bold tabular-nums" [class.text-attention-text]="i.alerte">{{ l.valeur }}</dd>
                <dd class="m-0 text-caption font-medium text-text-2">{{ l.detail }}</dd>
              </div>
            }
          </dl>
          <div class="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-3">
            @for (r of presentations[groupe.role].raccourcis; track r.lien) {
              <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" [routerLink]="r.lien">
                <strong class="text-body font-semibold">{{ r.libelle }}</strong>
                <span class="text-label text-text-2">{{ r.texte }}</span>
              </a>
            }
          </div>
        </section>
      } @empty {
        <p class="m-0 text-body text-text-2" i18n="@@tableau.vide">Les écrans de votre rôle ne sont pas encore disponibles dans cette version.</p>
      }
    } @else if (!erreur()) {
      <div class="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <fg-skeleton forme="carte" /><fg-skeleton forme="carte" /><fg-skeleton forme="carte" /><fg-skeleton forme="carte" />
      </div>
    }
  `,
  host: { class: 'contents' },
})
export class Tableau {
  private readonly client = inject(ClientTableau);
  private readonly router = inject(Router);

  protected readonly presentations = PRESENTATIONS;
  protected readonly jour = JOUR.format(new Date());
  protected readonly groupes = signal<readonly GroupeIndicateurs[] | null>(null);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    this.client.indicateurs().subscribe({
      next: (groupes) => this.groupes.set(groupes.filter((g) => g.role in PRESENTATIONS)),
      error: (cause: unknown) => {
        if (estRefus(cause)) {
          void this.router.navigate(['/refuse']);
        } else {
          this.erreur.set(erreurLisible(cause).message);
        }
      },
    });
  }

  protected readonly libeller = libeller;
}
