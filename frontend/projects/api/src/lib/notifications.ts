import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/** Abonnement d'un navigateur, tel que le rend `PushSubscription.toJSON()`. */
export interface AbonnementPush {
  readonly endpoint: string;
  readonly keys: { readonly p256dh: string; readonly auth: string };
}

/** Notification reçue, relue dans l'application (les alertes ont leur propre écran). */
export interface NotificationRecue {
  readonly id: string;
  readonly modele: string;
  readonly titre: string;
  readonly texte: string;
  /** Écran de l'application que la notification ouvre. */
  readonly lien: string | null;
  readonly creeeLe: string;
}

/** Client des notifications push : clé du serveur d'application, abonnement et désabonnement du navigateur. */
@Injectable({ providedIn: 'root' })
export class ClientNotifications {
  private readonly http = inject(HttpClient);

  /** Clé publique à passer à `PushManager.subscribe` ; échoue si le serveur n'envoie pas de push. */
  clePublique(): Observable<string> {
    return this.http.get<{ clePublique: string }>('/api/v1/notifications/cle-publique').pipe(map((reponse) => reponse.clePublique));
  }

  /** Trente dernières notifications, hors alertes. */
  recues(): Observable<NotificationRecue[]> {
    return this.http.get<NotificationRecue[]>('/api/v1/notifications');
  }

  abonner(abonnement: AbonnementPush): Observable<void> {
    return this.http.post<void>('/api/v1/notifications/abonnements', abonnement);
  }

  desabonner(endpoint: string): Observable<void> {
    return this.http.post<void>('/api/v1/notifications/desabonnement', { endpoint });
  }
}
