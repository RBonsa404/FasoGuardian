package bf.fasoguardian.dispositifs.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.domaine.Appairage;
import bf.fasoguardian.dispositifs.domaine.Bracelet;
import bf.fasoguardian.dispositifs.domaine.Bracelet.TransitionIllegaleException;
import bf.fasoguardian.dispositifs.domaine.CodeAppairage;
import bf.fasoguardian.dispositifs.domaine.ConfigurationBracelet;
import bf.fasoguardian.dispositifs.domaine.MotifFin;
import bf.fasoguardian.dispositifs.domaine.StatutBracelet;
import bf.fasoguardian.dispositifs.infrastructure.DepotAppairages;
import bf.fasoguardian.dispositifs.infrastructure.DepotBracelets;
import bf.fasoguardian.dispositifs.infrastructure.DepotConfigurations;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.famille.ProfilsQr;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.identite.MessagesTuteurs;
import bf.fasoguardian.identite.SecondFacteur;
import bf.fasoguardian.identite.SecondFacteur.ActionSensible;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appairage, désappairage, perte, vol et configuration du bracelet d'un enfant (US-PAR-013, US-PAR-014).
 * Chaque action vérifie le lien de tutelle, est journalisée et notifiée aux tuteurs de l'enfant.
 */
@Service
public class Appairages {

    public static final int ESSAIS_PAR_QUART_HEURE = 5;
    /** Domaine de l'empreinte à clé des codes d'appairage. */
    static final String DOMAINE_CODE = "code-appairage";
    private static final String ROLE = "PARENT";

    public enum Motif {
        PERDU,
        VOLE,
        CASSE
    }

    public record BraceletVue(String numeroSerie, StatutBracelet statut, String versionLogiciel,
            String revisionMaterielle, LocalDate garantieJusquAu, boolean modeEconomie, int intervalleS,
            Instant appaireLe, Instant suiviJusquAu) {
    }

