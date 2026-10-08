import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { FgIcon } from 'ui';

/**
 * Gabarit des parcours en étapes (inscription, vérification) : une question par écran, barre de
 * progression, action principale en bas d'écran (écrans 10 et 11 du design).
 */
@Component({
  selector: 'app-etape',
  imports: [FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-3 px-5 pt-3.5">
      <button
        type="button"
        class="-ml-2.5 grid size-11 place-items-center rounded-md text-text focus-visible:outline-2 focus-visible:outline-accent"
        i18n-aria-label="@@etape.retour"
        aria-label="Retour"
        (click)="retour.emit()"
      >
        <fg-icon nom="retour" [taille]="22" />
      </button>
      <div
        class="h-1 flex-1 overflow-hidden rounded-full bg-surface-2"
        role="progressbar"
        aria-valuemin="0"
        [attr.aria-valuemax]="total()"
        [attr.aria-valuenow]="numero()"
      >
        <div
          class="h-full rounded-full bg-accent transition-[width] duration-(--duration-base) ease-standard"
          [style.width.%]="progression()"
        ></div>
      </div>
      <span class="text-caption font-semibold text-text-3 tabular-nums">{{ numero() }}/{{ total() }}</span>
    </div>
    <div class="flex flex-col gap-2 px-5 pt-6">
      <span class="text-caption font-semibold tracking-widest text-accent uppercase">{{ surtitre() }}</span>
      <h1 class="m-0 text-titre-ecran font-bold tracking-tight">{{ titre() }}</h1>
      <p class="m-0 text-body text-text-2">{{ texte() }}</p>
    </div>
    <div class="flex flex-1 flex-col gap-3 p-5"><ng-content /></div>
    <div class="flex flex-col gap-2 px-5 pt-4 pb-7"><ng-content select="[pied]" /></div>
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col' },
})
export class Etape {
  readonly surtitre = input.required<string>();
  readonly titre = input.required<string>();
  readonly texte = input.required<string>();
  readonly numero = input.required<number>();
  readonly total = input.required<number>();
  readonly retour = output<void>();

  protected readonly progression = computed(() => (100 * this.numero()) / this.total());
}
