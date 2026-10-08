import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { DossierKycParent } from 'api';

import { EtatVerification } from './etat-verification';
import { Verification } from './verification';

@Component({ template: 'accueil' })
class AccueilFactice {}

const DOSSIER: DossierKycParent = {
  id: 'd-1',
  reference: 'KYC-20418',
  statut: 'BROUILLON',
  canal: 'EN_LIGNE',
  natureLien: 'PARENT',
  motif: null,
  deposeLe: null,
  decideLe: null,
  pieces: [],
};

describe('Verification', () => {
  let harnais: RouterTestingHarness;
  let serveur: HttpTestingController;
  let page: HTMLElement;
  let router: Router;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: '', component: AccueilFactice },
            { path: 'verification/:etape', component: Verification },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    harnais = await RouterTestingHarness.create('/verification/mode');
    serveur = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    page = harnais.routeNativeElement!;
  });

  afterEach(() => serveur.verify());

  const stable = () => harnais.fixture.whenStable();
  const action = () => page.querySelector('[pied] button, button[fg-button]') as HTMLButtonElement;
  const boutonPrincipal = () =>
    Array.from(page.querySelectorAll('button[fg-button]')).pop() as HTMLButtonElement;
  const saisir = (index: number, texte: string) => {
    const champ = page.querySelectorAll('input:not([type=file])')[index] as HTMLInputElement;
    champ.value = texte;
    champ.dispatchEvent(new Event('input'));
  };
  const joindre = async (type: string, fichier: File) => {
    const champ = page.querySelector(`input[data-piece=${type}]`) as HTMLInputElement;
    Object.defineProperty(champ, 'files', { value: [fichier], configurable: true });
    champ.dispatchEvent(new Event('change'));
    await new Promise((resoudre) => setTimeout(resoudre));
    await stable();
  };
  const suivant = async () => {
    boutonPrincipal().click();
    await stable();
  };
  const remplirIdentite = async () => {
    saisir(0, 'Ouédraogo');
    saisir(1, 'Mariam');
    saisir(2, 'B12345678');
    (page.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await stable();
  };
  const remplirEnfant = () => {
    saisir(0, 'Awa');
    saisir(1, 'Ouédraogo');
    saisir(2, '2018-03-14');
  };

  it('en ligne : crée le dossier, envoie les pièces une à une, le dépose puis affiche son instruction', async () => {
    expect(action()).toBeTruthy();
    await suivant();
    expect(router.url).toBe('/verification/identite');
    await remplirIdentite();
    expect(router.url).toBe('/verification/piece');

    await suivant();
    expect(page.textContent).toContain('Photographiez au moins le recto');
    await joindre('PIECE_RECTO', new File(['recto'], 'recto.jpg', { type: 'image/jpeg' }));
    expect(page.textContent).toContain('Photo jointe');
    await suivant();
    expect(router.url).toBe('/verification/filiation');

    remplirEnfant();
    await suivant();
    expect(page.textContent).toContain('Ajoutez le justificatif');
    await joindre('ACTE_NAISSANCE', new File(['%PDF-acte'], 'acte.pdf', { type: 'application/pdf' }));
    await suivant();
    expect(router.url).toBe('/verification/recap');
    expect(page.textContent).toContain('Ouédraogo Mariam');
    expect(page.textContent).toContain('Awa Ouédraogo');

    await suivant();
    const ouverture = serveur.expectOne('/api/v1/kyc/dossiers');
    expect(ouverture.request.body).toEqual({
      canal: 'EN_LIGNE',
      natureLien: 'PARENT',
      demandeur: { nom: 'Ouédraogo', prenoms: 'Mariam', typePiece: 'CNIB', numeroPiece: 'B12345678' },
      enfant: { prenom: 'Awa', nom: 'Ouédraogo', dateNaissance: '2018-03-14' },
    });
    ouverture.flush(DOSSIER);
    const recto = serveur.expectOne('/api/v1/kyc/dossiers/d-1/pieces');
    expect((recto.request.body as FormData).get('type')).toBe('PIECE_RECTO');
    recto.flush({ id: 'p-1', type: 'PIECE_RECTO', typeMime: 'image/jpeg', tailleOctets: 5 });
    const acte = serveur.expectOne('/api/v1/kyc/dossiers/d-1/pieces');
    expect((acte.request.body as FormData).get('type')).toBe('ACTE_NAISSANCE');
    acte.flush({ id: 'p-2', type: 'ACTE_NAISSANCE', typeMime: 'application/pdf', tailleOctets: 9 });
    serveur.expectOne('/api/v1/kyc/dossiers/d-1/depot').flush({ ...DOSSIER, statut: 'DEPOSE' });
    await stable();

    expect(router.url).toBe('/verification/instruction');
    expect(TestBed.inject(EtatVerification).identite()).toBeNull();
    serveur
      .expectOne('/api/v1/kyc/dossiers/courant')
      .flush({ ...DOSSIER, statut: 'DEPOSE', deposeLe: '2026-10-07T09:52:00Z' });
    await stable();
    expect(page.textContent).toContain('Dossier en instruction');
    expect(page.textContent).toContain('KYC-20418');
  });

  it("en point d'inscription : saute les photos et dépose le dossier sans pièce", async () => {
    (page.querySelectorAll('[role=radio]')[1] as HTMLButtonElement).click();
    await stable();
    await suivant();
    await remplirIdentite();
    expect(router.url).toBe('/verification/filiation');
    expect(page.querySelector('input[type=file]')).toBeNull();

    remplirEnfant();
    await suivant();
    expect(page.textContent).toContain("Présentez-vous en point d'inscription");
    await suivant();
    const ouverture = serveur.expectOne('/api/v1/kyc/dossiers');
    expect(ouverture.request.body.canal).toBe('POINT_INSCRIPTION');
    ouverture.flush({ ...DOSSIER, canal: 'POINT_INSCRIPTION' });
    serveur.expectOne('/api/v1/kyc/dossiers/d-1/depot').flush({ ...DOSSIER, statut: 'DEPOSE' });
    await stable();
    serveur.expectOne('/api/v1/kyc/dossiers/courant').flush({ ...DOSSIER, statut: 'DEPOSE' });
  });

  it('un complément demandé affiche le motif et rouvre les étapes photo sur le dossier existant', async () => {
    await harnais.navigateByUrl('/verification/instruction');
    serveur.expectOne('/api/v1/kyc/dossiers/courant').flush({
      ...DOSSIER,
      statut: 'COMPLEMENT_DEMANDE',
      motif: 'Le verso de la pièce est illisible.',
    });
    await stable();
    expect(page.textContent).toContain('Un complément est nécessaire');
    expect(page.textContent).toContain('Le verso de la pièce est illisible.');

    await suivant();
    expect(router.url).toBe('/verification/piece');
    await joindre('PIECE_VERSO', new File(['verso'], 'verso.jpg', { type: 'image/jpeg' }));
    await suivant();
    await suivant();
    expect(router.url).toBe('/verification/recap');
    await suivant();
    serveur.expectOne('/api/v1/kyc/dossiers/d-1/pieces').flush({ id: 'p-3', type: 'PIECE_VERSO', typeMime: 'image/jpeg', tailleOctets: 5 });
    serveur.expectOne('/api/v1/kyc/dossiers/d-1/depot').flush({ ...DOSSIER, statut: 'DEPOSE' });
    await stable();
    serveur.expectOne('/api/v1/kyc/dossiers/courant').flush({ ...DOSSIER, statut: 'DEPOSE' });
  });

  it('sans dossier, la page de suivi renvoie au début du parcours', async () => {
    await harnais.navigateByUrl('/verification/instruction');
    serveur.expectOne('/api/v1/kyc/dossiers/courant').flush(null, { status: 204, statusText: 'No Content' });
    await stable();

    expect(router.url).toBe('/verification/mode');
  });
});
