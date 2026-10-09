import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { CampagneOta, ClientOta, ImageOta, VagueOta } from 'api';

import { CampagnesOta, situation, taille } from './ota';

const SHA = '9f2c5a1e7b3d4c6f8a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f';

function vague(numero: number, pourcentage: number, cibles: number, installes: number, etat: VagueOta['etat']): VagueOta {
  return { numero, pourcentage, cibles, installes, etat };
}

function campagne(partiel: Partial<CampagneOta> = {}): CampagneOta {
  return {
    id: 'c-1',
    version: '2.4.2',
    tailleOctets: 421_888,
    note: "Correctif d'autonomie en 2G",
    statut: 'EN_COURS',
    vagueCourante: 2,
    creeeLe: '2026-10-07T13:15:00Z',
    vagues: [vague(1, 1, 89, 89, 'TERMINEE'), vague(2, 10, 891, 884, 'EN_COURS'), vague(3, 25, 2228, 0, 'PRETE'), vague(4, 100, 5705, 0, 'PLANIFIEE')],
    ...partiel,
  };
}

/** Double du client : retient ce que l'agent demande. */
class OtaFactice {
  liste: CampagneOta[] = [campagne()];
  appels: string[] = [];
  images: ImageOta[] = [];
  refuser = false;

  campagnes() {
    return of(this.liste);
  }

  preparer(image: ImageOta) {
    this.images.push(image);
    if (this.refuser) {
      return throwError(
        () => new HttpErrorResponse({ status: 400, error: { status: 400, code: 'REQUETE_INVALIDE', detail: "La signature de l'image ne correspond pas à la clé de publication : l'image est refusée." } }),
      );
    }
    return of(campagne({ id: 'c-2', version: image.version, statut: 'PREPAREE', vagueCourante: 0, vagues: [vague(1, 1, 1, 0, 'PRETE'), vague(2, 10, 0, 0, 'PLANIFIEE'), vague(3, 25, 0, 0, 'PLANIFIEE'), vague(4, 100, 2, 0, 'PLANIFIEE')] }));
  }

  lancerLaVagueSuivante(id: string) {
    this.appels.push(`vague:${id}`);
    return of(campagne({ vagueCourante: 3, vagues: [vague(1, 1, 89, 89, 'TERMINEE'), vague(2, 10, 891, 884, 'EN_COURS'), vague(3, 25, 2228, 0, 'EN_COURS'), vague(4, 100, 5705, 0, 'PRETE')] }));
  }

  mettreEnPause(id: string) {
    this.appels.push(`pause:${id}`);
    return of(campagne({ statut: 'EN_PAUSE' }));
  }

  reprendre(id: string) {
    this.appels.push(`reprise:${id}`);
    return of(campagne());
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

async function ouvrir(client: OtaFactice) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientOta, useValue: client }] });
  const fixture = TestBed.createComponent(CampagnesOta);
  await fixture.whenStable();
  return fixture;
}

describe('campagnes OTA', () => {
  it('montre chaque vague avec son avancement et ce qui reste à faire', async () => {
    const fixture = await ouvrir(new OtaFactice());
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('Campagne OTA 2.4.2');
    expect(lire(page)).toContain("Image signée (ECDSA P-256) · 412 Ko · Correctif d'autonomie en 2G.");
    expect(lire(page)).toContain("Les parents sont informés avant l'installation.");
    const vagues = [...page.querySelectorAll('ol li')].map(lire);
    expect(vagues[0]).toContain('Vague 1 · 1 %');
    expect(vagues[0]).toContain('89 / 89');
    expect(vagues[1]).toContain("7 en attente d'installation");
    expect(vagues[2]).toContain('Prête · déclenchement manuel');
    expect(vagues[3]).toContain('Planifiée');
    expect(page.querySelectorAll('[role="progressbar"]')[1].getAttribute('aria-label')).toBe('Vague 2 : 884 bracelets à jour sur 891');
  });

  it('lance la vague prête, met en pause puis reprend', async () => {
    const client = new OtaFactice();
    const fixture = await ouvrir(client);
    const page = fixture.nativeElement as HTMLElement;

    bouton(page, 'Lancer la vague 3 (25 %)').click();
    await fixture.whenStable();
    expect(bouton(page, 'Lancer la vague 4 (100 %)')).toBeDefined();

    bouton(page, 'Mettre en pause').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('En pause');
    // En pause, aucune vague ne se lance.
    expect(bouton(page, 'Lancer la vague').disabled).toBe(true);

    bouton(page, 'Reprendre').click();
    await fixture.whenStable();

    expect(client.appels).toEqual(['vague:c-1', 'pause:c-1', 'reprise:c-1']);
    expect(bouton(page, 'Mettre en pause')).toBeDefined();
  });

  it('contrôle le manifeste saisi, affiche le refus d’une image non signée, puis ajoute la campagne acceptée', async () => {
    const client = new OtaFactice();
    client.refuser = true;
    const fixture = await ouvrir(client);
    const page = fixture.nativeElement as HTMLElement;
    bouton(page, 'Nouvelle image').click();
    await fixture.whenStable();
    const envoyer = () => bouton(page.querySelector('fg-sheet form')!, 'Vérifier et enregistrer').click();

    saisir(page, 'Version', '2.5');
    envoyer();
    await fixture.whenStable();
    expect(lire(page)).toContain("La version s'écrit en trois nombres");

    saisir(page, 'Version', '2.5.0');
    saisir(page, 'Taille (octets)', '430080');
    saisir(page, "Adresse de l'image", 'http://exemple.test/fg.bin');
    envoyer();
    await fixture.whenStable();
    expect(lire(page)).toContain("L'image doit être servie en https.");
    expect(client.images).toEqual([]);

    saisir(page, "Adresse de l'image", 'https://mises-a-jour.fasoguardian.test/fg-2.5.0.bin');
    saisir(page, 'Empreinte SHA-256', SHA);
    saisir(page, 'Signature du manifeste', 'c2lnbmF0dXJl');
    envoyer();
    await fixture.whenStable();
    expect(lire(page)).toContain("l'image est refusée");
    expect(page.querySelectorAll('section').length).toBe(1);

    client.refuser = false;
    envoyer();
    await fixture.whenStable();

    expect(client.images[1]).toEqual({ version: '2.5.0', urlImage: 'https://mises-a-jour.fasoguardian.test/fg-2.5.0.bin', tailleOctets: 430080, sha256: SHA, signature: 'c2lnbmF0dXJl', note: null });
    expect(lire(page.querySelectorAll('section')[0])).toContain('Campagne OTA 2.5.0');
    expect(lire(page.querySelectorAll('section')[0])).toContain('Préparée');
  });

  it('écrit la taille en kilo-octets et la situation d’une vague sans cible', () => {
    expect(taille(421_888).replace(/\s/g, ' ')).toBe('412 Ko');
    expect(situation(vague(2, 10, 0, 0, 'TERMINEE'))).toBe('Terminée · aucun bracelet à viser');
  });
});
