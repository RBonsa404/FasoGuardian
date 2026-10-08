import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Session } from 'api';

import { Inscription } from './inscription';

@Component({ template: 'accueil' })
class AccueilFactice {}

function saisir(champ: HTMLInputElement, texte: string): void {
  champ.value = texte;
  champ.dispatchEvent(new Event('input'));
}

describe('Inscription', () => {
  let harnais: RouterTestingHarness;
  let serveur: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: '', component: AccueilFactice },
            { path: 'verification/:etape', component: AccueilFactice },
            { path: 'inscription/:etape', component: Inscription },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    harnais = await RouterTestingHarness.create('/inscription/numero');
    serveur = TestBed.inject(HttpTestingController);
    page = harnais.routeNativeElement!;
  });

  afterEach(() => serveur.verify());

  const champ = () => page.querySelector('input') as HTMLInputElement;
  const action = () => page.querySelector('button[fg-button][type=submit], [pied] button, button[fg-button]:last-of-type') as HTMLButtonElement;
  const soumettre = async () => {
    (page.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await harnais.fixture.whenStable();
  };

  it('refuse un numéro incomplet sans appeler le serveur', async () => {
    saisir(champ(), '7012');
    await soumettre();

    expect(page.textContent).toContain('Saisissez 8 chiffres.');
    expect(TestBed.inject(Router).url).toBe('/inscription/numero');
  });

  it('mène du numéro à la création du compte, puis à la vérification', async () => {
    saisir(champ(), '70123456');
    await soumettre();
    const envoi = serveur.expectOne('/api/v1/auth/inscription/numero');
    expect(envoi.request.body).toEqual({ telephone: '70123456' });
    envoi.flush(null, { status: 202, statusText: 'Accepted' });
    await harnais.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/inscription/code');
    expect(page.textContent).toContain('+226 70 12 34 56');

    saisir(champ(), '481902');
    await harnais.fixture.whenStable();
    serveur.expectOne('/api/v1/auth/inscription/code').flush({ preuve: 'preuve-1' });
    await harnais.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/inscription/mot-de-passe');

    saisir(champ(), 'soleil2026');
    await soumettre();
    expect(TestBed.inject(Router).url).toBe('/inscription/accords');
    const interrupteurs = Array.from(page.querySelectorAll('button[role=switch]')) as HTMLButtonElement[];
    expect(interrupteurs.map((i) => i.getAttribute('aria-checked'))).toEqual(['true', 'true', 'true', 'false']);
    expect(interrupteurs[0].disabled).toBe(true);
    interrupteurs[2].click();
    await harnais.fixture.whenStable();

    action().click();
    const fin = serveur.expectOne('/api/v1/auth/inscription/terminer');
    expect(fin.request.body).toEqual({
      preuve: 'preuve-1',
      motDePasse: 'soleil2026',
      consentements: ['CONDITIONS_GENERALES', 'DONNEES_ENFANT'],
    });
    fin.flush({ jetonAcces: 'jeton', expireDansSecondes: 900 }, { status: 201, statusText: 'Created' });
    await harnais.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/verification/mode');
    expect(TestBed.inject(Session).ouverte()).toBe(true);
  });

  it('affiche sous le champ le message du serveur pour un code incorrect', async () => {
    saisir(champ(), '70123456');
    await soumettre();
    serveur.expectOne('/api/v1/auth/inscription/numero').flush(null, { status: 202, statusText: 'Accepted' });
    await harnais.fixture.whenStable();

    saisir(champ(), '000000');
    await harnais.fixture.whenStable();
    serveur.expectOne('/api/v1/auth/inscription/code').flush(
      { type: 'x', title: 'Code incorrect', status: 400, detail: 'Code incorrect. Essais restants : 2.', code: 'CODE_INCORRECT' },
      { status: 400, statusText: 'Bad Request' },
    );
    await harnais.fixture.whenStable();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('Essais restants : 2');
    expect(TestBed.inject(Router).url).toBe('/inscription/code');
  });

  it('renvoie au début du parcours si une étape est ouverte sans numéro vérifié', async () => {
    await harnais.navigateByUrl('/inscription/mot-de-passe');
    await harnais.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/inscription/numero');
  });

  it('signale la perte de réseau en bannière', async () => {
    saisir(champ(), '70123456');
    await soumettre();
    serveur.expectOne('/api/v1/auth/inscription/numero').error(new ProgressEvent('error'), { status: 0 });
    await harnais.fixture.whenStable();

    expect(page.querySelector('fg-banner')?.textContent).toContain('Pas de connexion');
  });
});
