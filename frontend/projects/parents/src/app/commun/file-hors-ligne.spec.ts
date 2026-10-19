import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { ActionMiseEnAttente, rejouable } from 'api';

import { ActionGardee, FileHorsLigne, TableDActions, intercepteurHorsLigne } from './file-hors-ligne';
import { EtatsTransverses } from '../structure/etats-transverses';

/** Table en mémoire, à la place d'IndexedDB. */
class TableFactice implements TableDActions {
  lignes: ActionGardee[] = [];
  private suivant = 1;
  async toArray() {
    return [...this.lignes];
  }
  async add(action: ActionGardee) {
    this.lignes.push({ ...action, id: this.suivant++ });
  }
  async delete(id: number) {
    this.lignes = this.lignes.filter((ligne) => ligne.id !== id);
  }
  async clear() {
    this.lignes = [];
  }
}

describe('file des actions hors ligne (FG-DOC-06 §5.4)', () => {
  let http: HttpClient;
  let serveur: HttpTestingController;
  let file: FileHorsLigne;
  let table: TableFactice;
  const attendre = () => new Promise((resoudre) => setTimeout(resoudre));

  beforeEach(async () => {
    table = new TableFactice();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([intercepteurHorsLigne])), provideHttpClientTesting()],
    });
    file = TestBed.inject(FileHorsLigne);
    (file as unknown as { table: Promise<TableDActions> }).table = Promise.resolve(table);
    http = TestBed.inject(HttpClient);
    serveur = TestBed.inject(HttpTestingController);
    await attendre();
  });

  afterEach(() => serveur.verify());

  /** Lance une écriture rejouable que le réseau ne laisse pas partir. */
  async function horsLigne(): Promise<unknown> {
    const reponse = firstValueFrom(http.patch('/api/v1/enfants/e-1', { ecole: 'Les Manguiers' }, rejouable('FICHE_ENFANT'))).catch((erreur: unknown) => erreur);
    const requete = serveur.expectOne('/api/v1/enfants/e-1');
    requete.error(new ProgressEvent('error'), { status: 0 });
    const erreur = await reponse;
    await attendre();
    return { erreur, cle: requete.request.headers.get('Idempotency-Key') };
  }

  it('garde l’écriture sans réseau, avec sa clé d’idempotence, et le dit au composant', async () => {
    const { erreur, cle } = (await horsLigne()) as { erreur: unknown; cle: string };

    expect(erreur).toBeInstanceOf(ActionMiseEnAttente);
    expect(cle).toMatch(/^[A-Za-z0-9_-]{16,80}$/);
    expect(file.enAttente()).toEqual([expect.objectContaining({ genre: 'FICHE_ENFANT', methode: 'PATCH', url: '/api/v1/enfants/e-1', corps: { ecole: 'Les Manguiers' }, cle })]);
  });

  it('renvoie l’action au retour du réseau avec la même clé, puis la retire', async () => {
    const { cle } = (await horsLigne()) as { cle: string };

    const envoi = file.envoyer();
    await attendre();
    const renvoi = serveur.expectOne('/api/v1/enfants/e-1');
    expect(renvoi.request.headers.get('Idempotency-Key')).toBe(cle);
    expect(renvoi.request.body).toEqual({ ecole: 'Les Manguiers' });
    renvoi.flush({});
    await envoi;
    expect(file.enAttente()).toEqual([]);
  });

  it('garde l’action si le serveur est en panne, la retire et la compte s’il la refuse', async () => {
    await horsLigne();
    let envoi = file.envoyer();
    await attendre();
    serveur.expectOne('/api/v1/enfants/e-1').flush(null, { status: 503, statusText: 'Indisponible' });
    await envoi;
    expect(file.enAttente()).toHaveLength(1);

    envoi = file.envoyer();
    await attendre();
    serveur.expectOne('/api/v1/enfants/e-1').flush({ code: 'REQUETE_INVALIDE' }, { status: 400, statusText: 'Refusée' });
    await envoi;
    expect(file.enAttente()).toEqual([]);
    expect(file.refusees()).toBe(1);
  });

  it('laisse passer telle quelle une écriture qui n’est pas rejouable', async () => {
    const reponse = firstValueFrom(http.post('/api/v1/enfants/e-1/bracelet/localisation', {})).catch((erreur: unknown) => erreur);
    const requete = serveur.expectOne('/api/v1/enfants/e-1/bracelet/localisation');
    expect(requete.request.headers.has('Idempotency-Key')).toBe(false);
    requete.error(new ProgressEvent('error'), { status: 0 });
    expect(await reponse).not.toBeInstanceOf(ActionMiseEnAttente);
    await attendre();
    expect(file.enAttente()).toEqual([]);
  });

  it('annonce en tête d’écran ce qui partira au retour du réseau (écran 49)', async () => {
    await horsLigne();
    const fixture = TestBed.createComponent(EtatsTransverses);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain("1 action sera envoyée dès le retour du réseau : fiche de l'enfant.");
  });
});
