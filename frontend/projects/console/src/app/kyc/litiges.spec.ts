import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ClientLitiges, DecisionLitige, FondementLitige, Litige } from 'api';

import { EcranLitige, chronologie, mesures } from './litige';
import { Litiges } from './litiges';

function litige(partiel: Partial<Litige> = {}): Litige {
  return {
    id: 'l-1',
    reference: 'LIT-000031',
    dossierKyc: 'KYC-000412',
    tuteur: 'SANOU Adama',
    enfant: 'Ibrahim',
    motif: 'Jugement de garde exclusive produit par la mère.',
    statut: 'OUVERT',
    geolocalisationSuspendue: true,
    ouvertLe: '2026-10-05T16:20:00Z',
    echeanceLe: '2099-10-12T16:20:00Z',
    decision: null,
    fondement: null,
    referenceDuFondement: null,
    decideLe: null,
    ...partiel,
  };
}

/** Double du client : retient ce que l'agent demande. */
class ClientFactice {
  courant = litige();
  ouvertures: { dossier: string; motif: string; suspendre: boolean }[] = [];
  decisions: { decision: DecisionLitige; fondement: FondementLitige; reference: string }[] = [];
  suspensions: boolean[] = [];

  litiges() {
    return of([this.courant]);
  }

  litige() {
    return of(this.courant);
  }

  ouvrir(dossier: string, motif: string, suspendre: boolean) {
    this.ouvertures.push({ dossier, motif, suspendre });
    return of(this.courant);
  }

  suspendreLaGeolocalisation(_id: string, suspendue: boolean) {
    this.suspensions.push(suspendue);
    this.courant = { ...this.courant, geolocalisationSuspendue: suspendue };
    return of(this.courant);
  }

  decider(_id: string, decision: DecisionLitige, fondement: FondementLitige, reference: string) {
    this.decisions.push({ decision, fondement, reference });
    this.courant = { ...this.courant, statut: 'CLOS', decision, fondement, referenceDuFondement: reference, decideLe: '2026-10-09T10:00:00Z', geolocalisationSuspendue: false };
    return of(this.courant);
  }
}

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
}

function choisir(page: HTMLElement, libelle: string): void {
  const option = [...page.querySelectorAll('fg-sheet label')].find((l) => lire(l).includes(libelle))!;
  (option.querySelector('input') as HTMLInputElement).click();
}

function preparer(client: ClientFactice) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ClientLitiges, useValue: client }] });
}

describe('litiges de filiation', () => {
  it('liste les litiges et refuse d’en ouvrir un sans dossier ni signalement', async () => {
    const client = new ClientFactice();
    preparer(client);
    const fixture = TestBed.createComponent(Litiges);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('SANOU Adama → Ibrahim');
    expect(lire(page)).toContain('Ouvert · compte gelé');

    bouton(page, 'Ouvrir un litige').click();
    await fixture.whenStable();
    bouton(page, 'Ouvrir le litige et geler le compte').click();
    await fixture.whenStable();

    expect(lire(page)).toContain('Indiquez le dossier KYC et le signalement reçu.');
    expect(client.ouvertures).toEqual([]);
  });

  it('montre les mesures conservatoires et lève la suspension de la géolocalisation', async () => {
    const client = new ClientFactice();
    preparer(client);
    const fixture = TestBed.createComponent(EcranLitige);
    fixture.componentRef.setInput('id', 'l-1');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(lire(page)).toContain('Litige ouvert · LIT-000031 · compte gelé');
    expect(lire(page)).toContain('Contestation de la filiation : SANOU Adama → Ibrahim');
    expect(lire(page)).toContain('Suspension conservatoire');
    expect(lire(page)).toContain('SOS et page QR maintenus');
    expect(lire(page)).toContain('décision sous 7 jours');

    bouton(page, 'Rétablir la géolocalisation').click();
    await fixture.whenStable();

    expect(client.suspensions).toEqual([false]);
    expect(lire(page)).toContain('Maintenue');
  });

  it('n’enregistre une décision qu’avec son fondement, puis montre le litige clos', async () => {
    const client = new ClientFactice();
    preparer(client);
    const fixture = TestBed.createComponent(EcranLitige);
    fixture.componentRef.setInput('id', 'l-1');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    bouton(page, 'Enregistrer la décision').click();
    await fixture.whenStable();
    choisir(page, 'Lien retiré');
    await fixture.whenStable();
    bouton(page, 'Appliquer et notifier les parties').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Choisissez la décision et ce sur quoi elle se fonde.');
    expect(client.decisions).toEqual([]);

    choisir(page, 'Décision de justice');
    await fixture.whenStable();
    bouton(page, 'Appliquer et notifier les parties').click();
    await fixture.whenStable();

    expect(client.decisions).toEqual([{ decision: 'LIEN_RETIRE', fondement: 'DECISION_DE_JUSTICE', reference: '' }]);
    expect(lire(page)).toContain('Litige clos · LIT-000031');
    expect(bouton(page, 'Enregistrer la décision')).toBeUndefined();
  });

  it('résume un litige clos : gel levé, décision et son fondement', () => {
    const clos = litige({ statut: 'CLOS', decision: 'LIEN_MAINTENU', fondement: 'ACCORD_ECRIT', referenceDuFondement: 'Accord du 8 oct.', decideLe: '2026-10-09T10:00:00Z', geolocalisationSuspendue: false });

    const resume = mesures(clos);

    expect(resume[0].valeur).toBe('Gel levé');
    expect(resume[2].valeur).toBe('Lien maintenu');
    expect(resume[2].detail).toBe('Accord écrit des parties · Accord du 8 oct.');
    expect(chronologie(clos)).toHaveLength(2);
    expect(chronologie(clos)[1].quoi).toContain('lien maintenu');
  });
});
