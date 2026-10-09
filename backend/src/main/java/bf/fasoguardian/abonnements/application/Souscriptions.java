package bf.fasoguardian.abonnements.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import bf.fasoguardian.abonnements.Droits;
import bf.fasoguardian.abonnements.Droits.DroitsEnfant;
import bf.fasoguardian.abonnements.application.AgregateurPaiement.AgregateurIndisponible;
import bf.fasoguardian.abonnements.application.AgregateurPaiement.Demande;
import bf.fasoguardian.abonnements.domaine.Abonnement;
import bf.fasoguardian.abonnements.domaine.Facture;
import bf.fasoguardian.abonnements.domaine.Moyen;
import bf.fasoguardian.abonnements.domaine.Offre;
import bf.fasoguardian.abonnements.domaine.Paiement;
import bf.fasoguardian.abonnements.domaine.Paiement.Statut;
import bf.fasoguardian.abonnements.infrastructure.DepotAbonnements;
import bf.fasoguardian.abonnements.infrastructure.DepotFactures;
import bf.fasoguardian.abonnements.infrastructure.DepotOffres;
import bf.fasoguardian.abonnements.infrastructure.DepotPaiements;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.identite.Telephones;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abonnement d'un enfant, côté tuteur (US-PAR-015) : offres, paiement par mobile money, reçus. Demander un
 * paiement n'active rien : seule la confirmation signée de l'agrégateur le fait (voir {@link ReceptionPaiements}).
 */
@Service
public class Souscriptions {

    private static final String ROLE = "PARENT";
    private static final Pattern CLE_IDEMPOTENCE = Pattern.compile("[A-Za-z0-9_-]{16,80}");

    public record OffreVue(String code, String libelle, int prixFcfa, int intervalleS, int zonesMaximum, int historiqueJours) {
    }

    /** @param numero portefeuille à solliciter, en saisie usuelle */
    public record SaisiePaiement(String offre, Moyen moyen, String numero, boolean renouvellementAuto) {
    }

    /**
     * @param expireLe fin du délai laissé au parent pour valider sur son téléphone
     * @param recu     numéro du reçu, une fois le paiement confirmé
     */
    public record PaiementVue(UUID id, Statut statut, String offre, int montantFcfa, Moyen moyen, Instant expireLe,
            String motifEchec, String recu) {
    }

    /**
     * @param statut        {@code null} tant qu'aucun paiement n'a été confirmé pour cet enfant
     * @param restrictionLe jour où les fonctions avancées seront suspendues si l'échéance reste impayée
     * @param numeroMasque  portefeuille retenu pour le renouvellement, en partie masqué
     */
    public record AbonnementVue(String offre, String libelle, Integer prixFcfa, Abonnement.Statut statut,
            LocalDate prochaineEcheance, LocalDate restrictionLe, boolean renouvellementAuto, Moyen moyen,
            String numeroMasque, DroitsEnfant droits, PaiementVue paiementEnCours) {
    }

    public record RecuVue(String numero, String offre, int montantFcfa, Moyen moyen, String numeroMasque,
            LocalDate periodeDebut, LocalDate periodeFin, Instant emisLe) {
    }

    public record RecuPdf(String numero, byte[] contenu) {
    }

    private final DepotOffres offres;
    private final DepotAbonnements abonnements;
    private final DepotPaiements paiements;
    private final DepotFactures factures;
    private final AgregateurPaiement agregateur;
    private final RedacteurRecu redacteur;
    private final Droits droits;
    private final AccesEnfant acces;
    private final ServiceChiffrement chiffrement;
    private final JournalAudit journal;
    private final MeterRegistry metriques;
    private final Clock horloge;

    Souscriptions(DepotOffres offres, DepotAbonnements abonnements, DepotPaiements paiements, DepotFactures factures,
            AgregateurPaiement agregateur, RedacteurRecu redacteur, Droits droits, AccesEnfant acces,
            ServiceChiffrement chiffrement, JournalAudit journal, MeterRegistry metriques, Clock horloge) {
        this.offres = offres;
        this.abonnements = abonnements;
        this.paiements = paiements;
        this.factures = factures;
        this.agregateur = agregateur;
        this.redacteur = redacteur;
        this.droits = droits;
        this.acces = acces;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.metriques = metriques;
        this.horloge = horloge;
    }

    /** Offres qu'une famille peut souscrire elle-même, de la moins chère à la plus complète. */
    @Transactional(readOnly = true)
    public List<OffreVue> offres() {
        return offres.findAllByOrderByRang().stream().filter(Offre::souscriptible).map(o -> new OffreVue(o.code(),
                o.libelle(), o.prixFcfa(), o.intervalleS(), o.zonesMaximum(), o.historiqueJours())).toList();
    }

