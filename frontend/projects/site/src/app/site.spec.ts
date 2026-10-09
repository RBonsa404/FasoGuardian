import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import * as THREE from 'three';

import { repliDemande } from './bracelet-3d/bracelet-3d';
import { COLORIS, colorer, construireBracelet, eclater } from './bracelet-3d/modele';
import { COMPARATIF, OFFRES } from './contenu';
import { Faq, Introuvable, PointsRelais } from './pages/aide';
import { avancement } from './pages/bracelet';
import { Confidentialite } from './pages/legal';
import { Offres } from './pages/produit';

function lire(element: Element): string {
  return (element.textContent ?? '').replace(/\s+/g, ' ');
}

async function monter<T>(composant: new (...args: never[]) => T) {
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
  const fixture = TestBed.createComponent(composant as never);
  await fixture.whenStable();
  return { fixture, page: fixture.nativeElement as HTMLElement };
}

describe('modèle du bracelet', () => {
  it('nomme les pièces comme le dossier de design le demande', () => {
    const bracelet = construireBracelet(THREE);
    const noms = new Set<string>();
    bracelet.racine.traverse((objet) => noms.add(objet.name));

    for (const attendu of ['coque', 'capot', 'qr_grave', 'bouton_sos', 'led', 'contacts_charge_0', 'contacts_charge_1', 'carte', 'batterie', 'antennes', 'sangle', 'fermoir_securite']) {
      expect(noms.has(attendu), attendu).toBe(true);
    }
  });

  it('tient dans 48 × 38 mm, sangle exclue', () => {
    const bracelet = construireBracelet(THREE);
    const boite = new THREE.Box3().setFromObject(bracelet.racine.getObjectByName('boitier')!);
    const taille = boite.getSize(new THREE.Vector3());

    expect(taille.x * 1000).toBeGreaterThan(45);
    expect(taille.x * 1000).toBeLessThan(49);
    expect(taille.z * 1000).toBeLessThan(42);
  });

  it('écarte le capot, les antennes, la carte et la batterie dans cet ordre de hauteur', () => {
    const bracelet = construireBracelet(THREE);

    eclater(bracelet, 1);
    expect(bracelet.capot.position.y).toBeGreaterThan(bracelet.antennes.position.y);
    expect(bracelet.antennes.position.y).toBeGreaterThan(bracelet.carte.position.y);
    expect(bracelet.carte.position.y).toBeGreaterThan(bracelet.batterie.position.y);

    eclater(bracelet, 0);
    expect(bracelet.capot.position.y).toBeCloseTo(bracelet.repos.capot);
    // Une valeur hors bornes ne projette pas les pièces hors de la scène.
    eclater(bracelet, 7);
    expect(bracelet.capot.position.y).toBeCloseTo(bracelet.repos.capot + 0.044);
  });

  it('change la sangle et le capot avec le coloris', () => {
    const bracelet = construireBracelet(THREE);
    const ecole = COLORIS.find((coloris) => coloris.nom === 'Édition École')!;

    colorer(bracelet, ecole);

    expect(bracelet.matiereSangle.color.getHex()).toBe(0xe6eaf2);
    expect(bracelet.matiereCapot.color.getHex()).toBe(0x0a2246);
  });
});

describe('repli sans 3D', () => {
  const fenetre = (navigateur: object, reduit = false) => ({ navigator: navigateur, matchMedia: () => ({ matches: reduit }) }) as unknown as Window;

  it('est demandé en économie de données, sur un appareil modeste ou si les animations sont réduites', () => {
    expect(repliDemande(fenetre({ connection: { saveData: true } }))).toBe(true);
    expect(repliDemande(fenetre({ deviceMemory: 2 }))).toBe(true);
    expect(repliDemande(fenetre({}, true))).toBe(true);
    expect(repliDemande(fenetre({ deviceMemory: 8, connection: { saveData: false } }))).toBe(false);
  });
});

describe('défilement de la page du bracelet', () => {
  it('va de 0 à l’entrée de la section à 1 à sa sortie', () => {
    expect(avancement(200, 2000, 800)).toBe(0);
    expect(avancement(0, 2000, 800)).toBe(0);
    expect(avancement(-600, 2000, 800)).toBe(0.5);
    expect(avancement(-5000, 2000, 800)).toBe(1);
    // Section pas plus haute que la fenêtre : rien à faire défiler.
    expect(avancement(-100, 600, 800)).toBe(0);
  });
});

describe('pages', () => {
  it('le comparatif donne une valeur par offre, et ne promet que ce que le catalogue contient', async () => {
    const { page } = await monter(Offres);

    expect(COMPARATIF.every((ligne) => ligne.valeurs.length === OFFRES.length)).toBe(true);
    const entetes = [...page.querySelectorAll('thead th')].slice(1).map((th) => th.textContent?.trim());
    expect(entetes).toEqual(['Essentiel', 'Intermédiaire', 'Premium', 'École']);
    const lignes = [...page.querySelectorAll('tbody tr')].map(lire);
    expect(lignes.find((l) => l.includes('Historique'))).toContain('24 h30 jours90 jours30 jours');
    expect(lire(page)).not.toContain('Illimité');
    expect(lire(page)).not.toContain('prioritaire');
  });

  it('la FAQ n’ouvre qu’une réponse à la fois', async () => {
    const { fixture, page } = await monter(Faq);
    const boutons = [...page.querySelectorAll('button')];

    expect(boutons[0].getAttribute('aria-expanded')).toBe('true');
    expect(lire(page)).toContain("Il n'a ni écran, ni caméra, ni micro.");

    boutons[2].click();
    await fixture.whenStable();

    expect(boutons[0].getAttribute('aria-expanded')).toBe('false');
    expect(boutons[2].getAttribute('aria-expanded')).toBe('true');
    expect(page.querySelectorAll('li p').length).toBe(1);
    expect(lire(page)).toContain('il envoie ses alertes par SMS');
  });

  it('les points relais se filtrent par ville et se disent indicatifs', async () => {
    const { fixture, page } = await monter(PointsRelais);

    expect(lire(page)).toContain("Le réseau est en cours d'ouverture");
    expect(page.querySelectorAll('ul li').length).toBe(5);
    ([...page.querySelectorAll('button')].find((b) => lire(b).includes('Bobo-Dioulasso')) as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(page.querySelectorAll('ul li').length).toBe(1);
  });

  it('la politique de confidentialité publie les durées appliquées et ne promet pas d’appel masqué', async () => {
    const { page } = await monter(Confidentialite);

    expect(lire(page)).toContain('Journal des alertes : 5 ans.');
    expect(lire(page)).toContain('la personne compose le numéro de ce contact');
    expect(lire(page)).not.toContain('masqué');
    expect(page.querySelectorAll('nav a').length).toBe(page.querySelectorAll('article h2').length);
  });

  it('la page introuvable oriente la personne qui a scanné un bracelet', async () => {
    const { page } = await monter(Introuvable);

    expect(lire(page)).toContain('Vous avez scanné un bracelet ?');
    expect(page.querySelector('a[href="/"]')).not.toBeNull();
  });
});
