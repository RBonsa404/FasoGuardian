import { Directive, ElementRef, PLATFORM_ID, afterNextRender, inject, input } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Fait apparaître un bloc en douceur quand il entre à l'écran. Le site est prérendu : sans JavaScript, ou tant
 * que la page n'est pas hydratée, le contenu est visible tel quel ; la classe qui le masque n'est posée qu'au
 * navigateur, juste avant d'observer le bloc. « Réduire les animations » du système coupe l'effet (styles.css).
 */
@Directive({ selector: '[apparition]' })
export class Apparition {
  /** Décalage en millisecondes, pour faire entrer les éléments d'une liste l'un après l'autre. */
  readonly apparition = input<number | ''>('');

  constructor() {
    const element = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const navigateur = isPlatformBrowser(inject(PLATFORM_ID));
    afterNextRender(() => {
      if (!navigateur || typeof IntersectionObserver === 'undefined') {
        return;
      }
      const rect = element.getBoundingClientRect();
      // Déjà à l'écran à l'ouverture : rien à animer, la page ne doit pas « clignoter ».
      if (rect.top < innerHeight && rect.bottom > 0) {
        return;
      }
      const delai = this.apparition();
      if (delai !== '') {
        element.style.setProperty('--delai-apparition', `${delai}ms`);
      }
      element.classList.add('apparition');
      const observateur = new IntersectionObserver(
        (entrees) => {
          if (entrees.some((entree) => entree.isIntersecting)) {
            element.classList.add('apparu');
            observateur.disconnect();
          }
        },
        { rootMargin: '0px 0px -8% 0px' },
      );
      observateur.observe(element);
    });
  }
}
