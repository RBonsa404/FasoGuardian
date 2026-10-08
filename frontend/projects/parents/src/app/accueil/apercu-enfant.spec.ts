import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { ClientBracelet, ClientZones, FicheEnfant, SafeZone, Situation } from 'api';

import { Carte, PositionSurCarte } from '../carte/fond';
import { ApercuEnfant } from './apercu-enfant';

/** Remplace la carte Leaflet : les essais portent sur ce que l'aperçu lui transmet et affiche autour. */
@Component({ selector: 'app-carte', template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class CarteFactice {
  readonly libelle = input<string>();
  readonly position = input<PositionSurCarte | null>(null);
  readonly zones = input<readonly SafeZone[]>([]);
}

const AWA: FicheEnfant = {
  id: 'e1',
  prenom: 'Awa',
  nom: 'Ouédraogo',
  dateNaissance: '2018-03-14',
  profil: { ecole: null, quartier: null, tailleCm: null, signesDistinctifs: null },
  modifieLe: '2026-10-01T08:00:00Z',
};

const ECOLE: SafeZone = {
  id: 'z1',
  forme: 'CERCLE',
  nom: 'École Les Manguiers',
  categorie: 'ECOLE',
  centre: { latitude: 12.3714, longitude: -1.5197 },
  rayonM: 220,
  sommets: null,
  jours: [1, 2, 3, 4, 5],
  debut: '07:00',
  fin: '17:30',
  toleranceS: 300,
  statut: 'ACTIVE',
  dansLaPlage: true,
  sortieEnCours: false,
  enfantDedans: true,
};

function situation(source: 'GNSS' | 'CELLULE', precisionM: number, minutes: number, batterie = 78): Situation {
  const mesureeLe = new Date(Date.now() - minutes * 60_000).toISOString();
  return {
    numeroSerie: 'FG-2291',
    position: { latitude: 12.3714, longitude: -1.5197, precisionM, source, mesureeLe },
    etat: { batterie, signalDbm: -79, reseau: '4G', operateur: 'Orange BF', enMouvement: true, enLigne: true, versionLogiciel: '2.4.1', dernierContact: mesureeLe },
  };
}

describe('aperçu de l’enfant sur le tableau de bord', () => {
  async function monter(reponse: Observable<Situation>, zones: SafeZone[] = [ECOLE]) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ClientBracelet, useValue: { situation: () => reponse } },
        { provide: ClientZones, useValue: { zones: () => of({ zones, maximum: 3 }) } },
      ],
    });
    TestBed.overrideComponent(ApercuEnfant, { remove: { imports: [Carte] }, add: { imports: [CarteFactice] } });
    const fixture = TestBed.createComponent(ApercuEnfant);
    fixture.componentRef.setInput('enfant', AWA);
    await fixture.whenStable();
    return { texte: (fixture.nativeElement as HTMLElement).textContent ?? '', element: fixture.nativeElement as HTMLElement };
  }

  it('une position GPS récente est dite précise, située dans sa zone, avec son heure, la batterie et le réseau', async () => {
    const { texte, element } = await monter(of(situation('GNSS', 12, 2)));

    expect(texte).toContain('Dans « École Les Manguiers »');
    expect(texte).toContain('Précise ± 12 m');
    expect(texte).toContain('il y a 2 min');
    expect(texte).toContain('78 %');
    expect(texte).toContain('4G');
    expect(texte).toContain('Orange BF · fort');
    expect(texte).not.toContain("n'a pas transmis de position plus récente");
    expect(element.querySelector('h2')?.textContent).toBe('Dans « École Les Manguiers »');
  });

  it('une position par antenne relais est dite approximative, avec son rayon d’incertitude', async () => {
    const { texte } = await monter(of(situation('CELLULE', 850, 9)), []);

    expect(texte).toContain('Approx. ± 850 m');
    expect(texte).toContain('Position de Awa');
    expect(texte).not.toContain('Précise');
  });

  it('une position de plus de quinze minutes est signalée comme ancienne et une batterie basse comme faible', async () => {
    const { texte } = await monter(of(situation('GNSS', 10, 40, 12)));

    expect(texte).toContain("Le bracelet n'a pas transmis de position plus récente.");
    expect(texte).toContain('il y a 40 min');
    expect(texte).toContain('batterie faible');
  });

  it('une sortie de zone en cours prime dans le titre', async () => {
    const { texte } = await monter(of(situation('GNSS', 10, 1)), [{ ...ECOLE, sortieEnCours: true, enfantDedans: false }]);

    expect(texte).toContain('Hors de « École Les Manguiers »');
  });

  it('sans bracelet, l’aperçu invite à l’appairage', async () => {
    const introuvable = new HttpErrorResponse({ status: 404, error: { status: 404, code: 'RESSOURCE_INTROUVABLE', detail: "Aucun bracelet n'est associé à cet enfant." } });
    const { texte } = await monter(throwError(() => introuvable));

    expect(texte).toContain('Associez le bracelet de Awa');
    expect(texte).toContain("Le code d'appairage est sur la carte glissée dans la boîte.");
  });
});
