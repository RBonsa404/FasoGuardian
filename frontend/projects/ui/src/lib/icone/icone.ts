import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

import { ICONES, NomIcone } from './icones';

/**
 * Icône du design system (24 px, trait 1,75). Décorative par défaut : le texte voisin porte
 * le sens. Renseigner `libelle` uniquement lorsqu'elle est seule à transmettre l'information.
 */
@Component({
  selector: 'fg-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.75"
    stroke-linecap="round"
    stroke-linejoin="round"
    focusable="false"
    [attr.width]="taille()"
    [attr.height]="taille()"
    [attr.role]="libelle() ? 'img' : null"
    [attr.aria-label]="libelle() || null"
    [attr.aria-hidden]="libelle() ? null : 'true'"
    [innerHTML]="contenu()"
  ></svg>`,
  host: { class: 'inline-flex flex-none' },
})
export class FgIcon {
  private readonly assainisseur = inject(DomSanitizer);

  readonly nom = input.required<NomIcone>();
  readonly taille = input(24);
  readonly libelle = input<string>();

  // Les tracés sont des constantes du dépôt, issues du paquet de design : aucun contenu externe.
  protected readonly contenu = computed(() => this.assainisseur.bypassSecurityTrustHtml(ICONES[this.nom()]));
}
