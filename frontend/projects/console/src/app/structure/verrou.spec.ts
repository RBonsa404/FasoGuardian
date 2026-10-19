import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ClientAuthentification, ClientConsole, Session } from 'api';

import { INACTIVITE_MAXIMALE_MS, Verrou } from './verrou';

@Component({
  imports: [Verrou],
  template: `<app-verrou identifiant="agent.kyc@fasoguardian.bf" />`,
})
class Hote {}

describe('verrou de session de la console (écran 55)', () => {
  const expiree = signal(false);
  const session = {
    reauthentificationRequise: expiree.asReadonly(),
    signalerExpiration: vi.fn(() => expiree.set(true)),
  };
  let deconnexion: ReturnType<typeof vi.fn>;
  let connexion: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    expiree.set(false);
    session.signalerExpiration.mockClear();
    deconnexion = vi.fn(() => of(undefined));
    connexion = vi.fn(() => {
      expiree.set(false);
      return of({ jetonAcces: 'j' });
    });
  });

  afterEach(() => vi.useRealTimers());

  function monter() {
    TestBed.configureTestingModule({
      providers: [
        { provide: Session, useValue: session },
        { provide: ClientAuthentification, useValue: { deconnecter: deconnexion } },
        { provide: ClientConsole, useValue: { connecter: connexion } },
        { provide: INACTIVITE_MAXIMALE_MS, useValue: 1_000 },
      ],
    });
    const fixture = TestBed.createComponent(Hote);
    const page = fixture.nativeElement as HTMLElement;
    const dialog = page.querySelector('dialog') as HTMLDialogElement;
    dialog.showModal = function () {
      this.setAttribute('open', '');
    };
    dialog.close = function () {
      this.removeAttribute('open');
    };
    fixture.detectChanges();
    const verrou = fixture.debugElement.children[0].componentInstance as Verrou;
    return { fixture, page, dialog, verrou: verrou as unknown as Record<string, { setValue(v: string): void }> };
  }

  it('reste ouvert tant que l’agent agit, puis se verrouille et ferme la session côté serveur', () => {
    const { fixture, dialog } = monter();

    vi.advanceTimersByTime(900);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }));
    vi.advanceTimersByTime(900);
    fixture.detectChanges();
    expect(session.signalerExpiration).not.toHaveBeenCalled();
    expect(dialog.hasAttribute('open')).toBe(false);

    vi.advanceTimersByTime(200);
    fixture.detectChanges();
    expect(deconnexion).toHaveBeenCalledTimes(1);
    expect(expiree()).toBe(true);
    expect(dialog.hasAttribute('open')).toBe(true);
  });

  it('s’affiche aussi quand le serveur a laissé expirer la session', () => {
    const { fixture, page, dialog } = monter();
    expiree.set(true);
    fixture.detectChanges();
    expect(dialog.hasAttribute('open')).toBe(true);
    expect(page.textContent).toContain('agent.kyc@fasoguardian.bf');
    expect(deconnexion).not.toHaveBeenCalled();
  });

  it('exige le mot de passe et le code, puis rouvre la console après réauthentification', () => {
    const { fixture, page, dialog, verrou } = monter();
    expiree.set(true);
    fixture.detectChanges();

    (page.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(connexion).not.toHaveBeenCalled();
    expect(page.textContent).toContain('Saisissez votre mot de passe et les 6 chiffres du code.');

    verrou['motDePasse'].setValue('secret');
    verrou['code'].setValue('123456');
    (page.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(connexion).toHaveBeenCalledWith('agent.kyc@fasoguardian.bf', 'secret', '123456');
    expect(dialog.hasAttribute('open')).toBe(false);
  });

  it('montre le refus du serveur et vide le code', () => {
    connexion = vi.fn(() => throwError(() => ({ status: 401, error: { detail: 'Code TOTP invalide.' } })));
    const { fixture, page, dialog, verrou } = monter();
    expiree.set(true);
    fixture.detectChanges();

    verrou['motDePasse'].setValue('secret');
    verrou['code'].setValue('000000');
    (page.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(page.querySelector('fg-banner')).not.toBeNull();
    expect(dialog.hasAttribute('open')).toBe(true);
    expect((verrou['code'] as unknown as { value: string }).value).toBe('');
  });
});
