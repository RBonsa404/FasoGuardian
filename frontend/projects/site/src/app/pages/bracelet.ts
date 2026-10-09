import { isPlatformBrowser } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, PLATFORM_ID, afterNextRender, inject, signal, viewChild } from '@angular/core';

import { Bracelet3d } from '../bracelet-3d/bracelet-3d';
import { COLORIS, Coloris } from '../bracelet-3d/modele';
import { CARACTERISTIQUES } from '../contenu';

/** Part du défilement accomplie dans la section, entre 0 (elle entre dans la fenêtre) et 1 (elle en sort). */
export function avancement(haut: number, hauteur: number, fenetre: number): number {
  const course = hauteur - fenetre;
  return course <= 0 ? 0 : Math.min(1, Math.max(0, -haut / course));
}

/**
 * Le bracelet (écran 2) : en faisant défiler la page, le bracelet tourne sur lui-même et s'ouvre en vue
 * éclatée (capot, antennes, carte, batterie). Sans animation possible, les pièces sont nommées en texte.
 */
@Component({
  selector: 'app-page-bracelet',
  imports: [Bracelet3d],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section #defilement class="relative lg:h-[220vh]" aria-labelledby="titre-bracelet">
      <div class="mx-auto grid w-full max-w-6xl gap-8 px-5 py-12 lg:sticky lg:top-16 lg:h-[calc(100vh-4rem)] lg:grid-cols-2 lg:items-center lg:px-8 lg:py-0">
        <div class="flex flex-col gap-5">
          <span class="text-caption font-semibold tracking-widest text-accent uppercase">Le bracelet</span>
          <h1 id="titre-bracelet" class="m-0 text-h1 font-bold tracking-tight lg:text-display">Robuste, simple, sans écran.</h1>
          <p class="m-0 text-body-lg text-text-2">Faites défiler : le bracelet s'ouvre. Sous le capot gravé, deux antennes, une carte électronique et une batterie. Rien d'autre.</p>
          <ol class="m-0 flex list-none flex-col gap-2 p-0 text-label">
            @for (piece of pieces; track piece.nom; let i = $index) {
              <li class="flex items-baseline gap-3 rounded-md px-3 py-2" [class]="etape() === i ? 'bg-accent-soft text-text' : 'text-text-2'">
                <strong class="w-24 flex-none font-semibold">{{ piece.nom }}</strong><span>{{ piece.role }}</span>
              </li>
            }
          </ol>
          <div class="flex flex-col gap-2">
            <span class="text-label font-semibold">Coloris</span>
            <div class="flex flex-wrap gap-2" role="group" aria-label="Coloris du bracelet">
              @for (choix of coloris; track choix.nom) {
                <button type="button" class="flex h-11 items-center gap-2 rounded-full px-3.5 text-label font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent" [class]="couleur() === choix ? 'border-2 border-accent bg-accent-soft' : 'border border-line-strong text-text-2'" [attr.aria-pressed]="couleur() === choix" (click)="couleur.set(choix)">
                  <span class="size-4 rounded-full border border-line-strong" [style.background]="choix.css"></span>{{ choix.nom }}
                </button>
              }
            </div>
          </div>
        </div>
        <app-bracelet-3d class="h-96 rounded-xl border border-line bg-surface lg:h-[70vh]" [explosion]="explosion()" [tour]="tour()" [coloris]="couleur()" libelle="Vue éclatée du bracelet : de haut en bas, le capot gravé du QR code, les antennes 4G et GNSS, la carte électronique, la batterie et la coque, tenus par une sangle à fermoir de sécurité." />
      </div>
    </section>

    <section class="mx-auto flex w-full max-w-6xl flex-col gap-8 px-5 py-14 lg:px-8 lg:py-20" aria-labelledby="titre-fiche">
      <h2 id="titre-fiche" class="m-0 text-h2 font-bold tracking-tight lg:text-h1">Fiche technique</h2>
      <dl class="m-0 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        @for (ligne of caracteristiques; track ligne.cle) {
          <div class="flex flex-col gap-1 rounded-lg border border-line bg-surface p-5">
            <dd class="m-0 font-display text-h2 font-bold tabular-nums">{{ ligne.valeur }}</dd>
            <dt class="text-label text-text-2">{{ ligne.cle }}</dt>
          </div>
        }
      </dl>
    </section>
  `,
  host: { class: 'block' },
})
export class PageBracelet {
  protected readonly coloris = COLORIS;
  protected readonly caracteristiques = CARACTERISTIQUES;
  protected readonly pieces = [
    { nom: 'Capot', role: 'Gravé du QR code et du numéro du bracelet.' },
    { nom: 'Antennes', role: 'Réseau mobile 4G et 2G, positionnement par satellite.' },
    { nom: 'Carte', role: 'Microcontrôleur, élément sécurisé qui garde les clés, capteurs.' },
    { nom: 'Batterie', role: '600 mAh, recharge magnétique sans port ouvert.' },
  ] as const;

  protected readonly couleur = signal<Coloris>(COLORIS[0]);
  /** Vue éclatée : nulle au début du défilement, complète aux deux tiers. */
  protected readonly explosion = signal(0);
  /** Un tour complet sur la longueur de la section. */
  protected readonly tour = signal<number | null>(null);
  /** Pièce mise en avant dans la liste, selon l'ouverture. */
  protected readonly etape = signal(-1);

  private readonly defilement = viewChild.required<ElementRef<HTMLElement>>('defilement');

  constructor() {
    const navigateur = isPlatformBrowser(inject(PLATFORM_ID));
    const detruire = inject(DestroyRef);
    afterNextRender(() => {
      if (!navigateur) {
        return;
      }
      const suivre = () => {
        const cadre = this.defilement().nativeElement.getBoundingClientRect();
        const part = avancement(cadre.top - 64, cadre.height, window.innerHeight - 64);
        this.tour.set(part);
        const ouverture = Math.min(1, part / 0.66);
        this.explosion.set(ouverture);
        this.etape.set(ouverture < 0.15 ? -1 : Math.min(3, Math.floor(ouverture * 4)));
      };
      window.addEventListener('scroll', suivre, { passive: true });
      window.addEventListener('resize', suivre);
      suivre();
      detruire.onDestroy(() => {
        window.removeEventListener('scroll', suivre);
        window.removeEventListener('resize', suivre);
      });
    });
  }
}
