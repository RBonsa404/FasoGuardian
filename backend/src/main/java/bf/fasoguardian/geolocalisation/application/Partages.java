package bf.fasoguardian.geolocalisation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.audit.RegistrePurges;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.famille.ContactsUrgence;
import bf.fasoguardian.famille.ContactsUrgence.Contact;
import bf.fasoguardian.geolocalisation.domaine.PartagePosition;
import bf.fasoguardian.geolocalisation.infrastructure.DepotPartages;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import bf.fasoguardian.telemetrie.TrajetsRecents;
import bf.fasoguardian.telemetrie.TrajetsRecents.Point;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partage temporaire de la position (US-SEC-001). Le parent choisit un contact d'urgence de l'enfant et une
 * durée ; le contact reçoit par SMS un lien personnel qui montre la dernière position, sans compte, jusqu'à
 * l'échéance ou la révocation. Le lien ne montre ni le nom de l'enfant ni son historique.
 */
@Service
public class Partages {

    private static final String ROLE = "PARENT";
    /** Les positions plus anciennes ne sont pas montrées au contact : il doit voir où est l'enfant, pas où il était. */
    private static final Duration FRAICHEUR = Duration.ofHours(2);
    private static final Duration CONSERVATION = Duration.ofDays(30);

    /** @param destinataire numéro masqué du contact */
    public record PartageVue(UUID id, String lien, String destinataire, Instant debut, Instant fin, int ouvertures,
            Instant derniereOuverture) {
    }

    /** @param position dernière position récente, ou {@code null} si le bracelet n'en a pas donné depuis deux heures */
    public record VuePartagee(String partagePar, Instant fin, PositionPartagee position) {
    }

    public record PositionPartagee(double latitude, double longitude, int precisionM, boolean approximative, Instant mesureeLe) {
    }

    private final DepotPartages depot;
    private final AccesEnfant acces;
    private final ContactsUrgence contacts;
    private final LiensTutelle liens;
    private final TrajetsRecents trajets;
    private final ServiceSms sms;
    private final ServiceChiffrement chiffrement;
    private final JournalAudit journal;
    private final RegistrePurges registre;
    private final Clock horloge;
    private final String adresseDeLApplication;
    private final DateTimeFormatter heure;
    private final SecureRandom alea = new SecureRandom();

    Partages(DepotPartages depot, AccesEnfant acces, ContactsUrgence contacts, LiensTutelle liens, TrajetsRecents trajets,
            ServiceSms sms, ServiceChiffrement chiffrement, JournalAudit journal, RegistrePurges registre, Clock horloge,
            @Value("${fasoguardian.parents.url:http://localhost:4201}") String adresseDeLApplication,
            @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.depot = depot;
        this.acces = acces;
        this.contacts = contacts;
        this.liens = liens;
        this.trajets = trajets;
        this.sms = sms;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.registre = registre;
        this.horloge = horloge;
        this.adresseDeLApplication = adresseDeLApplication.replaceAll("/+$", "");
        this.heure = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH).withZone(fuseau);
    }

    @Transactional(readOnly = true)
    public Optional<PartageVue> enCours(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return depot.enCours(enfantId, horloge.instant()).map(Partages::vue);
    }

    /** Ouvre un partage ; celui qui était en cours est révoqué : un seul lien vaut à la fois. */
    @Transactional
    public PartageVue partager(UUID tuteurId, UUID enfantId, UUID contactId, int dureeMinutes) {
        acces.exigerTuteur(tuteurId, enfantId);
        Duration duree;
        try {
            duree = PartagePosition.duree(dureeMinutes);
        } catch (IllegalArgumentException erreur) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, erreur.getMessage());
        }
        Contact contact = contacts.contact(enfantId, contactId).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Ce contact d'urgence n'existe pas pour cet enfant."));
        Instant maintenant = horloge.instant();
        depot.revoquer(enfantId, maintenant);
        byte[] secret = new byte[16];
        alea.nextBytes(secret);
        String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        PartagePosition partage = new PartagePosition(UUID.randomUUID(), enfantId, tuteurId, contact.lien(),
                masquer(contact.telephoneE164()), maintenant, maintenant.plus(duree), null, 0, null);
        depot.creer(partage, chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, contact.telephoneE164()), sha256(jeton));
        journal.consigner(tuteurId, ROLE, "POSITION_PARTAGEE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        sms.envoyer(contact.telephoneE164(), "FasoGuardian : " + liens.prenomDuTuteur(tuteurId).orElse("un parent")
                + " partage avec vous la position de son enfant jusqu'à " + heure.format(partage.fin()) + ". Lien personnel, ne le "
                + "transférez pas : " + adresseDeLApplication + "/p/" + jeton);
        return vue(partage);
    }

    /** Révocation : l'accès du contact cesse aussitôt. */
    @Transactional
    public void revoquer(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        if (depot.revoquer(enfantId, horloge.instant()) > 0) {
            journal.consigner(tuteurId, ROLE, "PARTAGE_REVOQUE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        }
    }

    /**
     * Ce que voit le contact en ouvrant son lien. Jeton inconnu, partage échu ou révoqué : même réponse, qui
     * ne dit pas lequel.
     */
    @Transactional
    public VuePartagee consulter(String jeton) {
        Instant maintenant = horloge.instant();
        PartagePosition partage = jeton == null || !jeton.matches("[A-Za-z0-9_-]{22}") ? null
                : depot.parJeton(sha256(jeton)).filter(p -> p.actif(maintenant)).orElse(null);
        if (partage == null) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Ce partage est terminé.");
        }
        depot.noterOuverture(partage.id(), maintenant);
        journal.consigner(null, "CONTACT", "POSITION_PARTAGEE_CONSULTEE", "ENFANT", partage.enfantId().toString(), Resultat.SUCCES);
        List<Point> recents = trajets.depuis(partage.enfantId(), maintenant.minus(FRAICHEUR));
        Point dernier = recents.isEmpty() ? null : recents.get(recents.size() - 1);
        return new VuePartagee(liens.prenomDuTuteur(partage.creePar()).orElse(null), partage.fin(), dernier == null ? null
                : new PositionPartagee(dernier.latitude(), dernier.longitude(), dernier.precisionM(), dernier.approximative(), dernier.mesureeLe()));
    }

    /** Les partages terminés depuis trente jours sont effacés (numéro d'un tiers, FG-DOC-06 tableau 18). */
    @Scheduled(cron = "${fasoguardian.geolocalisation.purge-partages:0 15 3 * * *}")
    @SchedulerLock(name = "geolocalisation-purge-partages")
    @Transactional
    public void purger() {
        registre.consigner("PARTAGES_DE_POSITION", depot.purger(horloge.instant().minus(CONSERVATION)));
    }

    private static PartageVue vue(PartagePosition partage) {
        return new PartageVue(partage.id(), partage.destinataireLien(), partage.destinataireMasque(), partage.debut(), partage.fin(),
                partage.ouvertures(), partage.derniereOuverture());
    }

    /** « +22676112208 » devient « +226 76 •• •• 08 ». */
    private static String masquer(String numeroE164) {
        return numeroE164.substring(0, 4) + " " + numeroE164.substring(4, 6) + " •• •• " + numeroE164.substring(numeroE164.length() - 2);
    }

    private static String sha256(String valeur) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valeur.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }
}