    @Transactional(readOnly = true)
    public AbonnementVue abonnement(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        DroitsEnfant ouverts = droits.de(enfantId);
        Abonnement abonnement = abonnements.findByEnfantId(enfantId).orElse(null);
        if (abonnement == null) {
            return new AbonnementVue(null, null, null, null, null, null, false, null, null, ouverts, null);
        }
        PaiementVue enCours = paiements.findFirstByAbonnementIdAndStatutOrderByInitieLeDesc(abonnement.id(), Statut.INITIE)
                .map(this::vue).orElse(null);
        if (abonnement.statut() == Abonnement.Statut.EN_ATTENTE) {
            return new AbonnementVue(null, null, null, null, null, null, abonnement.renouvellementAuto(), abonnement.moyen(),
                    abonnement.numeroMasque(), ouverts, enCours);
        }
        Offre offre = offres.findById(abonnement.offreCode()).orElseThrow();
        return new AbonnementVue(offre.code(), offre.libelle(), offre.prixFcfa(), abonnement.statut(),
                abonnement.prochaineEcheance(), abonnement.statut() == Abonnement.Statut.EN_RETARD ? abonnement.restrictionLe() : null,
                abonnement.renouvellementAuto(), abonnement.moyen(), abonnement.numeroMasque(), ouverts, enCours);
    }

