import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CONSERVATION } from '../contenu';

/** Version et date des textes légaux : à changer à chaque révision, elles s'affichent en tête de page. */
const VERSION = { numero: '0.9 (projet)', date: '9 octobre 2026' } as const;

/**
 * Gabarit de lecture longue (écran 8) : colonne étroite, sommaire collant sur grand écran, version et date
 * de mise à jour en tête.
 */
@Component({
  selector: 'app-texte-legal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto grid w-full max-w-6xl gap-8 px-5 py-12 lg:grid-cols-[16rem_minmax(0,1fr)] lg:px-8 lg:py-16">
      <nav class="hidden flex-col gap-1 self-start lg:sticky lg:top-24 lg:flex" aria-label="Sommaire">
        @for (section of sommaire(); track section.ancre) {
          <a class="py-1.5 text-label text-text-2 hover:text-text focus-visible:outline-2 focus-visible:outline-accent" [href]="chemin() + '#' + section.ancre">{{ section.titre }}</a>
        }
      </nav>
      <article class="flex max-w-[68ch] flex-col gap-4 text-body-lg leading-relaxed text-text-2 [&_h2]:m-0 [&_h2]:scroll-mt-24 [&_h2]:pt-4 [&_h2]:font-display [&_h2]:text-h3 [&_h2]:font-semibold [&_h2]:text-text [&_p]:m-0 [&_ul]:m-0 [&_ul]:pl-5">
        <h1 class="m-0 text-h1 font-bold tracking-tight text-text">{{ titre() }}</h1>
        <p class="text-label text-text-3">Version {{ version.numero }} · mise à jour le {{ version.date }}. Texte en cours de validation juridique : il décrit le fonctionnement réel du service, sa rédaction définitive sera publiée avant l'ouverture au public.</p>
        <ng-content />
      </article>
    </div>
  `,
  host: { class: 'block' },
})
export class TexteLegal {
  readonly titre = input.required<string>();
  readonly chemin = input.required<string>();
  readonly sommaire = input.required<readonly { ancre: string; titre: string }[]>();
  protected readonly version = VERSION;
}

@Component({
  selector: 'app-mentions',
  imports: [TexteLegal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-texte-legal titre="Mentions légales" chemin="/mentions" [sommaire]="sommaire">
      <h2 id="editeur">Éditeur</h2>
      <p>Le site et le service FasoGuardian sont édités par le Collectif Dedsec, Ouagadougou, Burkina Faso. L'adresse du siège, les numéros d'immatriculation et le nom du directeur de la publication seront portés ici avant l'ouverture du service au public.</p>
      <h2 id="hebergement">Hébergement</h2>
      <p>Les données du service sont hébergées au Burkina Faso ou chez un hébergeur dont la localisation et les garanties sont acceptées par la Commission de l'informatique et des libertés (CIL). Le nom et l'adresse de l'hébergeur seront portés ici une fois le choix arrêté.</p>
      <h2 id="propriete">Propriété intellectuelle</h2>
      <p>La marque, le logo, les textes et le modèle du bracelet présentés sur ce site appartiennent au Collectif Dedsec. Toute reproduction demande son accord écrit.</p>
      <h2 id="responsabilite">Portée du service</h2>
      <p>FasoGuardian est une aide à la localisation et à l'alerte. Il ne remplace ni la vigilance des adultes ni les services de secours, et son fonctionnement dépend de la couverture du réseau mobile et de la charge du bracelet.</p>
    </app-texte-legal>
  `,
  host: { class: 'block' },
})
export class Mentions {
  protected readonly sommaire = [
    { ancre: 'editeur', titre: 'Éditeur' },
    { ancre: 'hebergement', titre: 'Hébergement' },
    { ancre: 'propriete', titre: 'Propriété intellectuelle' },
    { ancre: 'responsabilite', titre: 'Portée du service' },
  ] as const;
}

