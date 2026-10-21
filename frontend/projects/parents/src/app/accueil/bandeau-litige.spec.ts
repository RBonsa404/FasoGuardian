import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ClientMesLitiges, LitigeDuTuteur } from 'api';

import { BandeauLitige } from './bandeau-litige';

function afficher(litiges: LitigeDuTuteur[] | Error, enfant = 'e-awa') {
  TestBed.configureTestingModule({
    providers: [{ provide: ClientMesLitiges, useValue: { mesLitiges: () => (litiges instanceof Error ? throwError(() => litiges) : of(litiges)) } }],
  });
  const fixture = TestBed.createComponent(BandeauLitige);
  fixture.componentRef.setInput('enfant', enfant);
  fixture.componentRef.setInput('prenom', 'Awa');
  fixture.detectChanges();
  return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('bandeau du litige de filiation (US-KYC-001)', () => {
  it('explique le gel des réglages de l’enfant concerné, et la position suspendue', () => {
    const texte = afficher([{ enfant: 'e-awa', geolocalisationSuspendue: true, ouvertLe: '2026-10-20T08:00:00Z' }]);

    expect(texte).toContain("Signalement en cours d'instruction");
    expect(texte).toContain('Un signalement sur votre lien avec Awa est en cours d');
    expect(texte).toContain('Ses réglages sont gelés jusqu');
    expect(texte).toContain('Sa position est suspendue pendant ce temps.');
    expect(texte).toContain('Page QR et SOS toujours actifs');
  });

  it('ne dit rien de la position quand elle n’est pas suspendue', () => {
    expect(afficher([{ enfant: 'e-awa', geolocalisationSuspendue: false, ouvertLe: '2026-10-20T08:00:00Z' }])).not.toContain('position');
  });

  it('reste muet pour un autre enfant, sans litige ou si la lecture échoue', () => {
    expect(afficher([{ enfant: 'e-awa', geolocalisationSuspendue: true, ouvertLe: '2026-10-20T08:00:00Z' }], 'e-ibrahim')).toBe('');
    TestBed.resetTestingModule();
    expect(afficher([])).toBe('');
    TestBed.resetTestingModule();
    expect(afficher(new Error('réseau'))).toBe('');
  });
});
