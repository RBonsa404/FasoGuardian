import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { Abonnement, ClientAbonnements, ClientFamille, FicheEnfant, Offre, Paiement } from 'api';

import { EcranAbonnement } from './abonnement';
import { BandeauAbonnement } from './bandeau';
import { jourCourt, jourLong, mois, montant, resumeOffre } from './libelles';
import { EcranPaiement, INTERROGATION_MS } from './paiement';

const OFFRES: Offre[] = [
  { code: 'ESSENTIEL', libelle: 'Essentiel', prixFcfa: 1500, intervalleS: 900, zonesMaximum: 1, historiqueJours: 1 },
  { code: 'INTERMEDIAIRE', libelle: 'Intermédiaire', prixFcfa: 2250, intervalleS: 300, zonesMaximum: 3, historiqueJours: 30 },
  { code: 'PREMIUM', libelle: 'Premium', prixFcfa: 5000, intervalleS: 300, zonesMaximum: 3, historiqueJours: 90 },
];

function abonnement(partiel: Partial<Abonnement> = {}): Abonnement {
  return {
    statut: 'ACTIF',
    offre: 'INTERMEDIAIRE',
    libelle: 'Intermédiaire',
    prixFcfa: 2250,
    prochaineEcheance: '2030-11-07',
    restrictionLe: null,
    renouvellementAuto: true,
    moyen: 'ORANGE_MONEY',
    numeroMasque: '+226 70 •• •• 56',
    droits: { offre: 'INTERMEDIAIRE', zonesMaximum: 3, historiqueJours: 30, intervalleS: 300, suiviContinu: true },
    paiementEnCours: null,
    ...partiel,
  };
}

function paiement(partiel: Partial<Paiement> = {}): Paiement {
  return {
    id: 'p-1',
    statut: 'INITIE',
    offre: 'INTERMEDIAIRE',
    montantFcfa: 2250,
    moyen: 'ORANGE_MONEY',
    expireLe: new Date(Date.now() + 120_000).toISOString(),
    motifEchec: null,
    recu: null,
    ...partiel,
  };
}

/** Double du client : chaque essai règle ce que le serveur répondrait. */
class ClientFactice {
  etat: Abonnement = abonnement();
  reponseAuPaiement: Observable<Paiement> = of(paiement());
  suivis: Paiement[] = [];
  demandes: { cle: string; numero: string }[] = [];
  renouvellements: boolean[] = [];

  offres() {
    return of(OFFRES);
  }

  abonnement() {
    return of(this.etat);
  }

  payer(_enfant: string, saisie: { numero: string }, cle: string) {
    this.demandes.push({ cle, numero: saisie.numero });
    return this.reponseAuPaiement;
  }

  paiement() {
    return of(this.suivis.shift() ?? paiement());
  }

  choisirRenouvellement(_enfant: string, automatique: boolean) {
    this.renouvellements.push(automatique);
    return of({ ...this.etat, renouvellementAuto: automatique });
  }
}

/** Texte visible, espaces insécables et retours à la ligne ramenés à une espace simple. */
function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

const AWA = { id: 'e-1', prenom: 'Awa' } as FicheEnfant;
const YACOUBA = { id: 'e-2', prenom: 'Yacouba' } as FicheEnfant;

function preparer(client: ClientFactice, enfants: FicheEnfant[] = [AWA]) {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: ClientAbonnements, useValue: client }, { provide: ClientFamille, useValue: { mesEnfants: () => of(enfants) } }],
  });
}

