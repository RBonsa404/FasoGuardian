import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, InjectionToken, effect, inject, input, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { ClientAuthentification, ClientConsole, Session } from 'api';
import { FgBanniere, FgBouton, FgChamp, FgCode, FgIcon } from 'ui';

import { erreurLisible } from '../commun/acces';

/** Inactivité au-delà de laquelle la console se verrouille (FG-DOC-06 : 30 minutes pour un agent). */
export const INACTIVITE_MAXIMALE_MS = new InjectionToken<number>('INACTIVITE_MAXIMALE_MS', {
  providedIn: 'root',
  factory: () => 30 * 60_000,
});

const ACTIVITE = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const;

/**
 * Session verrouillée (écran 55) : après 30 minutes sans geste de l'agent, ou quand le serveur a laissé expirer
 * la session, l'écran en cours est masqué et l'agent reprend où il en était en se réauthentifiant. La maquette
 * ne demande que le code TOTP ; le mot de passe est aussi exigé, pour que la reprise reste à deux facteurs
 * (ADR 0038).
 */
@Component({
  selector: 'app-verrou',
  imports: [ReactiveFormsModule, FgBanniere, FgBouton, FgChamp, FgCode, FgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #fenetre
      class="m-auto w-full max-w-sm rounded-lg border border-line bg-surface p-7 text-text shadow-e2 backdrop:bg-bg/70 backdrop:backdrop-blur-md"
      i18n-aria-label="@@verrou.titre"
      aria-label="Session verrouillée"
      (cancel)="$event.preventDefault()"
    >
      <form class="flex flex-col gap-4" (submit)="$event.preventDefault(); deverrouiller()">
        <fg-icon class="text-text" nom="cadenas" [taille]="24" />
        <h2 class="m-0 text-h3 font-semibold" i18n="@@verrou.titre">Session verrouillée</h2>
        <p class="m-0 text-label text-text-2" i18n="@@verrou.texte">30 min d'inactivité. Saisissez votre mot de passe et votre code TOTP pour reprendre où vous en étiez.</p>
        <span class="text-label font-semibold break-all">{{ identifiant() }}</span>
        <fg-input type="password" i18n-libelle="@@connexion.motDePasse" libelle="Mot de passe" autocomplete="current-password" [longueurMax]="128" [formControl]="motDePasse" />
        <fg-otp i18n-libelle="@@code.libelle" libelle="Code à 6 chiffres" [formControl]="code" />
        @if (erreur(); as message) {
          <fg-banner ton="erreur">{{ message }}</fg-banner>
        }
        <button fg-button type="submit" [chargement]="enCours()" i18n="@@verrou.action">Déverrouiller</button>
      </form>
    </dialog>
  `,
  host: { class: 'contents' },
})
export class Verrou {
  /** Identifiant de connexion de l'agent, rappelé pour la reprise. */
  readonly identifiant = input.required<string>();

  private readonly session = inject(Session);
  private readonly client = inject(ClientConsole);
  private readonly authentification = inject(ClientAuthentification);
  private readonly fenetre = viewChild.required<ElementRef<HTMLDialogElement>>('fenetre');

  protected readonly motDePasse = new FormControl('', { nonNullable: true });
  protected readonly code = new FormControl('', { nonNullable: true });
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    const delai = inject(INACTIVITE_MAXIMALE_MS);
    let minuteur: ReturnType<typeof setTimeout> | undefined;
    const relancer = () => {
      clearTimeout(minuteur);
      if (!this.session.reauthentificationRequise()) {
        minuteur = setTimeout(() => this.verrouiller(), delai);
      }
    };
    ACTIVITE.forEach((evenement) => document.addEventListener(evenement, relancer, { passive: true }));
    relancer();
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(minuteur);
      ACTIVITE.forEach((evenement) => document.removeEventListener(evenement, relancer));
    });

    effect(() => {
      const fenetre = this.fenetre().nativeElement;
      if (this.session.reauthentificationRequise() && !fenetre.open) {
        // showModal rend le reste de la page inerte : l'écran verrouillé ne se lit ni ne s'utilise.
        typeof fenetre.showModal === 'function' ? fenetre.showModal() : fenetre.setAttribute('open', '');
      } else if (!this.session.reauthentificationRequise() && fenetre.open) {
        typeof fenetre.close === 'function' ? fenetre.close() : fenetre.removeAttribute('open');
        relancer();
      }
    });
  }

  /**
   * Verrou d'inactivité : la session est aussi close côté serveur, sans quoi un rafraîchissement silencieux du
   * jeton rouvrirait la console sans que l'agent se réauthentifie.
   */
  private verrouiller(): void {
    this.session.signalerExpiration();
    this.authentification.deconnecter().subscribe({
      // La déconnexion ferme la session locale ; le verrou, lui, doit rester affiché.
      next: () => this.session.signalerExpiration(),
      error: () => this.session.signalerExpiration(),
    });
  }

  protected deverrouiller(): void {
    if (this.enCours()) {
      return;
    }
    if (!this.motDePasse.value || this.code.value.length !== 6) {
      this.erreur.set($localize`:@@verrou.incomplet:Saisissez votre mot de passe et les 6 chiffres du code.`);
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.client.connecter(this.identifiant(), this.motDePasse.value, this.code.value).subscribe({
      next: () => {
        this.enCours.set(false);
        this.motDePasse.setValue('');
        this.code.setValue('');
      },
      error: (cause: unknown) => {
        this.enCours.set(false);
        this.code.setValue('');
        this.erreur.set(erreurLisible(cause).message);
      },
    });
  }
}
