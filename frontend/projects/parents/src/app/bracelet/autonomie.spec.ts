import { autonomiePleineH, autonomieRestanteH, duree } from './autonomie';

describe('autonomie estimée du bracelet (FG-DOC-08 tableau 9)', () => {
  it('suit le rythme d’émission et le réseau', () => {
    expect(autonomiePleineH(300, '4G', false)).toBe(104);
    expect(autonomiePleineH(300, '2G', false)).toBe(70);
    expect(autonomiePleineH(180, '4G', false)).toBe(74);
    expect(autonomiePleineH(900, '4G', false)).toBe(175);
    expect(autonomiePleineH(300, '4G', true)).toBe(31);
  });

  it('se proportionne au niveau de batterie, borné entre 0 et 100 %', () => {
    expect(autonomieRestanteH(50, 300, '4G')).toBe(52);
    expect(autonomieRestanteH(120, 300, '4G')).toBe(104);
    expect(autonomieRestanteH(-5, 300, '4G')).toBe(0);
  });

  it('s’écrit en heures sous deux jours, en jours au-delà', () => {
    expect(duree(41)).toBe('≈ 41 h');
    expect(duree(81)).toBe('≈ 3 j');
  });
});
