import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ClientFamille, ClientProfil, ContactUrgence, TuteursEnfant } from 'api';

import { Contacts, telephoneLisible } from './contacts';

const SEULE: TuteursEnfant = {
  tuteurs: [{ prenom: 'Mariam', nom: 'Ouédraogo', telephone: '+22670123456', principal: true, vous: true, suspendu: false }],
  invitation: null,
  peutInviter: true,
};

const CONTACTS: ContactUrgence[] = [
  { id: 'c-1', lien: 'Tante', nom: 'Fatimata Sawadogo', telephone: '+22665087721', visibleSurQr: true, rang: 1 },
];

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ').trim();
}

function bouton(page: HTMLElement, nom: string): HTMLButtonElement {
  return [...page.querySelectorAll('button')].find((b) => lire(b) === nom) as HTMLButtonElement;
}

async function ouvrir(famille: Partial<ClientFamille>) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ClientFamille, useValue: { tuteurs: () => of(SEULE), contacts: () => of(CONTACTS), ...famille } },
      { provide: ClientProfil, useValue: { demanderSecondFacteur: () => of(undefined) } },
    ],
  });
  const fixture = TestBed.createComponent(Contacts);
  fixture.componentRef.setInput('id', 'e-1');
  await fixture.whenStable();
  return { fixture, page: fixture.nativeElement as HTMLElement };
}

describe('tuteurs et contacts', () => {
  it('présente les tuteurs avec leur rôle, puis les contacts d’urgence', async () => {
    const tuteurs = vi.fn().mockReturnValue(
      of({
        tuteurs: [SEULE.tuteurs[0], { prenom: 'Ibrahim', nom: 'Ouédraogo', telephone: '+22676451290', principal: false, vous: false, suspendu: false }],
        invitation: null,
        peutInviter: false,
      } satisfies TuteursEnfant),
    );
    const { page } = await ouvrir({ tuteurs });

    const cartes = [...page.querySelectorAll('ul[aria-label="Tuteurs"] > li')].map(lire);
    expect(cartes[0]).toContain('Mariam Ouédraogo (vous)');
    expect(cartes[0]).toContain('+226 70 12 34 56');
    expect(cartes[0]).toContain('Tuteur principal');
    expect(cartes[1]).toContain('Ibrahim');
    expect(cartes[1]).toContain('Second tuteur');
    expect(lire(page)).toContain('Fatimata Sawadogo');
    expect(lire(page)).toContain('SMS en cascade');
    expect(lire(page)).toContain('Page QR');
    // Deux tuteurs : plus d'invitation possible
    expect(bouton(page, 'Inviter un second tuteur')).toBeUndefined();
  });

  it('invite un second tuteur après le code SMS, et redemande le code s’il est faux', async () => {
    const inviterTuteur = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { status: 400, code: 'CODE_INCORRECT', detail: 'Code incorrect.' } })))
      .mockReturnValueOnce(of({ telephone: '+22676451290', statut: 'EN_ATTENTE', expireLe: '2026-10-21T09:00:00Z' }));
    const { fixture, page } = await ouvrir({ inviterTuteur } as Partial<ClientFamille>);
    const composant = fixture.componentInstance as unknown as { numeroInvite: { setValue(v: string): void }; inviter(code: string): void };

    bouton(page, 'Inviter un second tuteur').click();
    await fixture.whenStable();
    composant.numeroInvite.setValue('76451290');
    bouton(page, 'Continuer').click();
    await fixture.whenStable();
    expect(lire(page)).toContain('Pour inviter +226 76 45 12 90 comme second tuteur');

    composant.inviter('000000');
    await fixture.whenStable();
    expect(inviterTuteur).toHaveBeenLastCalledWith('e-1', '76451290', '000000');
    expect(lire(page)).toContain('Code incorrect.');

    composant.inviter('123456');
    await fixture.whenStable();
    expect(lire(page)).toContain('Invitation envoyée par SMS');
  });

  it('montre l’invitation en cours et permet de l’annuler', async () => {
    const tuteurs = vi
      .fn()
      .mockReturnValueOnce(of({ ...SEULE, invitation: { telephone: '+22676451290', statut: 'EN_VERIFICATION', expireLe: '2026-10-21T09:00:00Z' }, peutInviter: false }))
      .mockReturnValueOnce(of(SEULE));
    const annulerInvitation = vi.fn().mockReturnValue(of(undefined));
    const { fixture, page } = await ouvrir({ tuteurs, annulerInvitation } as Partial<ClientFamille>);

    expect(lire(page)).toContain("Vérification d'identité en cours");
    bouton(page, "Annuler l'invitation").click();
    await fixture.whenStable();
    expect(annulerInvitation).toHaveBeenCalledWith('e-1');
    expect(lire(page)).not.toContain("Vérification d'identité en cours");
    expect(bouton(page, 'Inviter un second tuteur')).toBeDefined();
  });

  it('écrit les numéros par paires', () => {
    expect(telephoneLisible('+22670123456')).toBe('+226 70 12 34 56');
    expect(telephoneLisible(null)).toBe('');
  });
});
