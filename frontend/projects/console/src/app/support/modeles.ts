import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { ClientSupport, ModeleReponse } from 'api';
import { FgBanniere, FgBouton, FgChamp } from 'ui';

import { erreurLisible } from '../commun/acces';

/** Repère du prénom du parent dans un modèle, remplacé à l'insertion (ADR 0041). */
export const REPERE_PRENOM = '{prénom}';

/** Raccourci en cours de saisie à la fin de la réponse : « … /san » donne « san ». */
export function raccourciSaisi(texte: string): string | null {
  return /(?:^|\s)\/([a-z0-9-]*)$/.exec(texte)?.[1] ?? null;
}

/** Remplace le raccourci saisi par le texte du modèle, où le repère devient le prénom du parent s'il est connu. */
export function insererModele(texte: string, modele: ModeleReponse, prenom: string | null): string {
  const corps = prenom ? modele.texte.replaceAll(REPERE_PRENOM, prenom) : modele.texte;
  return texte.replace(/\/[a-z0-9-]*$/, corps);
}

/**
 * Modèles de réponse du support (écran 62, ADR 0041) : des textes préparés, partagés entre opérateurs, que l'on
 * insère dans une réponse en tapant leur raccourci.
 */
@Component({
  selector: 'app-modeles',
  imports: [ReactiveFormsModule, FgBanniere, FgBouton, FgChamp],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center justify-between gap-3">
      <h2 class="m-0 text-h3 font-semibold" i18n="@@modeles.titre">Modèles de réponse</h2>
      <button fg-button variante="ghost" taille="sm" type="button" (click)="fermer.emit()" i18n="@@modeles.fermer">Fermer</button>
    </div>
    <p class="m-0 text-label text-text-2" i18n="@@modeles.explication">
      Dans une réponse, tapez « / » puis le raccourci pour insérer le texte ; {{ repere }} y devient le prénom du parent. Relisez toujours avant
      d'envoyer.
    </p>
    @if (erreur(); as message) {
      <fg-banner ton="erreur">{{ message }}</fg-banner>
    }
    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[240px_1fr]">
      <ul class="m-0 flex list-none flex-col gap-1.5 p-0" i18n-aria-label="@@modeles.liste" aria-label="Modèles enregistrés">
        @for (modele of modeles(); track modele.id) {
          <li>
            <button type="button" class="flex w-full flex-col gap-0.5 rounded-md bg-surface px-3 py-2.5 text-left focus-visible:outline-2 focus-visible:outline-accent" [class]="edite()?.id === modele.id ? 'border-2 border-accent' : 'border border-line'" (click)="ouvrir(modele)">
              <span class="font-mono text-caption text-accent">/{{ modele.raccourci }}</span>
              <strong class="text-label font-semibold">{{ modele.titre }}</strong>
            </button>
          </li>
        } @empty {
          <li class="rounded-md border border-line p-3 text-label text-text-2" i18n="@@modeles.vide">Aucun modèle pour l'instant.</li>
        }
        <li>
          <button fg-button variante="secondary" taille="sm" type="button" class="w-full" (click)="nouveau()" i18n="@@modeles.nouveau">Nouveau modèle</button>
        </li>
      </ul>
      <form class="flex flex-col gap-3" (submit)="$event.preventDefault(); enregistrer()">
        <fg-input [formControl]="raccourci" i18n-libelle="@@modeles.raccourci" libelle="Raccourci" [longueurMax]="30" i18n-aide="@@modeles.raccourci.aide" aide="Lettres minuscules sans accent, chiffres et tirets : sangle, recu-paiement…" />
        <fg-input [formControl]="titre" i18n-libelle="@@modeles.titreModele" libelle="Titre" [longueurMax]="80" />
        <label class="flex flex-col gap-1.5 text-label font-semibold">
          <span i18n="@@modeles.texte">Texte</span>
          <textarea class="min-h-36 rounded-md border border-line-strong bg-surface p-3 text-label font-normal outline-none focus-visible:outline-2 focus-visible:outline-accent" maxlength="2000" [formControl]="texte"></textarea>
        </label>
        <div class="flex flex-wrap justify-end gap-2">
          @if (edite(); as m) {
            <button fg-button variante="danger" type="button" [chargement]="enCours()" (click)="supprimer(m)" i18n="@@modeles.supprimer">Supprimer</button>
          }
          <button fg-button type="submit" [chargement]="enCours()" i18n="@@modeles.enregistrer">Enregistrer le modèle</button>
        </div>
      </form>
    </div>
  `,
  host: { class: 'flex min-h-0 flex-col gap-3 rounded-lg border border-line bg-surface p-5' },
})
export class Modeles {
  private readonly client = inject(ClientSupport);

  readonly modeles = input.required<readonly ModeleReponse[]>();
  /** Un modèle a été créé, modifié ou supprimé : la liste est à relire. */
  readonly change = output<void>();
  readonly fermer = output<void>();

  protected readonly repere = REPERE_PRENOM;
  protected readonly edite = signal<ModeleReponse | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly raccourci = new FormControl('', { nonNullable: true });
  protected readonly titre = new FormControl('', { nonNullable: true });
  protected readonly texte = new FormControl('', { nonNullable: true });

  protected ouvrir(modele: ModeleReponse): void {
    this.edite.set(modele);
    this.erreur.set(null);
    this.raccourci.setValue(modele.raccourci);
    this.titre.setValue(modele.titre);
    this.texte.setValue(modele.texte);
  }

  protected nouveau(): void {
    this.edite.set(null);
    this.erreur.set(null);
    this.raccourci.setValue('');
    this.titre.setValue('');
    this.texte.setValue('');
  }

  protected enregistrer(): void {
    const saisie = { raccourci: this.raccourci.value.trim(), titre: this.titre.value.trim(), texte: this.texte.value.trim() };
    if (!saisie.raccourci || !saisie.titre || !saisie.texte) {
      this.erreur.set($localize`:@@modeles.incomplet:Donnez un raccourci, un titre et un texte au modèle.`);
      return;
    }
    const modele = this.edite();
    this.envoyer(modele ? this.client.modifierModele(modele.id, saisie) : this.client.creerModele(saisie), (enregistre) => this.ouvrir(enregistre));
  }

  protected supprimer(modele: ModeleReponse): void {
    this.envoyer(this.client.supprimerModele(modele.id), () => this.nouveau());
  }

  private envoyer<T>(requete: Observable<T>, ensuite: (reponse: T) => void): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    requete.subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        ensuite(reponse);
        this.change.emit();
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }
}
