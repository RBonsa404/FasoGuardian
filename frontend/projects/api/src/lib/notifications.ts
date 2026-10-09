import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/** Abonnement d'un navigateur, tel que le rend `PushSubscription.toJSON()`. */
export interface AbonnementPush {
  readonly endpoint: string;
  readonly keys: { readonly p256dh: string; readonly auth: string };
}

/** Client des notifications push : clé du serveur d'application, abonnement et désabonnement du navigateur. */
@Injectable({ providedIn: 'root' })
export class ClientNotifications {
  private readonly http = inject(HttpClient);

  /** Clé publique à passer à `PushManager.subscribe` ; échoue si le serveur n'envoie pas de push. */
  clePublique(): Observable<string> {
    return this.http.get<{ clePublique: string }>('/api/v1/notifications/cle-publique').pipe(map((reponse) => reponse.clePublique));
  }

  abonner(abonnement: AbonnementPush): Observable<void> {
    return this.http.post<void>('/api/v1/notifications/abonnements', abonnement);
  }

  desabonner(endpoint: string): Observable<void> {
    return this.http.post<void>('/api/v1/notifications/desabonnement', { endpoint });
  }
}