@Component({
  selector: 'app-confidentialite',
  imports: [TexteLegal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-texte-legal titre="Confidentialité" chemin="/confidentialite" [sommaire]="sommaire">
      <h2 id="responsable">Responsable de traitement</h2>
      <p>Le responsable de traitement est le Collectif Dedsec. Le service est soumis à la loi n° 001-2021/AN portant protection des personnes à l'égard du traitement des données à caractère personnel, et déclaré auprès de la CIL.</p>
      <h2 id="donnees">Données traitées</h2>
      <ul>
        <li>Le compte du parent : numéro de téléphone, mot de passe (jamais conservé en clair), pièces justifiant le lien avec l'enfant.</li>
        <li>La fiche de l'enfant : prénom, nom, date de naissance, éléments de reconnaissance, et la fiche médicale que le parent choisit de remplir.</li>
        <li>Les positions du bracelet, ses alertes et son état (batterie, réseau).</li>
        <li>Les paiements : montant, date, moyen de paiement ; aucun numéro de carte n'est traité.</li>
      </ul>
      <p>Le service ne traite aucune donnée biométrique. Le bracelet n'a ni caméra ni micro.</p>
      <h2 id="finalites">À quoi elles servent</h2>
      <p>Uniquement à identifier, localiser et alerter : montrer la position de l'enfant à ses parents, les prévenir d'un SOS, d'un retrait du bracelet ou d'une sortie de zone, et permettre à la personne qui trouve l'enfant de joindre sa famille. Elles ne sont ni vendues, ni utilisées pour de la publicité.</p>
      <h2 id="acces">Qui y accède</h2>
      <p>Les parents dont le lien avec l'enfant a été vérifié. Aucun agent de FasoGuardian ne voit la position ni la fiche médicale d'un enfant ; l'équipe de vérification ne voit que les pièces des dossiers qu'elle instruit. Chaque accès interne est inscrit dans un journal scellé. Les forces de sécurité ne reçoivent un dossier que si le parent signale une disparition et le confirme par un code SMS.</p>
      <h2 id="page-qr">La page ouverte par le QR code</h2>
      <p>Elle n'affiche ni le nom, ni la photo, ni la position de l'enfant : seulement le numéro du bracelet, les informations médicales que le parent a marquées critiques et le moyen d'appeler les contacts qu'il a choisis. En appuyant sur « Appeler », la personne compose le numéro de ce contact.</p>
      <h2 id="durees">Durées de conservation</h2>
      <ul>
        @for (ligne of conservation; track ligne.donnee) {
          <li>{{ ligne.donnee }} : {{ ligne.duree }}.</li>
        }
      </ul>
      <p>Passé ces durées, les données sont effacées par des purges automatiques, dont l'exécution est contrôlée chaque mois.</p>
      <h2 id="droits">Vos droits</h2>
      <p>Depuis l'application, vous pouvez à tout moment télécharger l'ensemble de vos données et clore votre compte, ce qui efface vos données et celles de votre enfant, hors ce que la loi impose de conserver. Vous pouvez aussi saisir la CIL.</p>
      <h2 id="site">Ce site</h2>
      <p>Ce site vitrine ne dépose aucun traceur publicitaire, ne comporte aucun formulaire et ne recueille aucune donnée personnelle. Votre choix d'apparence (sombre ou clair) est gardé sur votre appareil.</p>
    </app-texte-legal>
  `,
  host: { class: 'block' },
})
export class Confidentialite {
  protected readonly conservation = CONSERVATION;
  protected readonly sommaire = [
    { ancre: 'responsable', titre: 'Responsable de traitement' },
    { ancre: 'donnees', titre: 'Données traitées' },
    { ancre: 'finalites', titre: 'À quoi elles servent' },
    { ancre: 'acces', titre: 'Qui y accède' },
    { ancre: 'page-qr', titre: 'La page du QR code' },
    { ancre: 'durees', titre: 'Durées de conservation' },
    { ancre: 'droits', titre: 'Vos droits' },
    { ancre: 'site', titre: 'Ce site' },
  ] as const;
}

@Component({
  selector: 'app-cgu',
  imports: [TexteLegal, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-texte-legal titre="Conditions générales" chemin="/cgu" [sommaire]="sommaire">
      <h2 id="objet">Objet</h2>
      <p>Les présentes conditions encadrent l'usage du bracelet FasoGuardian, de l'application des parents et de la page publique ouverte par le QR code.</p>
      <h2 id="compte">Compte et vérification</h2>
      <p>Le compte est réservé aux parents et tuteurs légaux. Il n'est activé qu'après vérification du lien avec l'enfant, sur pièces. Un second tuteur passe sa propre vérification.</p>
      <h2 id="abonnement">Bracelet et abonnement</h2>
      <p>Le bracelet s'achète une fois ; le service fonctionne ensuite par abonnement mensuel, par enfant, sans engagement. En cas d'impayé, des rappels sont envoyés ; au bout de quinze jours, le suivi continu de la position est suspendu. Le SOS, la détection de retrait et la page QR restent actifs quel que soit l'état de l'abonnement.</p>
      <h2 id="limites">Limites du service</h2>
      <p>La position dépend du réseau mobile, du signal satellite et de la charge du bracelet : elle peut être approximative, ancienne ou absente, et l'application l'indique toujours. Le service est une aide ; il ne garantit pas la sécurité de l'enfant et ne remplace pas les secours.</p>
      <h2 id="usages">Usages interdits</h2>
      <p>Le bracelet est destiné à un enfant dont l'utilisateur est le parent ou le tuteur vérifié. Il est interdit de s'en servir pour suivre une autre personne. Un faux signalement de disparition engage la responsabilité de son auteur.</p>
      <h2 id="donnees">Données personnelles</h2>
      <p>Leur traitement est décrit dans la <a class="font-semibold text-accent underline" routerLink="/confidentialite">politique de confidentialité</a>.</p>
    </app-texte-legal>
  `,
  host: { class: 'block' },
})
export class Cgu {
  protected readonly sommaire = [
    { ancre: 'objet', titre: 'Objet' },
    { ancre: 'compte', titre: 'Compte et vérification' },
    { ancre: 'abonnement', titre: 'Bracelet et abonnement' },
    { ancre: 'limites', titre: 'Limites du service' },
    { ancre: 'usages', titre: 'Usages interdits' },
    { ancre: 'donnees', titre: 'Données personnelles' },
  ] as const;
}
