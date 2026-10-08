import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ClientAuthentification, ENTETE_ANTI_CSRF, Session } from './authentification';
import { intercepteurJeton } from './intercepteur-jeton';

describe('intercepteurJeton', () => {
  let http: HttpClient;
  let serveur: HttpTestingController;
  let session: Session;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([intercepteurJeton])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    serveur = TestBed.inject(HttpTestingController);
    session = TestBed.inject(Session);
  });

  afterEach(() => serveur.verify());

  it("ajoute le jeton d'accès aux API protégées, pas aux points publics ni d'authentification", () => {
    session.ouvrir({ jetonAcces: 'jeton-1', expireDansSecondes: 900 });

    http.get('/api/v1/moi').subscribe();
    http.get('/api/v1/public/q/abc').subscribe();
    http.post('/api/v1/auth/connexion', {}).subscribe();

    expect(serveur.expectOne('/api/v1/moi').request.headers.get('Authorization')).toBe('Bearer jeton-1');
    expect(serveur.expectOne('/api/v1/public/q/abc').request.headers.has('Authorization')).toBe(false);
    expect(serveur.expectOne('/api/v1/auth/connexion').request.headers.has('Authorization')).toBe(false);
  });

  it('sur un 401, rafraîchit la session puis rejoue la requête avec le nouveau jeton', () => {
    session.ouvrir({ jetonAcces: 'perime', expireDansSecondes: 0 });
    let recu: unknown;
    http.get('/api/v1/moi').subscribe((corps) => (recu = corps));

    serveur.expectOne('/api/v1/moi').flush(null, { status: 401, statusText: 'Unauthorized' });
    const echange = serveur.expectOne('/api/v1/auth/rafraichir');
    expect(echange.request.withCredentials).toBe(true);
    expect(echange.request.headers.get(ENTETE_ANTI_CSRF)).toBe('1');
    echange.flush({ jetonAcces: 'neuf', expireDansSecondes: 900 });

    const rejeu = serveur.expectOne('/api/v1/moi');
    expect(rejeu.request.headers.get('Authorization')).toBe('Bearer neuf');
    rejeu.flush({ id: '1' });
    expect(recu).toEqual({ id: '1' });
    expect(session.jetonAcces()).toBe('neuf');
  });

  it('deux requêtes en échec partagent un seul rafraîchissement', () => {
    session.ouvrir({ jetonAcces: 'perime', expireDansSecondes: 0 });
    http.get('/api/v1/moi').subscribe();
    http.get('/api/v1/enfants').subscribe();

    serveur.expectOne('/api/v1/moi').flush(null, { status: 401, statusText: 'Unauthorized' });
    serveur.expectOne('/api/v1/enfants').flush(null, { status: 401, statusText: 'Unauthorized' });
    serveur.expectOne('/api/v1/auth/rafraichir').flush({ jetonAcces: 'neuf', expireDansSecondes: 900 });

    serveur.expectOne('/api/v1/moi').flush({});
    serveur.expectOne('/api/v1/enfants').flush([]);
  });

  it("si le rafraîchissement échoue, demande une réauthentification et propage l'erreur d'origine", () => {
    session.ouvrir({ jetonAcces: 'perime', expireDansSecondes: 0 });
    let statut = 0;
    http.get('/api/v1/moi').subscribe({ error: (erreur) => (statut = erreur.status) });

    serveur.expectOne('/api/v1/moi').flush(null, { status: 401, statusText: 'Unauthorized' });
    serveur.expectOne('/api/v1/auth/rafraichir').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(statut).toBe(401);
    expect(session.ouverte()).toBe(false);
    expect(session.reauthentificationRequise()).toBe(true);
  });

  it('la connexion ouvre la session et la déconnexion la ferme', () => {
    const client = TestBed.inject(ClientAuthentification);

    client.connecter('70123456', 'secret').subscribe();
    serveur.expectOne('/api/v1/auth/connexion').flush({ jetonAcces: 'jeton-1', expireDansSecondes: 900 });
    expect(session.ouverte()).toBe(true);

    client.deconnecter().subscribe();
    serveur.expectOne('/api/v1/auth/deconnexion').flush(null);
    expect(session.ouverte()).toBe(false);
  });
});
