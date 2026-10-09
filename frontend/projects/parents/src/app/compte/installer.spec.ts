import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { EtatPush, NotificationsPush, octets } from '../commun/notifications-push';
import { Installer } from './installer';

/** Double du service : les essais portent sur ce que l'écran dit et permet dans chaque état. */
class PushFactice {
  readonly etat = signal<EtatPush>('inconnu');
  activations = 0;
  desactivations = 0;
  apresActivation: EtatPush = 'actives';

  constructor(private readonly initial: EtatPush) {}

  async actualiser(): Promise<void> {
    this.etat.set(this.initial);
  }

  async activer(): Promise<void> {
    this.activations++;
    this.etat.set(this.apresActivation);
  }

  async desactiver(): Promise<void> {
    this.desactivations++;
    this.etat.set('a-activer');
  }
}

describe('installer l’application et activer les notifications', () => {
  async function monter(initial: EtatPush) {
    const push = new PushFactice(initial);
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: NotificationsPush, useValue: push }] });
    const fixture = TestBed.createComponent(Installer);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const interrupteur = page.querySelector('button[role="switch"]') as HTMLButtonElement;
    return { fixture, push, page, interrupteur };
  }

  it('active les notifications à la demande du parent et le confirme', async () => {
    const { fixture, push, page, interrupteur } = await monter('a-activer');
    expect(interrupteur.getAttribute('aria-checked')).toBe('false');

    interrupteur.click();
    await fixture.whenStable();

    expect(push.activations).toBe(1);
    expect(interrupteur.getAttribute('aria-checked')).toBe('true');
    expect(page.textContent).toContain('Les alertes arrivent sur cet appareil');
  });

  it('désactive les notifications d’un appareil abonné', async () => {
    const { fixture, push, interrupteur } = await monter('actives');
    expect(interrupteur.getAttribute('aria-checked')).toBe('true');

    interrupteur.click();
    await fixture.whenStable();

    expect(push.desactivations).toBe(1);
    expect(interrupteur.getAttribute('aria-checked')).toBe('false');
  });

  it('dit que les alertes arrivent par SMS quand le navigateur bloque les notifications', async () => {
    const { page, interrupteur } = await monter('refusees');

    expect(page.textContent).toContain('bloquées pour ce site');
    expect(page.textContent).toContain('les alertes arrivent par SMS');
    expect(interrupteur.disabled).toBe(true);
  });

  it('reste honnête quand l’appareil ne peut pas recevoir de notifications', async () => {
    const { page, interrupteur } = await monter('indisponibles');

    expect(page.textContent).toContain('Les alertes vous parviennent par SMS');
    expect(interrupteur.disabled).toBe(true);
  });

  it('un refus au moment de l’autorisation laisse l’interrupteur éteint', async () => {
    const { fixture, push, page, interrupteur } = await monter('a-activer');
    push.apresActivation = 'refusees';

    interrupteur.click();
    await fixture.whenStable();

    expect(interrupteur.getAttribute('aria-checked')).toBe('false');
    expect(page.textContent).toContain('bloquées pour ce site');
  });
});

describe('clé publique du serveur d’application', () => {
  it('se décode du base64url vers les 65 octets attendus par le navigateur', () => {
    const cle = 'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U';

    const tableau = octets(cle);

    expect(tableau.length).toBe(65);
    expect(tableau[0]).toBe(4);
  });
});
