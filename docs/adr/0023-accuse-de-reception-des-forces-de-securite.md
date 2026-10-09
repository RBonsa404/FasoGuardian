# ADR 0023 — Accusé de réception d'un signalement par les forces de sécurité

Statut : acceptée — 9 octobre 2026

## Contexte

US-FDS-001 (COULD) : « quand l'agent confirme la prise en charge, alors l'accusé est horodaté et notifié au
parent ». L'écran 75 de la console montre, dans l'espace des forces de sécurité, la référence du signalement,
l'identité et la photo de l'enfant, ses informations médicales, le numéro du parent, le trajet et le bouton
d'accusé. Or aucune convention n'est signée avec la Police ou la Gendarmerie : le dossier est aujourd'hui
remis par le parent (ADR 0013), et FG-DOC-06 impose la minimisation.

## Décision

1. **Un rôle FDS, un seul geste d'écriture.** L'agent retrouve un signalement par sa **référence exacte**,
   celle imprimée sur le dossier. Il n'existe ni liste ni recherche approchée : sans le dossier en main, on
   ne trouve rien. Il peut en **accuser réception** ; le premier accusé fait foi, les suivants ne changent
   rien et ne renotifient personne.
2. **L'accusé est horodaté, attribué et notifié.** L'heure et l'agent sont enregistrés, l'action est au
   journal d'audit, et chaque tuteur de l'enfant reçoit une notification qui ne cite que la référence. Le
   parent voit l'accusé sur son dossier de signalement.
3. **Le constat ne porte aucune donnée de l'enfant.** Référence, nature de l'alerte, heure d'établissement,
   état de l'accusé : rien d'autre. Le **dossier** (identité, photo, santé critique, trajet) n'est ouvert
   depuis cet espace, en PDF, que s'il a été **transmis par la passerelle convenue** ; remis par le parent,
   l'agent l'a déjà entre les mains et la plateforme n'a pas à le resservir.
4. **Trente jours.** Passé ce délai, la référence n'ouvre plus rien dans l'espace des forces de sécurité, et
   le dossier est effacé (ADR 0013). Chaque consultation, chaque refus et chaque téléchargement sont journalisés.

## Écart avec la maquette

L'écran 75 affiche le contenu du dossier dans la page. Il est ici dans le PDF, ouvert d'un bouton, et
seulement quand une convention l'autorise : le dossier est une pièce figée, scellée par son empreinte au
moment de l'escalade, et le recomposer à l'écran à partir des données courantes montrerait autre chose que
ce que le parent a approuvé. Le reste de l'écran suit la maquette (référence, « Reçu », accusé au nom de
l'agent, mention de traçabilité).

## Points à trancher

- **Convention.** Qui sont les agents habilités, comment leurs comptes sont créés et retirés, et si le
  dossier doit s'afficher dans la page : cela relève de la convention avec la Police et la Gendarmerie.
- **Comptes.** En attendant, un compte FDS est créé par l'administrateur comme tout agent interne, avec
  second facteur obligatoire.
