import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { ActionSensible, ClientProfil } from 'api';
import { FgBouton, FgCode, FgFeuille } from 'ui';

import { erreurLisible } from './erreurs';

const DELAI_RENVOI_S = 60;

/**
 * Feuille de second facteur (écran 13, fg-otp-sheet) : à l'ouverture, un code propre à l'action est envoyé
 * par SMS ; le code saisi est remis au parent de la feuille, qui exécute l'action.
 */
@Component({
  selector: 'app-second-facteur',
  imports: [ReactiveFormsModule, FgFeuille, FgCode, FgBouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fg-sheet i18n-titre="@@secondFacteur.titre" titre="Confirmez par code SMS" [ouverte]="ouverte()" (fermee)="annule.emit()">
      <p class="m-0 text-label text-text-2">{{ explication() }}</p>
      <fg-otp i18n-libelle="@@secondFacteur.code" libelle="Code à 6 chiffres" [erreur]="erreur() ?? erreurEnvoi()" [formControl]="code" (complet)="saisi.emit($event)" />
      @if (attente() > 0) {
        <span class="text-label text-text-2" i18n="@@secondFacteur.attente">Renvoyer le code dans <strong class="text-text tabular-nums">{{ minuteur() }}</strong></span>
      } @else {
        <button fg-button variante="ghost" taille="sm" type="button" class="self-start" (click)="envoyer()" i18n="@@secondFacteur.renvoyer">
          Renvoyer le code
        </button>
      }
      <button fg-button variante="secondary" type="button" (click)="annule.emit()" i18n="@@commun.annuler">Annuler</button>
    </fg-sheet>
  `,
})
export class SecondFacteur {
  readonly action = input.required<ActionSensible>();
  readonly ouverte = input(false);
  readonly explication = input.required<string>();
  /** Message d'erreur renvoyé par l'action (code incorrect, expiré…). */
  readonly erreur = input<string | null>(null);
  readonly saisi = output<string>();
  readonly annule = output<void>();

  private readonly client = inject(ClientProfil);

  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly attente = signal(0);
  protected readonly erreurEnvoi = signal<string | null>(null);
  protected readonly minuteur = computed(() => `0:${String(this.attente()).padStart(2, '0')}`);

  constructor() {
    effect(() => {
      if (this.ouverte()) {
        this.code.setValue('');
        this.envoyer();
      }
    });
    effect(() => {
      if (this.erreur()) {
        this.code.setValue('');
      }
    });
    const minuterie = setInterval(() => this.attente.update((s) => Math.max(0, s - 1)), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected envoyer(): void {
    this.erreurEnvoi.set(null);
    this.client.demanderSecondFacteur(this.action()).subscribe({
      next: () => this.attente.set(DELAI_RENVOI_S),
      error: (cause: unknown) => {
        const lisible = erreurLisible(cause);
        // Un code récent est encore valable : ce n'est pas une erreur pour le parent.
        if (lisible.code === 'TROP_DE_REQUETES') {
          this.attente.set(DELAI_RENVOI_S);
        } else {
          this.erreurEnvoi.set(lisible.message);
        }
      },
    });
  }
}
