import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { LectureGardee, LecturesLocales, TableDeLectures, intercepteurLecturesLocales } from './lectures-locales';
import { EtatsTransverses } from '../structure/etats-transverses';

class TableFactice implements TableDeLectures {
  lignes = new Map<string, LectureGardee>();
  async get(url: string) {
    return this.lignes.get(url);
  }
  async put(lecture: LectureGardee) {
    this.lignes.set(lecture.url, lecture);
  }
  async clear() {
    this.lignes.clear();
  }
}

const ENFANT = '0b9d6c1e-3f4a-4b2c-9d8e-7f6a5b4c3d2e';

describe('lectures consultables hors ligne (FG-DOC-06, tableau 8)', () => {
  let http: HttpClient;
  let serveur: HttpTestingController;
  let lectures: LecturesLocales;
  let table: TableFactice;
  const attendre = () => new Promise((resoudre) => setTimeout(resoudre));

  beforeEach(() => {
    table = new TableFactice();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([intercepteurLecturesLocales])), provideHttpClientTesting()],
    });
    lectures = TestBed.inject(LecturesLocales);
    (lectures as unknown as { table: Promise<TableDeLectures> }).table = Promise.resolve(table);
    http = TestBed.inject(HttpClient);
    serveur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => serveur.verify());

  async function lire(url: string, reponse: unknown | 'hors-ligne'): Promise<unknown> {
    const resultat = firstValueFrom(http.get(url)).catch((erreur: unknown) => ({ erreur }));
    const requete = serveur.expectOne(url);
    if (reponse === 'hors-ligne') {
      requete.error(new ProgressEvent('error'), { status: 0 });
    } else {
      requete.flush(reponse as object);
    }
    const valeur = await resultat;
    await attendre();
    return valeur;
  }

  it('sert la dernière réponse gardée quand le réseau manque, et dit de quand elle date', async () => {
    const position = { numeroSerie: 'FG-123456', position: { latitude: 12.36, longitude: -1.53, precisionM: 18, source: 'GNSS', mesureeLe: '2026-10-19T08:40:00Z' }, etat: null };
    await lire(`/api/v1/enfants/${ENFANT}/position`, position);
    expect(lectures.enLigne()).toBe(true);

    expect(await lire(`/api/v1/enfants/${ENFANT}/position`, 'hors-ligne')).toEqual(position);
    expect(lectures.enLigne()).toBe(false);
    expect(lectures.copieDu()).toBe(table.lignes.get(`/api/v1/enfants/${ENFANT}/position`)!.recueLe);

    // Le bandeau dit que la fiche et la dernière position restent consultables.
    const fixture = TestBed.createComponent(EtatsTransverses);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Hors ligne · données gardées à');
  });

  it('ne garde de la fiche médicale que les éléments critiques', async () => {
    await lire(`/api/v1/enfants/${ENFANT}/sante`, {
      groupeSanguin: 'O+',
      groupeSanguinSurQr: false,
      elements: [
        { type: 'ALLERGIE', libelle: 'Arachides', critique: true },
        { type: 'TRAITEMENT', libelle: 'Vitamine D', critique: false },
      ],
      modifieLe: null,
    });
    const gardee = table.lignes.get(`/api/v1/enfants/${ENFANT}/sante`)!.corps as { elements: { libelle: string }[] };
    expect(gardee.elements.map((element) => element.libelle)).toEqual(['Arachides']);
  });

  it('ne garde ni les trajets ni les alertes, et laisse l’erreur passer sans copie', async () => {
    await lire(`/api/v1/enfants/${ENFANT}/trajets`, []);
    expect(table.lignes.size).toBe(0);
    expect(await lire(`/api/v1/enfants/${ENFANT}`, 'hors-ligne')).toEqual({ erreur: expect.objectContaining({ status: 0 }) });
  });
});
