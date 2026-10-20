import { ChangeDetectionStrategy, Component } from '@angular/core';

import { FgBracelet3d } from 'ui';

/** Points lumineux de la trame, en pourcentage de la scène : des positions qui battent doucement. */
const POINTS = [
  { x: 14, y: 22, delai: 0 },
  { x: 82, y: 18, delai: 700 },
  { x: 90, y: 64, delai: 1400 },
  { x: 9, y: 74, delai: 2100 },
  { x: 68, y: 86, delai: 2800 },
] as const;

/**
 * Décor de marque sur lequel se pose le bracelet en trois dimensions : dégradé « hero » des deux thèmes,
 * bouclier FasoGuardian en filigrane, ondes de localisation qui partent du centre, trame de carte et points de
 * position, lueur au sol. Purement visuel (aria-hidden) ; « réduire les animations » fige ondes et points.
 */
@Component({
  selector: 'app-decor-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="absolute inset-0 bg-(image:--fond-hero)"></div>
    <div class="scene-trame absolute inset-0"></div>
    <svg class="absolute top-1/2 left-1/2 h-[78%] -translate-x-1/2 -translate-y-1/2 text-hero-fg opacity-[.07]" viewBox="0 0 64 64" fill="none">
      <path d="M32 5 54 13v17c0 15-9.5 25-22 29C19.5 55 10 45 10 30V13Z" fill="currentColor" />
      <path d="M41 25a11 11 0 1 0 1.5 10H33" stroke="var(--color-bg)" stroke-width="5" stroke-linecap="round" />
    </svg>
    @for (onde of ondes; track onde) {
      <span class="scene-onde absolute top-1/2 left-1/2 size-56 rounded-full border border-accent/40" [style.animation-delay.ms]="onde * 1300"></span>
    }
    @for (point of points; track point.x) {
      <span class="absolute size-2" [style.left.%]="point.x" [style.top.%]="point.y">
        <span class="scene-point absolute inset-0 rounded-full bg-accent/60" [style.animation-delay.ms]="point.delai"></span>
        <span class="absolute inset-0 rounded-full bg-accent shadow-[0_0_12px_var(--color-accent)]"></span>
      </span>
    }
    <div class="absolute bottom-[12%] left-1/2 h-10 w-1/2 -translate-x-1/2 rounded-[100%] bg-accent/25 blur-2xl"></div>
  `,
  styles: `
    .scene-trame {
      background-image:
        linear-gradient(color-mix(in oklab, var(--color-hero-fg) 7%, transparent) 1px, transparent 1px),
        linear-gradient(90deg, color-mix(in oklab, var(--color-hero-fg) 7%, transparent) 1px, transparent 1px);
      background-size: 36px 36px;
      mask-image: radial-gradient(60% 55% at 50% 50%, #000 20%, transparent 80%);
    }

    @keyframes scene-onde {
      from {
        transform: translate(-50%, -50%) scale(0.35);
        opacity: 0.9;
      }
      to {
        transform: translate(-50%, -50%) scale(2.1);
        opacity: 0;
      }
    }

    .scene-onde {
      transform: translate(-50%, -50%);
      animation: scene-onde 3.9s var(--ease-enter) infinite;
    }

    @keyframes scene-point {
      to {
        transform: scale(3.2);
        opacity: 0;
      }
    }

    .scene-point {
      animation: scene-point 2.4s var(--ease-enter) infinite;
    }

    @media (prefers-reduced-motion: reduce) {
      .scene-onde,
      .scene-point {
        animation: none;
        opacity: 0.25;
      }
    }
  `,
  host: { class: 'pointer-events-none absolute inset-0 overflow-hidden', 'aria-hidden': 'true' },
})
export class DecorScene {
  protected readonly ondes = [0, 1, 2];
  protected readonly points = POINTS;
}

/** Scène du bracelet de l'accueil : le bracelet en trois dimensions posé sur le décor de marque. */
@Component({
  selector: 'app-scene-bracelet',
  imports: [FgBracelet3d, DecorScene],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-decor-scene />
    <div class="absolute inset-0">
      <fg-bracelet-3d class="size-full" [libelle]="libelle" />
    </div>
  `,
  host: { class: 'relative block overflow-hidden' },
})
export class SceneBracelet {
  protected readonly libelle =
    'Bracelet FasoGuardian en trois dimensions : boîtier bleu sans écran, QR code gravé, bouton SOS ambre sur la tranche. Tournez-le avec les flèches gauche et droite.';
}
