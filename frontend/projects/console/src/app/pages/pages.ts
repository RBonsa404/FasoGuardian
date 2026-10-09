import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { RoleInterne, Session } from 'api';
import { FgIcon } from 'ui';

import { LIBELLES_ROLES } from '../commun/acces';

/** Tableau de bord (écran 57) : point d'entrée vers les files de travail du rôle de l'agent. */
@Component({
  selector: 'app-tableau',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="m-0 text-titre-ecran font-bold tracking-tight" i18n="@@tableau.titre">Tableau de bord</h1>
    <div class="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-3">
      @if (session.roles().includes('KYC')) {
        <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" routerLink="/kyc">
          <strong class="text-body font-semibold" i18n="@@tableau.kyc">Dossiers KYC</strong>
          <span class="text-label text-text-2" i18n="@@tableau.kyc.texte">Objectif : décision sous 48 h ouvrées.</span>
        </a>
      }
      @if (session.roles().includes('SAV')) {
        <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" routerLink="/sav/muets">
          <strong class="text-body font-semibold" i18n="@@nav.muets">Bracelets muets</strong>
          <span class="text-label text-text-2" i18n="@@tableau.muets.texte">Tickets ouverts par la supervision, à prendre en charge.</span>
        </a>
        <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" routerLink="/sav/parc">
          <strong class="text-body font-semibold" i18n="@@nav.parc">Parc de bracelets</strong>
          <span class="text-label text-text-2" i18n="@@tableau.parc.texte">Cycle de vie de chaque unité, retours et remises en stock.</span>
        </a>
      }
      @if (session.roles().includes('ADMIN')) {
        <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" routerLink="/admin/agents">
          <strong class="text-body font-semibold" i18n="@@nav.agents">Agents et rôles</strong>
          <span class="text-label text-text-2" i18n="@@tableau.agents.texte">Périmètre de chaque agent, création et suspension.</span>
        </a>
        <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" routerLink="/admin/securite">
          <strong class="text-body font-semibold" i18n="@@nav.securite">Sécurité</strong>
          <span class="text-label text-text-2" i18n="@@tableau.securite.texte">Sources bloquées pour énumération de QR, accès refusés.</span>
        </a>
        <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" routerLink="/admin/audit">
          <strong class="text-body font-semibold" i18n="@@nav.audit">Journal d'audit</strong>
          <span class="text-label text-text-2" i18n="@@tableau.audit.texte">Accès sensibles, refus et état de la chaîne d'empreintes.</span>
        </a>
        <a class="flex flex-col gap-1.5 rounded-lg border border-line bg-surface p-4 hover:border-line-strong focus-visible:outline-2 focus-visible:outline-accent" routerLink="/conformite">
          <strong class="text-body font-semibold" i18n="@@nav.conformite">Conformité CIL</strong>
          <span class="text-label text-text-2" i18n="@@tableau.conformite.texte">Durées de conservation, purges, demandes d'effacement, rapport mensuel.</span>
        </a>
      }
    </div>
    @if (session.roles().length > 0 && !session.roles().includes('KYC') && !session.roles().includes('ADMIN') && !session.roles().includes('SAV')) {
      <p class="m-0 text-body text-text-2" i18n="@@tableau.vide">Les écrans de votre rôle ne sont pas encore disponibles dans cette version.</p>
    }
  `,
  host: { class: 'contents' },
})
export class Tableau {
  protected readonly session = inject(Session);
}

/** Accès refusé (écran 58) : l'agent sait pourquoi, et que la tentative est tracée. */
@Component({
  selector: 'app-refuse',
  imports: [RouterLink, FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="m-auto flex max-w-md flex-col items-center gap-3 text-center">
      <span class="grid size-14 place-items-center rounded-lg bg-attention-soft text-attention"><fg-icon nom="cadenas" [taille]="28" /></span>
      <h1 class="m-0 text-h2 font-semibold" i18n="@@refuse.titre">Accès refusé</h1>
      <p class="m-0 text-body text-text-2">{{ message() }}</p>
      <a class="text-label font-semibold text-accent" routerLink="/" i18n="@@refuse.retour">Retour à mon tableau de bord</a>
      <span class="text-caption text-text-3" i18n="@@refuse.aide">Besoin d'un accès ? Demandez-le à un administrateur.</span>
    </div>
  `,
  host: { class: 'contents' },
})
export class Refuse {
  /** Rôle exigé par l'écran visé (paramètre de requête). */
  readonly role = input<RoleInterne>();
  private readonly session = inject(Session);

  protected readonly message = computed(() => {
    const miens = this.session.roles().map((role) => LIBELLES_ROLES[role as RoleInterne] ?? role).join(', ');
    const requis = this.role();
    return requis && LIBELLES_ROLES[requis]
      ? $localize`:@@refuse.role:Votre rôle « ${miens}:roles: » ne permet pas d'ouvrir cet écran, réservé au rôle « ${LIBELLES_ROLES[requis]}:requis: ».`
      : $localize`:@@refuse.generique:Votre rôle « ${miens}:roles: » ne permet pas cette action. La tentative a été enregistrée dans le journal d'audit.`;
  });
}
