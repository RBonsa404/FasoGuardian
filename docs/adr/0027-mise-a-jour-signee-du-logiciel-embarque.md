# ADR 0027 — Mise à jour du logiciel embarqué : image signée, déploiement par vagues

Statut : acceptée — 9 octobre 2026

## Contexte

US-PAR-013 (MUST) : « étant donné une mise à jour du logiciel embarqué, quand elle est déployée, alors seule
une image signée est acceptée et le parent est informé ». FG-DOC-08 prévoit la mise à jour à distance et le
démarrage sécurisé ; l'écran 68 de la console montre une campagne en quatre vagues, à déclenchement manuel.

## Décision

1. **Deux signatures, deux clés.** La commande qui demande l'installation est signée par la plateforme, comme
   toute commande (ADR 0012). L'image, elle, est signée par la **clé de publication du logiciel**, dont la
   partie privée ne quitte jamais le poste de publication : la plateforme n'en connaît que la partie publique
   (`FG_OTA_CLE_PUBLIQUE`). Une plateforme compromise ne peut donc pas faire installer une image de son cru.
2. **Un manifeste lie tout.** La signature porte sur `FG-OTA|version|taille|empreinte SHA-256`. La plateforme
   la vérifie à l'enregistrement de l'image : sans signature valide, pas de campagne, et le refus est au
   journal d'audit. Le bracelet la vérifie de nouveau avant d'installer, puis contrôle la taille et
   l'empreinte de ce qu'il a téléchargé. Sans clé de publication configurée, aucune image n'est acceptée.
3. **Quatre vagues lancées à la main** : 1 % (au moins un bracelet), 10 %, 25 %, puis tout le parc porté.
   Chaque lancement est journalisé. La campagne se met en pause et se reprend ; une seule campagne déploie
   à la fois, pour que deux versions ne se disputent pas les mêmes bracelets.
4. **Le parent est informé avant l'installation**, par une notification qui nomme la version et le bracelet.
   Elle se relit dans l'application (rubrique « Informations » du centre d'alertes), même si le push n'est
   pas arrivé : le serveur expose désormais les trente dernières notifications de chacun, hors alertes.
5. **L'avancement se lit sur les faits.** Un bracelet compte comme à jour quand il annonce lui-même la
   nouvelle version à sa reconnexion (`status`). Tant qu'il ne l'a pas fait, la demande lui est renvoyée
   toutes les heures : un bracelet éteint ou hors réseau n'est pas oublié.

## Ce qui reste hors de la plateforme

- **Le micrologiciel.** La vérification du manifeste, le téléchargement, le contrôle de l'empreinte, l'écriture
  dans la seconde banque et le retour arrière en cas d'échec sont à écrire dans le logiciel embarqué. Le
  simulateur en reproduit la décision (image acceptée ou refusée) et sert de référence.
- **L'hébergement des images.** La campagne porte une adresse https ; le serveur qui les sert, et son débit
  face à un parc entier, sont à choisir avec l'hébergeur.

## Points à trancher

- Où est gardée la clé privée de publication, et qui peut signer une image (procédure à deux personnes
  recommandée).
- Faut-il exiger qu'une vague soit terminée, ou un taux d'échec maximal, avant d'autoriser la suivante ?
  Aujourd'hui l'agent en juge sur l'avancement affiché.
