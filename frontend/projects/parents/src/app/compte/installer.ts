import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { FgBanniere, FgBouton, FgIcon, FgInterrupteur } from 'ui';

import { NotificationsPush } from '../commun/notifications-push';

/** Invite d'installation que le navigateur remet à la page quand l'application est installable. */
interface InviteInstallation extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * Installer l'application et activer les notifications (écran 15). Les notifications d'alerte se règlent ici ;
 * refusées ou indisponibles, les alertes continuent d'arriver par SMS, et l'écran le dit.
 */
@Component({
  selector: 'app-installer',
  imports: [ReactiveFormsModule, RouterLink, FgBanniere, FgBouton, FgIcon, FgInterrupteur],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="-ml-2.5 grid size-11 place-items-center rounded-md focus-visible:outline-2 focus-visible:outline-accent" routerLink="/" i18n-aria-label="@@etape.retour" aria-label="Retour">
      <fg-icon nom="retour" [taille]="22" />
    </a>
    <div class="flex flex-col gap-1.5">
      <h1 class="m-0 text-h2 font-bold tracking-tight" i18n="@@installer.titre">Gardez FasoGuardian à portée de main</h1>
      <p class="m-0 text-body text-text-2" i18n="@@installer.texte">Ajoutez l'application à votre écran d'accueil. Elle pèse moins de 1 Mo et fonctionne même avec peu de réseau.</p>
    </div>

    <section class="flex flex-col gap-3 rounded-lg border border-line bg-surface p-4">
      <fg-switch [formControl]="notifications" i18n-libelle="@@installer.notifications" libelle="Notifications d'alerte">
        <strong class="text-body font-semibold" i18n="@@installer.notifications">Notifications d'alerte</strong>
        <span class="text-label text-text-2" i18n="@@installer.notifications.texte">Indispensables pour le SOS. Doublées par SMS.</span>
      </fg-switch>
      @switch (push.etat()) {
        @case ('actives') {
          <fg-banner ton="succes" i18n="@@installer.actives">Les alertes arrivent sur cet appareil, même application fermée.</fg-banner>
        }
        @case ('refusees') {
          <fg-banner ton="attention" i18n="@@installer.refusees">Les notifications sont bloquées pour ce site dans votre navigateur. Autorisez-les dans ses réglages ; en attendant, les alertes arrivent par SMS.</fg-banner>
        }
        @case ('indisponibles') {
          <fg-banner ton="info" i18n="@@installer.indisponibles">Cet appareil ne reçoit pas de notifications de l'application. Les alertes vous parviennent par SMS.</fg-banner>
        }
      }
      @if (erreur(); as message) {
        <fg-banner ton="erreur">{{ message }}</fg-banner>
      }
    </section>

    @if (installee()) {
      <fg-banner ton="succes" i18n="@@installer.installee">L'application est installée sur cet appareil.</fg-banner>
    } @else if (!invite()) {
      <p class="m-0 text-label text-text-2" i18n="@@installer.manuel">Pour l'installer, ouvrez le menu de votre navigateur puis choisissez « Ajouter à l'écran d'accueil ».</p>
    }

    <div class="mt-auto flex flex-col gap-2">
      @if (invite() && !installee()) {
        <button fg-button taille="lg" type="button" (click)="installer()" i18n="@@installer.ajouter">Ajouter à l'écran d'accueil</button>
      }
      <a class="grid min-h-12 place-items-center text-body font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent" routerLink="/" i18n="@@installer.plusTard">Plus tard</a>
    </div>
  `,
  host: { class: 'mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-5 pt-6 pb-7 lg:mx-0 lg:max-w-2xl lg:px-10 lg:pt-10' },
})
export class Installer {
  protected readonly push = inject(NotificationsPush);

  protected readonly notifications = new FormControl(false, { nonNullable: true });
  protected readonly invite = signal<InviteInstallation | null>(null);
  protected readonly installee = signal(typeof matchMedia !== 'undefined' && matchMedia('(display-mode: standalone)').matches);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    void this.push.actualiser().then(() => this.refleter());
    this.notifications.valueChanges.subscribe((actives) => void this.basculer(actives));

    const surInvite = (evenement: Event) => {
      // Le navigateur garde son invite pour le moment où le parent appuie sur le bouton.
      evenement.preventDefault();
      this.invite.set(evenement as InviteInstallation);
    };
    const surInstallation = () => this.installee.set(true);
    window.addEventListener('beforeinstallprompt', surInvite);
    window.addEventListener('appinstalled', surInstallation);
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('beforeinstallprompt', surInvite);
      window.removeEventListener('appinstalled', surInstallation);
    });
  }

  protected async installer(): Promise<void> {
    const invite = this.invite();
    if (invite) {
      await invite.prompt();
      if ((await invite.userChoice).outcome === 'accepted') {
        this.installee.set(true);
      }
      this.invite.set(null);
    }
  }

  private async basculer(actives: boolean): Promise<void> {
    this.erreur.set(null);
    try {
      await (actives ? this.push.activer() : this.push.desactiver());
    } catch {
      this.erreur.set($localize`:@@installer.erreur:Les notifications n'ont pas pu être réglées. Réessayez dans un instant.`);
      await this.push.actualiser();
    }
    this.refleter();
  }

  /** L'interrupteur montre l'état réel ; il est verrouillé quand le parent ne peut rien y changer d'ici. */
  private refleter(): void {
    const etat = this.push.etat();
    this.notifications.setValue(etat === 'actives', { emitEvent: false });
    if (etat === 'refusees' || etat === 'indisponibles') {
      this.notifications.disable({ emitEvent: false });
    } else {
      this.notifications.enable({ emitEvent: false });
    }
  }
}
