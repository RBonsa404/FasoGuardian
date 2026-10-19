import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { EtatReseau, intercepteurEtatReseau } from './etat-reseau';
import { EtatsTransverses } from '../structure/etats-transverses';

const PHOTO = '/api/v1/enfants/0b9d6c1e-3f4a-4b2c-9d8e-7f6a5b4c3d2e/photo';

describe('qualité de la liaison (écran 49)', () => {
  let http: HttpClient;
  let serveur: HttpTestingController;
  let etat: EtatReseau;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([intercepteurEtatReseau])), provideHttpClientTesting()],
    });
    etat = TestBed.inject(EtatReseau);
    http = TestBed.inject(HttpClient);
    serveur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => serveur.verify());

  async function texteDuBandeau(): Promise<string> {
    const fixture = TestBed.createComponent(EtatsTransverses);
    await fixture.whenStable();
    return fixture.nativeElement.textContent;
  }

  it('sur une connexion lente, ne charge pas les photos et le dit', async () => {
    etat.lent.set(true);
    const photo = firstValueFrom(http.get(PHOTO, { responseType: 'blob' })).catch((erreur: unknown) => erreur);
    serveur.expectNone(PHOTO);
    expect(await photo).toEqual(expect.objectContaining({ status: 0 }));
    expect(await texteDuBandeau()).toContain('Connexion lente : les photos ne sont pas chargées');
  });

  it('annonce un service indisponible, avec les numéros d’urgence, jusqu’à la réponse suivante', async () => {
    const panne = firstValueFrom(http.get('/api/v1/enfants')).catch(() => undefined);
    serveur.expectOne('/api/v1/enfants').flush(null, { status: 503, statusText: 'Indisponible' });
    await panne;
    expect(etat.serveurIndisponible()).toBe(true);
    const texte = await texteDuBandeau();
    expect(texte).toContain('Service momentanément indisponible');
    expect(texte).toContain('Appelez le 17 (Police) ou le 18 (Pompiers).');
    expect(texte).not.toContain('SMS');

    const retour = firstValueFrom(http.get('/api/v1/enfants'));
    serveur.expectOne('/api/v1/enfants').flush([]);
    await retour;
    expect(etat.serveurIndisponible()).toBe(false);
  });
});
