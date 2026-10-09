import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AbonnementPush, ClientNotifications } from 'api';

/**
 * Où en sont les notifications push sur cet appareil.
 * - `indisponibles` : le navigateur ne les connaît pas, ou le serveur n'en envoie pas ;
 * - `a-activer` : possibles, pas encore demandées ;
 * - `refusees` : l'autorisation a été refusée dans le navigateur, seul l'utilisateur peut la rétablir ;
 * - `actives` : ce navigateur est abonné.
 */
export type EtatPush = 'inconnu' | 'indisponibles' | 'a-activer' | 'refusees' | 'actives';

/**
 * Abonnement de ce navigateur aux notifications push. Le service worker n'est enregistré qu'à l'activation :
 * rien n'est installé tant que le parent ne l'a pas demandé.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsPush {
  private readonly client = inject(ClientNotifications);

  readonly etat = signal<EtatPush>('inconnu');

  /** Relit l'état réel du navigateur ; à appeler à l'ouverture d'un écran qui l'affiche. */
  async actualiser(): Promise<void> {
    if (!this.navigateurCompatible()) {
      this.etat.set('indisponibles');
      return;
    }
    if (Notification.permission === 'denied') {
      this.etat.set('refusees');
      return;
    }
    const enregistrement = await navigator.serviceWorker.getRegistration();
    const abonnement = await enregistrement?.pushManager.getSubscription();
    this.etat.set(abonnement && Notification.permission === 'granted' ? 'actives' : 'a-activer');
  }

  /** Demande l'autorisation, abonne le navigateur et enregistre l'abonnement auprès du serveur. */
  async activer(): Promise<void> {
    if (!this.navigateurCompatible()) {
      this.etat.set('indisponibles');
      return;
    }
    let clePublique: string;
    try {
      clePublique = await firstValueFrom(this.client.clePublique());
    } catch {
      // Le serveur n'envoie pas de push : les alertes continueront d'arriver par SMS.
      this.etat.set('indisponibles');
      return;
    }
    if ((await Notification.requestPermission()) !== 'granted') {
      this.etat.set(Notification.permission === 'denied' ? 'refusees' : 'a-activer');
      return;
    }
    const enregistrement = await navigator.serviceWorker.register('/sw.js');
    await navigator.serviceWorker.ready;
    const abonnement = await enregistrement.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: octets(clePublique) });
    await firstValueFrom(this.client.abonner(abonnement.toJSON() as AbonnementPush));
    this.etat.set('actives');
  }

  /** Désabonne ce navigateur, côté serveur puis côté navigateur. */
  async desactiver(): Promise<void> {
    const enregistrement = await navigator.serviceWorker.getRegistration();
    const abonnement = await enregistrement?.pushManager.getSubscription();
    if (abonnement) {
      await firstValueFrom(this.client.desabonner(abonnement.endpoint)).catch(() => undefined);
      await abonnement.unsubscribe();
    }
    this.etat.set('a-activer');
  }

  private navigateurCompatible(): boolean {
    return typeof navigator !== 'undefined' && 'serviceWorker' in navigator && typeof PushManager !== 'undefined' && typeof Notification !== 'undefined';
  }
}

/** Clé publique base64url (65 octets) vers le tableau d'octets attendu par `PushManager.subscribe`. */
export function octets(base64url: string): Uint8Array<ArrayBuffer> {
  const base64 = (base64url + '='.repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const brut = atob(base64);
  const tableau = new Uint8Array(new ArrayBuffer(brut.length));
  for (let i = 0; i < brut.length; i++) {
    tableau[i] = brut.charCodeAt(i);
  }
  return tableau;
}
