import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let suite = 0;

const LONGUEUR = 7;
/** Position du tiret dans la présentation « K7Q4–M2X ». */
const COUPURE = 4;

/**
 * Saisie du code d'appairage imprimé sur la carte d'activation : sept lettres ou chiffres présentés en
 * huit cases, tiret compris. Un seul champ réel est superposé aux cases, comme pour fg-otp.
 */
@Component({
  selector: 'fg-pairing-code',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FgCodeAppairage), multi: true }],
  template: `
    <label class="sr-only" [attr.for]="id">{{ libelle() }}</label>
    <div class="relative flex items-center justify-center gap-1.5 font-mono text-code font-semibold">
      @for (case of cases(); track $index) {
        <span [class]="case.classes" aria-hidden="true">{{ case.signe }}</span>
      }
      <input
        class="absolute inset-0 w-full cursor-text opacity-0"
        type="text"
        autocomplete="off"
        autocapitalize="characters"
        spellcheck="false"
        maxlength="9"
        [id]="id"
        [value]="valeur()"
        [disabled]="desactive()"
        [attr.aria-invalid]="erreur() ? 'true' : null"
        [attr.aria-describedby]="erreur() ? id + '-message' : null"
        (input)="saisir($event)"
        (focus)="focalise.set(true)"
        (blur)="focalise.set(false); toucher()"
      />
    </div>
    @if (erreur(); as message) {
      <span class="text-center text-caption font-medium text-danger" role="alert" [id]="id + '-message'">{{ message }}</span>
    }
  `,
  host: { class: 'flex flex-col gap-2.5' },
})
export class FgCodeAppairage implements ControlValueAccessor {
  readonly libelle = input.required<string>();
  readonly erreur = input<string | null>();
  /** Émis dès que les sept caractères sont saisis. */
  readonly complet = output<string>();

  protected readonly id = `fg-code-appairage-${++suite}`;
  protected readonly valeur = signal('');
  protected readonly desactive = signal(false);
  protected readonly focalise = signal(false);
  protected readonly cases = computed(() => {
    const valeur = this.valeur();
    const active = this.focalise() ? Math.min(valeur.length, LONGUEUR - 1) : -1;
    const cases = Array.from({ length: LONGUEUR }, (_, index) => ({
      signe: valeur[index] ?? '',
      classes:
        'grid h-12.5 w-8.5 place-items-center rounded-sm bg-surface ' +
        (this.erreur() ? 'border-2 border-danger' : index === active ? 'border-2 border-accent' : 'border border-line-strong'),
    }));
    cases.splice(COUPURE, 0, { signe: '–', classes: 'grid h-12.5 w-4 place-items-center text-text-3' });
    return cases;
  });

  private notifier: (valeur: string) => void = () => {};
  protected toucher: () => void = () => {};

  protected saisir(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const code = nettoyer(champ.value);
    champ.value = code;
    this.valeur.set(code);
    this.notifier(code);
    if (code.length === LONGUEUR) {
      this.complet.emit(code);
    }
  }

  writeValue(valeur: string | null): void {
    this.valeur.set(nettoyer(valeur ?? ''));
  }

  registerOnChange(fonction: (valeur: string) => void): void {
    this.notifier = fonction;
  }

  registerOnTouched(fonction: () => void): void {
    this.toucher = fonction;
  }

  setDisabledState(desactive: boolean): void {
    this.desactive.set(desactive);
  }
}

function nettoyer(saisie: string): string {
  return saisie
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, LONGUEUR);
}
