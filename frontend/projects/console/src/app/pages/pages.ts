import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';

import { RoleInterne, Session } from 'api';
import { FgBouton, FgIcon } from 'ui';

import { LIBELLES_ROLES } from '../commun/acces';

/** Accès refusé (écran 58) : l'agent sait pourquoi, et que la tentative est tracée. */
@Component({
  selector: 'app-refuse',
  imports: [FgBouton, FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="m-auto flex max-w-md flex-col items-start gap-3">
      <span class="grid size-14 place-items-center rounded-lg border border-line bg-surface text-text" aria-hidden="true"><fg-icon nom="bouclier" [taille]="26" /></span>
      <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@refuse.titre">Accès refusé</h1>
      <p class="m-0 text-body text-text-2">{{ message() }}</p>
      <button fg-button type="button" (click)="retour()" i18n="@@refuse.retour">Retour à mon tableau de bord</button>
      <span class="text-caption text-text-3" i18n="@@refuse.aide">Besoin d'un accès ? Demandez-le à un administrateur.</span>
    </div>
  `,
  host: { class: 'contents' },
})
export class Refuse {
  /** Rôle exigé par l'écran visé (paramètre de requête). */
  readonly role = input<RoleInterne>();
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  protected retour(): void {
    void this.router.navigate(['/']);
  }

  protected readonly message = computed(() => {
    const miens = this.session.roles().map((role) => LIBELLES_ROLES[role as RoleInterne] ?? role).join(', ');
    const requis = this.role();
    return requis && LIBELLES_ROLES[requis]
      ? $localize`:@@refuse.role:Votre rôle « ${miens}:roles: » ne permet pas d'ouvrir cet écran, réservé au rôle « ${LIBELLES_ROLES[requis]}:requis: ».`
      : $localize`:@@refuse.generique:Votre rôle « ${miens}:roles: » ne permet pas cette action. La tentative a été enregistrée dans le journal d'audit.`;
  });
}
