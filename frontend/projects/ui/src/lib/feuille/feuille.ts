import { ChangeDetectionStrategy, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';

/**
 * Feuille de bas d'écran (fg-sheet). S'appuie sur l'élément natif <dialog> : focus piégé, Échap pour
 * fermer, arrière-plan inerte. Sur grand écran, elle se centre.
 */
@Component({
  selector: 'fg-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialogue
      class="m-0 mt-auto w-full max-w-md rounded-t-xl border border-line bg-surface p-5 pb-7 text-text shadow-e3 backdrop:bg-nuit-950/70 open:animate-rise sm:m-auto sm:rounded-xl"
      [attr.aria-label]="titre()"
      (close)="fermee.emit()"
      (click)="surClic($event)"
    >
      <div class="flex flex-col gap-4">
        <h2 class="m-0 text-h3 font-semibold">{{ titre() }}</h2>
        <ng-content />
      </div>
    </dialog>
  `,
})
export class FgFeuille {
  readonly titre = input.required<string>();
  readonly ouverte = input(false);
  /** Émis à la fermeture, qu'elle vienne d'Échap, d'un clic sur l'arrière-plan ou du parent. */
  readonly fermee = output<void>();

  private readonly dialogue = viewChild.required<ElementRef<HTMLDialogElement>>('dialogue');

  constructor() {
    effect(() => {
      const dialogue = this.dialogue().nativeElement;
      if (this.ouverte() && !dialogue.open) {
        // showModal est absent de certains moteurs de test : repli sur l'attribut open.
        typeof dialogue.showModal === 'function' ? dialogue.showModal() : dialogue.setAttribute('open', '');
      } else if (!this.ouverte() && dialogue.open) {
        typeof dialogue.close === 'function' ? dialogue.close() : dialogue.removeAttribute('open');
      }
    });
  }

  protected surClic(evenement: MouseEvent): void {
    // Un clic sur l'arrière-plan atteint le <dialog> lui-même, pas son contenu.
    if (evenement.target === this.dialogue().nativeElement) {
      this.dialogue().nativeElement.close();
    }
  }
}
