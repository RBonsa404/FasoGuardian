import { isPlatformBrowser } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, PLATFORM_ID, afterNextRender, effect, inject, input, signal, viewChild } from '@angular/core';

import type * as Three from 'three';

import { Bracelet, COLORIS, Coloris, colorer, construireBracelet, eclater } from './modele';

/** L'appareil ou la personne demande de l'économie : pas de scène 3D, l'illustration fixe suffit. */
export function repliDemande(fenetre: Window): boolean {
  const navigateur = fenetre.navigator as Navigator & { connection?: { saveData?: boolean }; deviceMemory?: number };
  return (
    navigateur.connection?.saveData === true ||
    (typeof navigateur.deviceMemory === 'number' && navigateur.deviceMemory < 3) ||
    fenetre.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
  );
}

/**
 * Bracelet en trois dimensions (HANDOFF §6). three.js n'est chargé qu'à l'entrée de la scène dans la
 * fenêtre ; avant cela, et sur un appareil modeste, en mode économie de données ou si les animations sont
 * réduites, une illustration fixe tient la place. La scène se tourne à la souris, au doigt ou au clavier.
 */
@Component({
  selector: 'app-bracelet-3d',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!pret()) {
      <svg class="absolute inset-0 m-auto h-4/5 w-4/5" viewBox="0 0 320 320" fill="none" aria-hidden="true">
        <ellipse cx="160" cy="176" rx="118" ry="104" stroke="var(--color-primary)" stroke-width="26" />
        <rect x="84" y="58" width="152" height="118" rx="30" fill="var(--color-surface-2)" stroke="var(--color-line-strong)" stroke-width="2" />
        <rect x="92" y="64" width="136" height="104" rx="26" fill="var(--color-primary)" />
        <g fill="var(--color-accent)" opacity="0.85">
          <rect x="110" y="84" width="22" height="22" rx="3" />
          <rect x="110" y="126" width="22" height="22" rx="3" />
          <rect x="152" y="84" width="22" height="22" rx="3" />
          <rect x="140" y="112" width="8" height="8" />
          <rect x="152" y="114" width="8" height="8" />
          <rect x="164" y="126" width="8" height="8" />
          <rect x="152" y="138" width="8" height="8" />
          <rect x="140" y="126" width="8" height="8" />
        </g>
        <circle cx="206" cy="82" r="5" fill="var(--color-accent)" />
        <rect x="232" y="100" width="12" height="34" rx="6" fill="var(--color-alert)" />
        <rect x="136" y="268" width="48" height="22" rx="6" fill="var(--color-line-strong)" />
      </svg>
    }
    <canvas
      #toile
      class="absolute inset-0 size-full touch-pan-y outline-none focus-visible:outline-2 focus-visible:outline-accent"
      [class.opacity-0]="!pret()"
      role="img"
      tabindex="0"
      [attr.aria-label]="libelle()"
      (pointerdown)="saisir($event)"
      (pointermove)="glisser($event)"
      (pointerup)="lacher($event)"
      (pointercancel)="lacher($event)"
      (keydown)="touche($event)"
    ></canvas>
  `,
  host: { class: 'relative block overflow-hidden' },
})
export class Bracelet3d {
  /** Description lue par les lecteurs d'écran : la scène elle-même ne leur dit rien. */
  readonly libelle = input.required<string>();
  /** Vue éclatée, de 0 (assemblé) à 1. */
  readonly explosion = input(0);
  /** Rotation imposée, en tours ; `null` laisse le bracelet tourner lentement de lui-même. */
  readonly tour = input<number | null>(null);
  readonly coloris = input<Coloris>(COLORIS[0]);

  protected readonly pret = signal(false);

  private readonly toile = viewChild.required<ElementRef<HTMLCanvasElement>>('toile');
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);
  private scene: { THREE: typeof Three; rendu: Three.WebGLRenderer; camera: Three.PerspectiveCamera; monde: Three.Scene; bracelet: Bracelet } | null = null;
  private angle = 0.6;
  private prise: { x: number; angle: number } | null = null;
  private image = 0;
  private visible = false;
  private detruit = false;

  constructor() {
    const navigateur = isPlatformBrowser(inject(PLATFORM_ID));
    const detruire = inject(DestroyRef);
    afterNextRender(() => {
      if (!navigateur || repliDemande(window) || typeof IntersectionObserver === 'undefined') {
        return;
      }
      const veilleur = new IntersectionObserver((entrees) => {
        this.visible = entrees.some((entree) => entree.isIntersecting);
        if (this.visible && !this.scene) {
          void this.monter();
        } else if (this.visible) {
          this.animer();
        }
      });
      veilleur.observe(this.hote.nativeElement);
      const redimensionner = () => this.cadrer();
      window.addEventListener('resize', redimensionner);
      detruire.onDestroy(() => {
        this.detruit = true;
        veilleur.disconnect();
        window.removeEventListener('resize', redimensionner);
        cancelAnimationFrame(this.image);
        this.scene?.rendu.dispose();
      });
    });
    effect(() => {
      // Lecture des entrées : tout changement redessine la scène.
      this.explosion();
      this.tour();
      this.coloris();
      this.dessiner();
    });
  }

  private async monter(): Promise<void> {
    const THREE = await import('three');
    if (this.detruit || this.scene) {
      return;
    }
    let rendu: Three.WebGLRenderer;
    try {
      rendu = new THREE.WebGLRenderer({ canvas: this.toile().nativeElement, antialias: true, alpha: true });
    } catch {
      // Pas de WebGL sur cet appareil : l'illustration fixe reste en place.
      return;
    }
    rendu.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    const monde = new THREE.Scene();
    monde.add(new THREE.HemisphereLight(0xffffff, 0x1a2139, 1.5));
    const soleil = new THREE.DirectionalLight(0xffffff, 2.2);
    soleil.position.set(0.12, 0.2, 0.16);
    monde.add(soleil);
    const bracelet = construireBracelet(THREE);
    monde.add(bracelet.racine);
    const camera = new THREE.PerspectiveCamera(32, 1, 0.01, 2);
    this.scene = { THREE, rendu, camera, monde, bracelet };
    this.cadrer();
    this.pret.set(true);
    this.animer();
  }

  /** Rotation lente tant que la scène est visible et que rien n'impose l'angle. */
  private animer(): void {
    cancelAnimationFrame(this.image);
    const pas = () => {
      if (this.detruit || !this.visible) {
        return;
      }
      if (this.tour() === null && !this.prise) {
        this.angle += 0.004;
      }
      this.dessiner();
      this.image = requestAnimationFrame(pas);
    };
    this.image = requestAnimationFrame(pas);
  }

  private cadrer(): void {
    if (!this.scene) {
      return;
    }
    const { clientWidth, clientHeight } = this.hote.nativeElement;
    if (clientWidth === 0 || clientHeight === 0) {
      return;
    }
    this.scene.rendu.setSize(clientWidth, clientHeight, false);
    this.scene.camera.aspect = clientWidth / clientHeight;
    this.scene.camera.updateProjectionMatrix();
    this.dessiner();
  }

  private dessiner(): void {
    const scene = this.scene;
    if (!scene) {
      return;
    }
    const explosion = this.explosion();
    eclater(scene.bracelet, explosion);
    colorer(scene.bracelet, this.coloris());
    const tour = this.tour();
    scene.bracelet.racine.rotation.y = tour === null ? this.angle : tour * 2 * Math.PI + this.angle;
    // La vue éclatée est plus haute : la caméra recule et vise plus haut pour tout garder dans le cadre.
    scene.camera.position.set(0, 0.07 + 0.03 * explosion, 0.2 + 0.07 * explosion);
    scene.camera.lookAt(0, -0.008 + 0.018 * explosion, 0);
    scene.rendu.render(scene.monde, scene.camera);
  }

  protected saisir(evenement: PointerEvent): void {
    this.prise = { x: evenement.clientX, angle: this.angle };
    (evenement.target as HTMLElement).setPointerCapture?.(evenement.pointerId);
  }

  protected glisser(evenement: PointerEvent): void {
    if (this.prise) {
      this.angle = this.prise.angle + (evenement.clientX - this.prise.x) * 0.01;
      this.dessiner();
    }
  }

  protected lacher(evenement: PointerEvent): void {
    this.prise = null;
    (evenement.target as HTMLElement).releasePointerCapture?.(evenement.pointerId);
  }

  protected touche(evenement: KeyboardEvent): void {
    if (evenement.key === 'ArrowLeft' || evenement.key === 'ArrowRight') {
      this.angle += evenement.key === 'ArrowLeft' ? -0.2 : 0.2;
      this.dessiner();
      evenement.preventDefault();
    }
  }
}