describe('mon abonnement', () => {
  async function monter(client: ClientFactice, enfants?: FicheEnfant[]) {
    preparer(client, enfants);
    const fixture = TestBed.createComponent(EcranAbonnement);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('montre l’offre en cours, son échéance et les offres à comparer', async () => {
    const { page } = await monter(new ClientFactice());

    expect(lire(page)).toContain('2 250 FCFA / mois');
    expect(lire(page)).toContain('Prochaine échéance 7 nov. · renouvellement auto · Orange Money');
    expect(lire(page)).toContain('Intermédiaire · actuelle');
    expect(lire(page)).toContain('Position toutes les 15 min · 1 Safe Zone · historique 24 h');
    expect(lire(page)).toContain('SOS, détection de retrait et page QR inclus dans toutes les offres.');
    // À jour et loin de l'échéance : aucun bouton de paiement n'insiste.
    expect([...page.querySelectorAll('button')].some((b) => lire(b).includes('Payer'))).toBe(false);
  });

  it('en retard, dit que tout fonctionne encore et jusqu’à quand', async () => {
    const client = new ClientFactice();
    client.etat = abonnement({ statut: 'EN_RETARD', prochaineEcheance: '2026-11-07', restrictionLe: '2026-11-22' });

    const { page } = await monter(client);

    expect(lire(page)).toContain('Paiement non reçu');
    expect(lire(page)).toContain('Réglez avant le 22 nov.');
    expect(lire(page)).toContain('Page QR et SOS toujours actifs');
    expect([...page.querySelectorAll('button')].some((b) => lire(b).includes('Payer 2 250 FCFA'))).toBe(true);
  });

  it('restreint, dit ce qui est suspendu et ce qui reste actif', async () => {
    const client = new ClientFactice();
    client.etat = abonnement({ statut: 'RESTREINT', prochaineEcheance: '2026-11-07' });

    const { page } = await monter(client);

    expect(lire(page)).toContain('Suivi continu suspendu');
    expect(lire(page)).toContain('Position à la demande uniquement, historique limité à 24 h.');
    expect(lire(page)).toContain('Page QR et SOS toujours actifs');
  });

  it('sans abonnement, invite à choisir une offre', async () => {
    const client = new ClientFactice();
    client.etat = abonnement({ statut: null, offre: null, libelle: null, prixFcfa: null, prochaineEcheance: null, moyen: null, numeroMasque: null });

    const { page } = await monter(client);

    expect(lire(page)).toContain('Aucun abonnement pour cet enfant.');
    expect(page.querySelector('button[role="switch"]')).toBeNull();
  });

  it('le parent renonce au renouvellement automatique', async () => {
    const client = new ClientFactice();
    const { fixture, page } = await monter(client);
    const interrupteur = page.querySelector('button[role="switch"]') as HTMLButtonElement;
    expect(interrupteur.getAttribute('aria-checked')).toBe('true');

    interrupteur.click();
    await fixture.whenStable();

    expect(client.renouvellements).toEqual([false]);
    expect(lire(page)).toContain('à renouveler vous-même');
  });

  it('avec plusieurs enfants, propose de choisir celui que l’on regarde', async () => {
    const { page } = await monter(new ClientFactice(), [AWA, YACOUBA]);

    const choix = [...page.querySelectorAll('[role="group"] button')];
    expect(choix.map((b) => b.textContent?.trim())).toEqual(['Awa', 'Yacouba']);
    expect(choix[0].getAttribute('aria-pressed')).toBe('true');
  });
});

describe('rappel d’impayé du tableau de bord', () => {
  async function monter(etat: Abonnement) {
    const client = new ClientFactice();
    client.etat = etat;
    preparer(client);
    const fixture = TestBed.createComponent(BandeauAbonnement);
    fixture.componentRef.setInput('enfant', 'e-1');
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('reste muet quand l’abonnement est à jour', async () => {
    expect((await monter(abonnement())).textContent?.trim()).toBe('');
  });

  it('prévient d’un retard puis d’une suspension', async () => {
    expect(lire(await monter(abonnement({ statut: 'EN_RETARD', restrictionLe: '2026-11-22' })))).toContain('réglez avant le 22 nov.');
    TestBed.resetTestingModule();
    expect(lire(await monter(abonnement({ statut: 'RESTREINT' })))).toContain('Page QR et SOS toujours actifs');
  });
});

describe('paiement mobile money', () => {
  async function monter(client: ClientFactice) {
    preparer(client);
    const fixture = TestBed.createComponent(EcranPaiement);
    fixture.componentRef.setInput('enfant', 'e-1');
    fixture.componentRef.setInput('offre', 'INTERMEDIAIRE');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const bouton = (nom: string) => [...page.querySelectorAll('button')].find((b) => lire(b).includes(nom)) as HTMLButtonElement;
    const saisir = async (numero: string) => {
      const champ = page.querySelector('input[type="tel"]') as HTMLInputElement;
      champ.value = numero;
      champ.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    /** Avec l'horloge factice, la stabilité s'obtient en vidant les tâches en attente. */
    const stable = async () => {
      await vi.advanceTimersByTimeAsync(0);
      fixture.detectChanges();
    };
    return { fixture, page, bouton, saisir, stable };
  }

  afterEach(() => vi.useRealTimers());

  it('refuse un numéro incomplet sans rien demander au serveur', async () => {
    const client = new ClientFactice();
    const { fixture, page, bouton, saisir } = await monter(client);
    await saisir('7012');

    bouton('Payer 2 250 FCFA').click();
    await fixture.whenStable();

    expect(lire(page)).toContain('Saisissez les 8 chiffres du numéro.');
    expect(client.demandes).toEqual([]);
  });

  it('attend la validation, interroge le serveur puis confirme avec le reçu', async () => {
    const client = new ClientFactice();
    client.suivis = [paiement(), paiement({ statut: 'CONFIRME', recu: 'FG-R-2026-11-0381' })];
    const { page, bouton, saisir, stable } = await monter(client);
    await saisir('70123456');
    vi.useFakeTimers();

    bouton('Payer 2 250 FCFA').click();
    await stable();

    expect(client.demandes).toHaveLength(1);
    expect(client.demandes[0].numero).toBe('70123456');
    expect(lire(page)).toContain('Validez sur votre téléphone');
    expect(lire(page)).toContain('Orange Money vous envoie une demande.');
    expect(lire(page)).toContain('Expire dans 2:00');

    await vi.advanceTimersByTimeAsync(INTERROGATION_MS);
    await stable();
    expect(lire(page)).toContain('Validez sur votre téléphone');

    await vi.advanceTimersByTimeAsync(INTERROGATION_MS);
    await stable();
    expect(lire(page)).toContain('Paiement reçu');
    expect(lire(page)).toContain("Abonnement Intermédiaire actif jusqu'au 7 novembre 2030. Reçu FG-R-2026-11-0381 envoyé par SMS.");
  });

  it('un refus de l’opérateur dit qu’aucun débit n’a eu lieu et laisse réessayer avec une nouvelle clé', async () => {
    const client = new ClientFactice();
    client.reponseAuPaiement = of(paiement({ statut: 'ECHOUE', motifEchec: 'Solde insuffisant' }));
    const { fixture, page, bouton, saisir } = await monter(client);
    await saisir('70123400');
    bouton('Payer 2 250 FCFA').click();
    await fixture.whenStable();

    expect(lire(page)).toContain('Paiement non abouti');
    expect(lire(page)).toContain('Solde insuffisant.');
    expect(lire(page)).toContain("Aucun débit n'a été effectué.");

    bouton('Réessayer').click();
    await fixture.whenStable();
    bouton('Payer 2 250 FCFA').click();
    await fixture.whenStable();

    expect(client.demandes).toHaveLength(2);
    expect(client.demandes[0].cle).not.toBe(client.demandes[1].cle);
  });

  it('passé le délai sans réponse, explique que rien n’est débité sans validation', async () => {
    const client = new ClientFactice();
    const { page, bouton, saisir, stable } = await monter(client);
    await saisir('70123456');
    vi.useFakeTimers();
    client.reponseAuPaiement = of(paiement({ expireLe: new Date(Date.now() + 3000).toISOString() }));
    bouton('Payer 2 250 FCFA').click();
    await stable();
    client.suivis = [paiement({ expireLe: new Date(Date.now() - 1000).toISOString() })];

    await vi.advanceTimersByTimeAsync(4000);
    await stable();

    expect(lire(page)).toContain('Pas de confirmation reçue');
    expect(lire(page)).toContain("Sans validation de votre part, rien n'est débité.");
  });

  it('montre l’erreur du serveur sans quitter la saisie', async () => {
    const client = new ClientFactice();
    client.reponseAuPaiement = throwError(() => new Error('réseau'));
    const { fixture, page, bouton, saisir } = await monter(client);
    await saisir('70123456');

    bouton('Payer 2 250 FCFA').click();
    await fixture.whenStable();

    expect(page.querySelector('input[type="tel"]')).not.toBeNull();
    expect(page.querySelector('fg-banner')).not.toBeNull();
  });
});

describe('libellés de l’abonnement', () => {
  it('écrit les montants, les dates et le résumé d’une offre', () => {
    expect(montant(2250).replace(/\s/g, ' ')).toBe('2 250');
    expect(jourCourt('2026-11-07')).toBe('7 nov.');
    expect(jourLong('2026-12-07')).toBe('7 décembre 2026');
    expect(mois('2026-10-09')).toBe('Octobre 2026');
    expect(resumeOffre(OFFRES[1])).toBe('Position toutes les 5 min · 3 Safe Zones · historique 30 jours');
  });
});
