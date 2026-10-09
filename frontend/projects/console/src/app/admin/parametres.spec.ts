import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ClientAbonnements, ClientPasserelles, InstallationPasserelle, Offre, Passerelle } from 'api';

import { Parametres, nombre } from './parametres';

const OFFRES: Offre[] = [
  { code: 'ESSENTIEL', libelle: 'Essentiel', prixFcfa: 1500, intervalleS: 900, zonesMaximum: 1, historiqueJours: 1 },
  { code: 'PREMIUM', libelle: 'Premium', prixFcfa: 5000, intervalleS: 300, zonesMaximum: 3, historiqueJours: 90 },
];

function passerelle(partiel: Partial<Passerelle> = {}): Passerelle {
  return {
    id: 'p-1',
    eui: 'A84041FFFF1F2E3D',
    etablissement: 'École Les Manguiers',
    latitude: 12.3642,
    longitude: -1.5331,
    rayonM: 150,
    creeeLe: '2026-10-01T08:00:00Z',
    vueLe: '2026-10-09T14:00:00Z',
    enLigne: true,
    ...partiel,
  };
}

/** Double du registre : retient ce que l'administrateur demande. */
class PasserellesFactices {
  liste: Passerelle[] = [passerelle(), passerelle({ id: 'p-2', eui: '0011223344556677', etablissement: 'École Wend-Kuuni', enLigne: false, vueLe: null })];
  installations: InstallationPasserelle[] = [];
  retraits: string[] = [];
  conflit = false;

  enService() {
    return of(this.liste);
  }

  installer(installation: InstallationPasserelle) {
    this.installations.push(installation);
    if (this.conflit) {
      return throwError(() => new HttpErrorResponse({ status: 409, error: { status: 409, code: 'CONFLIT', detail: 'Une passerelle porte déjà cet identifiant.' } }));
    }
    return of(passerelle({ id: 'p-3', ...installation, enLigne: false, vueLe: null }));
  }

  retirer(id: string) {
    this.retraits.push(id);
    return of(undefined);
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(racine: ParentNode, nom: string): HTMLButtonElement {
  return [...racine.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

function saisir(page: HTMLElement, libelle: string, valeur: string): void {
  const champ = [...page.querySelectorAll('fg-input')].find((c) => lire(c).includes(libelle))!.querySelector('input') as HTMLInputElement;
  champ.value = valeur;
  champ.dispatchEvent(new Event('input'));
}

async function ouvrir(client: PasserellesFactices) {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: ClientPasserelles, useValue: client }, { provide: ClientAbonnements, useValue: { offres: () => of(OFFRES) } }],
  });
  const fixture = TestBed.createComponent(Parametres);
  await fixture.whenStable();
  return fixture;
}

describe('paramétrage', () => {
  it('montre les tarifs, le gabarit du SMS et l’état de chaque passerelle', async () => {
    const fixture = await ouvrir(new PasserellesFactices());
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('Essentiel1 500 FCFA');
    expect(lire(page)).toContain('Premium5 000 FCFA');
    expect(lire(page)).toContain('Passerelles LoRaWAN2 installée(s) · 1 en ligne');
    expect(lire(page)).toContain('FasoGuardian : ALERTE SOS.');
    expect(lire(page)).toContain('aucune donnée de santé ni coordonnée GPS');
    const lignes = [...page.querySelectorAll('li')].map(lire);
    expect(lignes[0]).toContain('École Les Manguiers');
    expect(lignes[0]).toContain('A84041FFFF1F2E3D · rayon 150 m');
    expect(lignes[0]).toContain('En ligne');
    expect(lignes[1]).toContain('Hors ligne');
  });

  it('contrôle la saisie avant d’enregistrer une passerelle, puis l’ajoute à la liste', async () => {
    const client = new PasserellesFactices();
    const fixture = await ouvrir(client);
    const page = fixture.nativeElement as HTMLElement;
    bouton(page, 'Enregistrer une passerelle').click();
    await fixture.whenStable();
    const feuille = page.querySelector('fg-sheet') as HTMLElement;

    saisir(page, 'Établissement', 'École Sainte-Marie');
    saisir(page, 'Identifiant de la passerelle', 'a84041');
    bouton(feuille.querySelector('form')!, 'Enregistrer').click();
    await fixture.whenStable();
    expect(lire(page)).toContain("L'identifiant compte 16 chiffres hexadécimaux");

    saisir(page, 'Identifiant de la passerelle', 'a84041ffff000001');
    saisir(page, 'Latitude', '12,3801');
    saisir(page, 'Longitude', '-1.5102');
    saisir(page, "Rayon de l'enceinte", '5');
    bouton(feuille.querySelector('form')!, 'Enregistrer').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('entre 20 et 2 000');
    expect(client.installations).toEqual([]);

    saisir(page, "Rayon de l'enceinte", '120');
    bouton(feuille.querySelector('form')!, 'Enregistrer').click();
    await fixture.whenStable();

    expect(client.installations).toEqual([{ eui: 'A84041FFFF000001', etablissement: 'École Sainte-Marie', latitude: 12.3801, longitude: -1.5102, rayonM: 120 }]);
    expect([...page.querySelectorAll('li')].map(lire).some((l) => l.includes('École Sainte-Marie'))).toBe(true);
    expect(lire(page)).toContain('3 installée(s) · 1 en ligne');
  });

  it('affiche le refus du serveur pour un identifiant déjà pris, et retire une passerelle', async () => {
    const client = new PasserellesFactices();
    client.conflit = true;
    const fixture = await ouvrir(client);
    const page = fixture.nativeElement as HTMLElement;
    bouton(page, 'Enregistrer une passerelle').click();
    await fixture.whenStable();
    saisir(page, 'Établissement', 'École Sainte-Marie');
    saisir(page, 'Identifiant de la passerelle', 'A84041FFFF1F2E3D');
    saisir(page, 'Latitude', '12.38');
    saisir(page, 'Longitude', '-1.51');
    bouton(page.querySelector('fg-sheet form')!, 'Enregistrer').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Une passerelle porte déjà cet identifiant.');

    bouton(page.querySelectorAll('li')[1], 'Retirer').click();
    await fixture.whenStable();

    expect(client.retraits).toEqual(['p-2']);
    expect(page.querySelectorAll('li').length).toBe(1);
  });

  it('lit un nombre saisi avec une virgule ou un point', () => {
    expect(nombre(' 12,3801 ')).toBe(12.3801);
    expect(nombre('-1.51')).toBe(-1.51);
    expect(nombre('')).toBeNull();
    expect(nombre('douze')).toBeNull();
  });
});
