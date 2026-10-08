import { ilYA } from '../commun/temps';

describe('ancienneté du dernier contact', () => {
  const maintenant = new Date('2026-10-08T14:30:00Z');

  it("s'exprime en minutes, en heures puis en jours", () => {
    expect(ilYA('2026-10-08T14:29:40Z', maintenant)).toBe("à l'instant");
    expect(ilYA('2026-10-08T14:28:00Z', maintenant)).toBe('il y a 2 min');
    expect(ilYA('2026-10-08T13:31:00Z', maintenant)).toBe('il y a 59 min');
    expect(ilYA('2026-10-08T11:29:00Z', maintenant)).toBe('il y a 3 h');
    expect(ilYA('2026-10-06T14:00:00Z', maintenant)).toBe('il y a 2 j');
  });

  it("ne produit pas de durée négative si l'horloge du téléphone retarde", () => {
    expect(ilYA('2026-10-08T14:31:00Z', maintenant)).toBe("à l'instant");
  });
});
