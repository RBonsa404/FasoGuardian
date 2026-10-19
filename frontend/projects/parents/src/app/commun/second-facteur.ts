import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { ActionSensible, ClientAuthentification, ClientProfil } from 'api';
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
    <fg-sheet i18n-titre="@@secondFacteur.titre" titre="Confirmez avec le code SMS" icone="cadenas" [ouverte]="ouverte()" (fermee)="annule.emit()">
      <p class="m-0 text-label text-text-2">
        {{ explication() }}
        @if (telephone(); as numero) {
          <ng-container i18n="@@secondFacteur.envoye">Code envoyé au {{ numero }}.</ng-container>
        }
      </p>
      <fg-otp i18n-libelle="@@secondFacteur.code" libelle="Code à 6 chiffres" [erreur]="erreur() ?? erreurEnvoi()" [formControl]="code" (complet)="saisi.emit($event)" />
      <span class="flex flex-wrap items-center gap-x-1 text-label text-text-2">
        @if (attente() > 0) {
          <span i18n="@@secondFacteur.attente">Renvoyer le code dans <strong class="text-text tabular-nums">{{ minuteur() }}</strong></span>
        } @else {
          <button fg-button variante="ghost" taille="sm" type="button" (click)="envoyer()" i18n="@@secondFacteur.renvoyer">Renvoyer le code</button>
        }
        <!-- Un code neuf permet trois essais ; après une erreur, le message du serveur dit combien il en reste. -->
        @if (!erreur()) {
          <span i18n="@@secondFacteur.essais">· 3 essais</span>
        }
      </span>
      <button fg-button taille="lg" type="button" [disabled]="!codeComplet()" (click)="saisi.emit(code.value)" i18n="@@secondFacteur.confirmer">Confirmer</button>
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
  /** Numéro masqué du compte, rappelé pour que le parent sache où chercher le SMS. */
  protected readonly telephone = signal<string | null>(null);
  private readonly saisie = signal('');
  protected readonly codeComplet = computed(() => /^\d{6}$/.test(this.saisie()));

  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly attente = signal(0);
  protected readonly erreurEnvoi = signal<string | null>(null);
  protected readonly minuteur = computed(() => `${Math.floor(this.attente() / 60)}:${String(this.attente() % 60).padStart(2, '0')}`);

  constructor() {
    this.code.valueChanges.subscribe((valeur) => this.saisie.set(valeur));
    inject(ClientAuthentification)
      .moi()
      .subscribe({ next: (compte) => this.telephone.set(compte.telephoneMasque), error: () => undefined });
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