    /**
     * Demande un paiement. La clé d'idempotence, choisie par le client, rend l'appel rejouable : la même clé
     * renvoie le paiement déjà demandé, sans solliciter le portefeuille une seconde fois.
     */
    @Transactional
    public PaiementVue payer(UUID tuteurId, UUID enfantId, SaisiePaiement saisie, String cleIdempotence) {
        acces.exigerTuteur(tuteurId, enfantId);
        if (cleIdempotence == null || !CLE_IDEMPOTENCE.matcher(cleIdempotence).matches()) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE,
                    "L'en-tête Idempotency-Key est obligatoire : 16 à 80 lettres, chiffres, tirets ou soulignés.");
        }
        Paiement rejoue = paiements.findByCleIdempotence(cleIdempotence).orElse(null);
        if (rejoue != null) {
            if (!abonnements.findById(rejoue.abonnementId()).orElseThrow().enfantId().equals(enfantId)) {
                throw new ErreurMetier(CodeErreur.CONFLIT, "Cette clé d'idempotence a déjà servi pour une autre demande.");
            }
            return vue(rejoue);
        }
        Offre offre = offres.findById(saisie.offre() == null ? "" : saisie.offre()).filter(Offre::souscriptible)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Cette offre ne peut pas être souscrite."));
        if (saisie.moyen() == null) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Choisissez Orange Money ou Moov Money.");
        }
        String numero;
        try {
            numero = Telephones.normaliserE164(saisie.numero());
        } catch (IllegalArgumentException erreur) {
            throw new ErreurMetier(CodeErreur.TELEPHONE_INVALIDE, "Ce numéro de portefeuille n'est pas un numéro mobile burkinabè.");
        }
        Instant maintenant = horloge.instant();
        Abonnement abonnement = abonnements.findByEnfantId(enfantId)
                .orElseGet(() -> abonnements.save(new Abonnement(tuteurId, enfantId, offre.code(), maintenant)));
        Paiement enAttente = paiements.findFirstByAbonnementIdAndStatutOrderByInitieLeDesc(abonnement.id(), Statut.INITIE).orElse(null);
        if (enAttente != null) {
            if (enAttente.initieLe().plus(Paiement.VALIDITE).isAfter(maintenant)) {
                throw new ErreurMetier(CodeErreur.PAIEMENT_EN_COURS,
                        "Un paiement est déjà en attente de validation pour cet enfant. Validez-le sur le téléphone ou patientez.");
            }
            // Délai de validation dépassé : la demande est close pour laisser réessayer. Si l'opérateur la
            // confirme malgré tout, elle sera honorée.
            enAttente.expirer(maintenant);
        }
        abonnement.retenirPaiement(tuteurId, saisie.moyen(), chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, numero),
                masquer(numero), saisie.renouvellementAuto(), maintenant);
        Paiement paiement = demander(abonnement, offre, numero, cleIdempotence);
        journal.consigner(tuteurId, ROLE, "PAIEMENT_DEMANDE", "ABONNEMENT", abonnement.id().toString(), Resultat.SUCCES);
        return vue(paiement);
    }

    /** Renouvellement automatique à l'échéance : même offre, même portefeuille (US-PAR-015). */
    void renouveler(Abonnement abonnement) {
        Offre offre = offres.findById(abonnement.offreCode()).orElseThrow();
        String cle = "renouvellement-" + abonnement.id() + "-" + abonnement.prochaineEcheance();
        if (paiements.findByCleIdempotence(cle).isEmpty()) {
            demander(abonnement, offre, chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, abonnement.numeroChiffre()), cle);
            journal.consigner(null, "SYSTEME", "RENOUVELLEMENT_DEMANDE", "ABONNEMENT", abonnement.id().toString(), Resultat.SUCCES);
        }
    }

    /** État d'un paiement, que l'écran d'attente interroge jusqu'à la réponse de l'opérateur. */
    @Transactional(readOnly = true)
    public PaiementVue paiement(UUID tuteurId, UUID paiementId) {
        Paiement paiement = paiements.findById(paiementId)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Paiement introuvable."));
        acces.exigerTuteur(tuteurId, abonnements.findById(paiement.abonnementId()).orElseThrow().enfantId());
        return vue(paiement);
    }

    @Transactional
    public AbonnementVue choisirRenouvellement(UUID tuteurId, UUID enfantId, boolean automatique) {
        acces.exigerTuteur(tuteurId, enfantId);
        Abonnement abonnement = abonnements.findByEnfantId(enfantId).filter(a -> a.moyen() != null).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun abonnement pour cet enfant."));
        abonnement.choisirRenouvellement(automatique, horloge.instant());
        journal.consigner(tuteurId, ROLE, automatique ? "RENOUVELLEMENT_AUTO_ACTIVE" : "RENOUVELLEMENT_AUTO_DESACTIVE",
                "ABONNEMENT", abonnement.id().toString(), Resultat.SUCCES);
        return abonnement(tuteurId, enfantId);
    }

    /** Reçus des paiements du tuteur, les plus récents d'abord. */
    @Transactional(readOnly = true)
    public List<RecuVue> recus(UUID tuteurId) {
        return factures.findByTuteurIdOrderByEmiseLeDesc(tuteurId).stream().map(f -> new RecuVue(f.numero(), f.offreLibelle(),
                f.montantFcfa(), f.moyen(), f.numeroMasque(), f.periodeDebut(), f.periodeFin(), f.emiseLe())).toList();
    }

    @Transactional(readOnly = true)
    public RecuPdf recuPdf(UUID tuteurId, String numero) {
        Facture facture = factures.findByNumeroAndTuteurId(numero, tuteurId)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Reçu introuvable."));
        return new RecuPdf(facture.numero(), redacteur.rediger(facture));
    }

    // -------------------------------------------------------------------- aides

    /**
     * Dépose la demande chez l'agrégateur. S'il ne répond pas, le paiement est enregistré en échec : le parent
     * le voit tout de suite et peut réessayer, plutôt que d'attendre une validation qui ne viendra pas.
     */
    private Paiement demander(Abonnement abonnement, Offre offre, String numeroE164, String cle) {
        Instant maintenant = horloge.instant();
        Paiement paiement = new Paiement(abonnement.id(), offre.code(), offre.prixFcfa(), abonnement.moyen(), cle, maintenant);
        try {
            paiement.noterReference(agregateur.initier(new Demande(paiement.id(), offre.prixFcfa(), abonnement.moyen(),
                    numeroE164, "FasoGuardian " + offre.libelle())));
            metriques.counter("fasoguardian.paiements", "issue", "DEMANDE").increment();
        } catch (AgregateurIndisponible erreur) {
            paiement.echouer("Le service de paiement ne répond pas. Réessayez dans quelques minutes.", maintenant);
            metriques.counter("fasoguardian.paiements", "issue", "INDISPONIBLE").increment();
        }
        return paiements.save(paiement);
    }

    private PaiementVue vue(Paiement paiement) {
        return new PaiementVue(paiement.id(), paiement.statut(), paiement.offreCode(), paiement.montantFcfa(), paiement.moyen(),
                paiement.initieLe().plus(Paiement.VALIDITE), paiement.motifEchec(),
                paiement.statut() == Statut.CONFIRME ? factures.findByPaiementId(paiement.id()).map(Facture::numero).orElse(null) : null);
    }

    /** « +22670123456 » devient « +226 70 •• •• 56 » : assez pour reconnaître son portefeuille, pas pour le reconstituer. */
    static String masquer(String numeroE164) {
        return numeroE164.substring(0, 4) + " " + numeroE164.substring(4, 6) + " •• •• " + numeroE164.substring(numeroE164.length() - 2);
    }
}
