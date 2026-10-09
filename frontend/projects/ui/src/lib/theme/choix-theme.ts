import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { PreferenceTheme, Theme } from './theme';

const CHOIX: readonly PreferenceTheme[] = ['systeme', 'sombre', 'clair'];

/** Choix de l'apparence : suivre le système, ou imposer le thème sombre ou clair. */
@Component({
  selector: 'fg-theme',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (choix of liste; track choix) {
      <button
        type="button"
        class="h-11 min-w-0 flex-1 truncate rounded-full px-2 text-label font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        [class]="theme.preference() === choix ? 'border-2 border-accent bg-accent-soft' : 'border border-line-strong text-text-2'"
        [attr.aria-pressed]="theme.preference() === choix"
        (click)="theme.choisir(choix)"
      >
        {{ libelles()[choix] }}
      </button>
    }
  `,
  host: { class: 'flex gap-1.5', role: 'group', '[attr.aria-label]': 'libelle()' },
})
export class FgChoixTheme {
  protected readonly theme = inject(Theme);
  protected readonly liste = CHOIX;
  /** Nom du groupe, lu par les lecteurs d'écran. */
  readonly libelle = input('Apparence');
  /** Libellés des trois choix ; l'application appelante peut fournir les siens. */
  readonly libelles = input<Record<PreferenceTheme, string>>({ systeme: 'Système', sombre: 'Sombre', clair: 'Clair' });
}
