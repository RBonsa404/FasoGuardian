import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { GenreActionRejouable } from 'api';
import { FgBanniere } from 'ui';

import { FileHorsLigne } from '../commun/file-hors-ligne';

const GENRES: Record<GenreActionRejouable, string> = {
  FICHE_ENFANT: $localize`:@@attente.fiche:fiche de l'enfant`,
  FICHE_SANTE: $localize`:@@attente.sante:fiche médicale`,
  CONTACT_URGENCE: $localize`:@@attente.contact:contact d'urgence`,
  PRISE_EN_CHARGE: $localize`:@@attente.priseEnCharge:prise en charge d'alerte`,
  LEVEE_ALERTE: $localize`:@@attente.levee:levée d'alerte`,
  FAUSSE_ALERTE: $localize`:@@attente.fausse:fausse alerte`,
  ZONE_REACTIVEE: $localize`:@@attente.zone:réactivation de Safe Zone`,
  MODE_ECONOMIE: $localize`:@@attente.economie:mode économie`,
  RENOUVELLEMENT: $localize`:@@attente.renouvellement:renouvellement automatique`,
  NOTIFICATIONS_LUES: $localize`:@@attente.lues:notifications lues`,
  MESSAGE_SUPPORT: $localize`:@@attente.support:message au support`,
};

/**
 * États transverses de l'application (écran 49) : ils s'affichent en tête de chaque écran, au-dessus de son
 * contenu. Les actions gardées hors ligne disent ce qui partira au retour du réseau (FG-DOC-06 §5.4).
 */
@Component({
  selector: 'app-etats-transverses',
  imports: [FgBanniere],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (attente(); as texte) {
      <fg-banner icone="historique">{{ texte }}</fg-banner>
    }
    @if (file.refusees() > 0) {
      <fg-banner ton="erreur" i18n="@@attente.refusees">
        {file.refusees(), plural, =1 {Une action gardée hors ligne a été refusée au retour du réseau : vérifiez l'écran concerné.} other {{{ file.refusees() }} actions gardées hors ligne ont été refusées au retour du réseau : vérifiez les écrans concernés.}}
      </fg-banner>
    }
  `,
  host: { class: 'empty:hidden mx-auto flex w-full max-w-md flex-col gap-2 px-5 pt-4 lg:mx-0 lg:max-w-2xl lg:px-10' },
})
export class EtatsTransverses {
  protected readonly file = inject(FileHorsLigne);

  /** « 2 actions seront envoyées dès le retour du réseau : fiche médicale, levée d'alerte. » */
  protected readonly attente = computed(() => {
    const actions = this.file.enAttente();
    if (actions.length === 0) {
      return null;
    }
    const genres = [...new Set(actions.map((action) => GENRES[action.genre]))].join(', ');
    return actions.length === 1
      ? $localize`:@@attente.une:1 action sera envoyée dès le retour du réseau : ${genres}:genres:.`
      : $localize`:@@attente.plusieurs:${actions.length}:nombre: actions seront envoyées dès le retour du réseau : ${genres}:genres:.`;
  });
}