    private final DepotBracelets bracelets;
    private final DepotAppairages appairages;
    private final DepotConfigurations configurations;
    private final AccesEnfant acces;
    private final ProfilsQr profilsQr;
    private final SecondFacteur secondFacteur;
    private final LiensTutelle liens;
    private final MessagesTuteurs messages;
    private final ServiceChiffrement chiffrement;
    private final CommandesBracelet commandes;
    private final JournalAudit journal;
    private final Clock horloge;
    // Le code d'appairage est court : les essais infructueux d'un même parent sont comptés.
    private final Cache<UUID, Boolean> demandesRecentes =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();
    private final Cache<UUID, AtomicInteger> essaisRates =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(15)).maximumSize(100_000).build();

    Appairages(DepotBracelets bracelets, DepotAppairages appairages, DepotConfigurations configurations,
            AccesEnfant acces, ProfilsQr profilsQr, SecondFacteur secondFacteur, LiensTutelle liens,
            MessagesTuteurs messages, ServiceChiffrement chiffrement, CommandesBracelet commandes, JournalAudit journal,
            Clock horloge) {
        this.commandes = commandes;
        this.bracelets = bracelets;
        this.appairages = appairages;
        this.configurations = configurations;
        this.acces = acces;
        this.profilsQr = profilsQr;
        this.secondFacteur = secondFacteur;
        this.liens = liens;
        this.messages = messages;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.horloge = horloge;
    }

    /** Bracelet actuellement porté par l'enfant, s'il en a un. */
    @Transactional(readOnly = true)
    public Optional<BraceletVue> braceletDe(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return appairages.findByEnfantIdAndFinIsNull(enfantId).map(this::vue);
    }

    /**
     * Associe à l'enfant le bracelet dont le code d'appairage est saisi : le bracelet devient actif et son QR
     * gravé ouvre désormais la page publique de l'enfant. Un bracelet déclaré perdu est remplacé d'office.
     */
    @Transactional
    public BraceletVue associer(UUID tuteurId, UUID enfantId, String saisie) {
        acces.exigerTuteur(tuteurId, enfantId);
        AtomicInteger rates = essaisRates.get(tuteurId, cle -> new AtomicInteger());
        if (rates.get() >= ESSAIS_PAR_QUART_HEURE) {
            throw new ErreurMetier(CodeErreur.TROP_DE_REQUETES, "Trop d'essais. Réessayez dans quinze minutes.");
        }
        Bracelet bracelet = CodeAppairage.normaliser(saisie)
                .flatMap(code -> bracelets.findByCodeAppairageEmpreinte(chiffrement.empreinteLibre(DOMAINE_CODE, code)))
                .filter(candidat -> candidat.statut() == StatutBracelet.EN_STOCK && !candidat.certificatRevoque())
                .orElse(null);
        if (bracelet == null) {
            rates.incrementAndGet();
            journal.consigner(tuteurId, ROLE, "APPAIRAGE_REFUSE", "ENFANT", enfantId.toString(), Resultat.REFUS);
            throw new ErreurMetier(CodeErreur.CODE_APPAIRAGE_INVALIDE,
                    "Ce code n'est pas reconnu. Vérifiez la carte d'activation.");
        }
        Instant maintenant = horloge.instant();
        appairages.findByEnfantIdAndFinIsNull(enfantId).ifPresent(ancien -> remplacer(ancien, maintenant));
        bracelet.activer(maintenant);
        Appairage appairage = appairages.save(new Appairage(bracelet.id(), enfantId, maintenant));
        profilsQr.associer(enfantId, bracelet.jetonQrSha256(), bracelet.numeroSerie());
        essaisRates.invalidate(tuteurId);
        journal.consigner(tuteurId, ROLE, "BRACELET_APPAIRE", "BRACELET", bracelet.id().toString(), Resultat.SUCCES);
        prevenir(enfantId, "FasoGuardian : le bracelet " + bracelet.numeroSerie() + " est associé à votre enfant.");
        return vue(appairage);
    }

    /**
     * Déclaration de perte, de vol ou de casse, confirmée par code SMS. Perte : page publique désactivée,
     * suivi maintenu 72 h. Vol : page désactivée et certificat révoqué aussitôt. Casse : retour au SAV, la
     * page publique reste utile tant que l'enfant porte le bracelet (ADR 0009).
     */
    @Transactional
    public BraceletVue declarer(UUID tuteurId, UUID enfantId, Motif motif, String codeSecondFacteur) {
        acces.exigerTuteur(tuteurId, enfantId);
        Appairage appairage = appairageActif(enfantId);
        Bracelet bracelet = bracelets.findById(appairage.braceletId()).orElseThrow();
        secondFacteur.exiger(tuteurId, ActionSensible.DECLARER_BRACELET, codeSecondFacteur);
        Instant maintenant = horloge.instant();
        try {
            switch (motif) {
                case PERDU -> {
                    bracelet.declarerPerdu(maintenant);
                    profilsQr.suspendre(enfantId);
                }
                case VOLE -> {
                    bracelet.declarerVole(maintenant);
                    appairage.clore(MotifFin.VOL, maintenant);
                    profilsQr.suspendre(enfantId);
                }
                case CASSE -> {
                    bracelet.retournerAuSav(maintenant);
                    appairage.clore(MotifFin.PANNE, maintenant);
                }
            }
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        }
        journal.consigner(tuteurId, ROLE, "BRACELET_DECLARE_" + motif, "BRACELET", bracelet.id().toString(),
                Resultat.SUCCES);
        prevenir(enfantId, "FasoGuardian : le bracelet " + bracelet.numeroSerie() + " a été déclaré "
                + switch (motif) {
                    case PERDU -> "perdu. Sa page QR est désactivée ; le suivi continue 72 h.";
                    case VOLE -> "volé. Sa page QR est désactivée et il ne peut plus se connecter.";
                    case CASSE -> "cassé. Rapportez-le en point relais pour le faire remplacer.";
                });
        return vue(bracelet, appairage);
    }

    /** Le bracelet perdu a été retrouvé pendant le suivi : il redevient actif et sa page publique aussi. */
    @Transactional
    public BraceletVue retrouver(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        Appairage appairage = appairageActif(enfantId);
        Bracelet bracelet = bracelets.findById(appairage.braceletId()).orElseThrow();
        try {
            bracelet.retrouver(horloge.instant());
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        }
        profilsQr.reactiver(enfantId);
        journal.consigner(tuteurId, ROLE, "BRACELET_RETROUVE", "BRACELET", bracelet.id().toString(), Resultat.SUCCES);
        prevenir(enfantId, "FasoGuardian : le bracelet " + bracelet.numeroSerie() + " est de nouveau actif.");
        return vue(bracelet, appairage);
    }

    /** Rompt l'association sans déclaration : le bracelet n'est plus actif pour l'enfant (US-PAR-014). */
    @Transactional
    public void desappairer(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        Appairage appairage = appairageActif(enfantId);
        Bracelet bracelet = bracelets.findById(appairage.braceletId()).orElseThrow();
        Instant maintenant = horloge.instant();
        if (bracelet.statut() == StatutBracelet.PERDU) {
            bracelet.cloreSuivi(maintenant);
            profilsQr.suspendre(enfantId);
        } else {
            bracelet.retournerAuSav(maintenant);
            profilsQr.dissocier(bracelet.jetonQrSha256());
        }
        appairage.clore(MotifFin.DESAPPAIRAGE, maintenant);
        journal.consigner(tuteurId, ROLE, "BRACELET_DESAPPAIRE", "BRACELET", bracelet.id().toString(), Resultat.SUCCES);
        prevenir(enfantId, "FasoGuardian : le bracelet " + bracelet.numeroSerie()
                + " n'est plus associé à votre enfant.");
    }

    /** Toute modification de configuration est journalisée et notifiée (US-PAR-013). */
    @Transactional
    public BraceletVue reglerModeEconomie(UUID tuteurId, UUID enfantId, boolean actif) {
        acces.exigerTuteur(tuteurId, enfantId);
        Appairage appairage = appairageActif(enfantId);
        Bracelet bracelet = bracelets.findById(appairage.braceletId()).orElseThrow();
        ConfigurationBracelet configuration = configurations.findById(bracelet.id()).orElseThrow();
        if (configuration.reglerModeEconomie(actif)) {
            commandes.configurer(bracelet, configuration, tuteurId);
            journal.consigner(tuteurId, ROLE, actif ? "MODE_ECONOMIE_ACTIVE" : "MODE_ECONOMIE_DESACTIVE", "BRACELET",
                    bracelet.id().toString(), Resultat.SUCCES);
            prevenir(enfantId, "FasoGuardian : le mode économie du bracelet " + bracelet.numeroSerie() + " est "
                    + (actif ? "activé" : "désactivé") + ".");
        }
        return vue(bracelet, appairage);
    }

    /**
     * « Localiser maintenant » : demande au bracelet une position immédiate, au plus une fois par minute et par
     * enfant. La position arrive ensuite par le flux habituel.
     */
    @Transactional
    public void localiserMaintenant(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        if (demandesRecentes.asMap().putIfAbsent(enfantId, Boolean.TRUE) != null) {
            throw new ErreurMetier(CodeErreur.TROP_DE_REQUETES, "Une localisation vient d'être demandée. Patientez une minute.");
        }
        if (!commandes.localiser(tuteurId, enfantId)) {
            demandesRecentes.invalidate(enfantId);
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun bracelet en service n'est associé à cet enfant.");
        }
    }

    /** Fin du suivi de 72 h des bracelets perdus : appairage clos, certificat révoqué. */
    @Scheduled(fixedDelayString = "${fasoguardian.dispositifs.cloture-suivis:PT10M}")
    @SchedulerLock(name = "dispositifs-cloture-suivis")
    @Transactional
    public void cloreSuivisEchus() {
        Instant maintenant = horloge.instant();
        for (Bracelet bracelet : bracelets.findByStatutAndSuiviJusquAuBefore(StatutBracelet.PERDU, maintenant)) {
            bracelet.cloreSuivi(maintenant);
            appairages.findByBraceletIdAndFinIsNull(bracelet.id())
                    .ifPresent(appairage -> appairage.clore(MotifFin.PERTE, maintenant));
            journal.consigner(null, "SYSTEME", "SUIVI_BRACELET_CLOS", "BRACELET", bracelet.id().toString(),
                    Resultat.SUCCES);
        }
    }

    // -------------------------------------------------------------------- aides

    /** L'enfant a déjà un appairage actif : seul un bracelet déclaré perdu peut être remplacé sans autre démarche. */
    private void remplacer(Appairage ancien, Instant maintenant) {
        Bracelet precedent = bracelets.findById(ancien.braceletId()).orElseThrow();
        if (precedent.statut() != StatutBracelet.PERDU) {
            throw new ErreurMetier(CodeErreur.ENFANT_DEJA_EQUIPE,
                    "Cet enfant porte déjà un bracelet. Désappairez-le ou déclarez-le avant d'en associer un autre.");
        }
        precedent.cloreSuivi(maintenant);
        ancien.clore(MotifFin.REMPLACEMENT, maintenant);
        // L'unicité de l'appairage actif est contrôlée en base : la clôture doit y précéder le nouvel appairage.
        appairages.flush();
    }

    private Appairage appairageActif(UUID enfantId) {
        return appairages.findByEnfantIdAndFinIsNull(enfantId).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun bracelet n'est associé à cet enfant."));
    }

    private void prevenir(UUID enfantId, String texte) {
        liens.tuteursActifsDe(enfantId).forEach(tuteur -> messages.envoyerSms(tuteur, texte));
    }

    private BraceletVue vue(Appairage appairage) {
        return vue(bracelets.findById(appairage.braceletId()).orElseThrow(), appairage);
    }

    private BraceletVue vue(Bracelet bracelet, Appairage appairage) {
        ConfigurationBracelet configuration = configurations.findById(bracelet.id()).orElseThrow();
        return new BraceletVue(bracelet.numeroSerie(), bracelet.statut(), bracelet.versionLogiciel(),
                bracelet.revisionMaterielle(), bracelet.garantieJusquAu(), configuration.modeEconomie(),
                configuration.intervalleCourantS(), appairage.debut(), bracelet.suiviJusquAu());
    }

    private static ErreurMetier conflit() {
        return new ErreurMetier(CodeErreur.CONFLIT, "Cette action n'est pas possible dans l'état actuel du bracelet.");
    }
}
