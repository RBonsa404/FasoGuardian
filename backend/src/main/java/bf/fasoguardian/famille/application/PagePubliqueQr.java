package bf.fasoguardian.famille.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.famille.ProfilsQr;
import bf.fasoguardian.famille.QrConsulte;
import bf.fasoguardian.famille.TiersAPrevenu;
import bf.fasoguardian.famille.application.DossierMedical.ContactPublic;
import bf.fasoguardian.famille.application.DossierMedical.InformationCritique;
import bf.fasoguardian.famille.domaine.ProfilQr;
import bf.fasoguardian.famille.infrastructure.DepotEnfants;
import bf.fasoguardian.famille.infrastructure.DepotProfilsQr;
import bf.fasoguardian.identite.Telephones;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Page publique consultée en scannant le QR code d'un bracelet (US-TRS-001, US-SYS-010, REQ-SYS-014).
 * Elle ne projette que le numéro du bracelet, les informations médicales marquées critiques et les contacts
 * marqués visibles : jamais de nom, d'initiale, de photo ni de position (ADR 0002). Tout jeton inconnu ou
 * mal formé reçoit la même réponse générique ; au-delà de 20 jetons invalides par minute, la source est
 * bloquée et l'administrateur averti.
 */
@Service
public class PagePubliqueQr implements ProfilsQr {

    public static final int INVALIDES_PAR_MINUTE = 20;
    public static final Duration DUREE_BLOCAGE = Duration.ofMinutes(15);
    public static final int MESSAGES_PAR_HEURE = 3;

    private static final Pattern JETON = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern EMPREINTE = Pattern.compile("[0-9a-f]{64}");
    private static final Logger journalTechnique = LoggerFactory.getLogger(PagePubliqueQr.class);

    public enum Etat {
        TROUVE,
        INCONNU,
        DESACTIVE
    }

    /** Projection minimale : aucun champ ne permet d'identifier ou de localiser l'enfant. */
    public record VuePublique(Etat etat, String numeroBracelet, List<InformationCritique> informations,
            List<ContactPublic> contacts) {

        static VuePublique inconnue() {
            return new VuePublique(Etat.INCONNU, null, List.of(), List.of());
        }
    }

    private final DepotProfilsQr profils;
    private final DepotEnfants enfants;
    private final DossierMedical medical;
    private final ServiceChiffrement chiffrement;
    private final JournalAudit journal;
    private final ApplicationEventPublisher evenements;
    private final JdbcTemplate jdbc;
    private final Clock horloge;

    // Compteurs par source, propres à chaque instance du serveur : suffisant au pilote (deux instances).
    private final Cache<String, AtomicInteger> invalides =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(50_000).build();
    private final Cache<String, Boolean> bloquees =
            Caffeine.newBuilder().expireAfterWrite(DUREE_BLOCAGE).maximumSize(50_000).build();
    private final Cache<UUID, AtomicInteger> messages =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(50_000).build();

    PagePubliqueQr(DepotProfilsQr profils, DepotEnfants enfants, DossierMedical medical, ServiceChiffrement chiffrement,
            JournalAudit journal, ApplicationEventPublisher evenements, JdbcTemplate jdbc, Clock horloge) {
        this.profils = profils;
        this.enfants = enfants;
        this.medical = medical;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.evenements = evenements;
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    // --------------------------------------------------------------- page publique

    /** @throws ErreurMetier TROP_DE_REQUETES si la source est bloquée pour énumération */
    @Transactional
    public VuePublique consulter(String jeton, String adresseIp) {
        String source = pseudonymiser(adresseIp);
        exigerSourceNonBloquee(source);
        ProfilQr profil = profil(jeton);
        Instant maintenant = horloge.instant();
        if (profil == null) {
            consigner(null, source, Etat.INCONNU, maintenant);
            compterInvalide(source);
            return VuePublique.inconnue();
        }
        if (!profil.actif()) {
            consigner(profil.enfantId(), source, Etat.DESACTIVE, maintenant);
            evenements.publishEvent(new QrConsulte(profil.enfantId(), profil.numeroBracelet(), false, maintenant));
            return new VuePublique(Etat.DESACTIVE, profil.numeroBracelet(), List.of(), List.of());
        }
        consigner(profil.enfantId(), source, Etat.TROUVE, maintenant);
        evenements.publishEvent(new QrConsulte(profil.enfantId(), profil.numeroBracelet(), true, maintenant));
        return vue(profil);
    }

    /**
     * Enregistre le message du tiers (son numéro, un lieu facultatif) pour la famille. Le numéro et le lieu
     * sont chiffrés et conservés 30 jours ; la famille les lit dans l'application authentifiée.
     *
     * @throws ErreurMetier TELEPHONE_INVALIDE, TROP_DE_REQUETES ou RESSOURCE_INTROUVABLE (jeton sans page active)
     */
    @Transactional
    public VuePublique prevenir(String jeton, String adresseIp, String telephoneTiers, String lieu) {
        exigerSourceNonBloquee(pseudonymiser(adresseIp));
        ProfilQr profil = profil(jeton);
        if (profil == null || !profil.actif()) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Page indisponible.");
        }
        String telephone;
        try {
            telephone = Telephones.normaliserE164(telephoneTiers);
        } catch (IllegalArgumentException erreur) {
            throw new ErreurMetier(CodeErreur.TELEPHONE_INVALIDE, "Saisissez un numéro mobile à 8 chiffres.");
        }
        if (messages.get(profil.enfantId(), cle -> new AtomicInteger()).incrementAndGet() > MESSAGES_PAR_HEURE) {
            throw new ErreurMetier(CodeErreur.TROP_DE_REQUETES, "La famille a déjà été prévenue. Appelez-la ou composez le 17.");
        }
        String lieuPropre = lieu == null ? "" : lieu.trim();
        if (lieuPropre.length() > 120) {
            lieuPropre = lieuPropre.substring(0, 120);
        }
        UUID id = UUID.randomUUID();
        Instant maintenant = horloge.instant();
        jdbc.update("""
                INSERT INTO famille.signalement_tiers (id, enfant_id, telephone_chiffre, lieu_chiffre, recu_le)
                VALUES (?, ?, ?, ?, ?)
                """, id, profil.enfantId(), chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, telephone),
                lieuPropre.isEmpty() ? null : chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, lieuPropre),
                Timestamp.from(maintenant));
        evenements.publishEvent(new TiersAPrevenu(profil.enfantId(), profil.numeroBracelet(), id, maintenant));
        return vue(profil);
    }

    /** Relit la page d'un jeton déjà consulté, sans journaliser de nouveau scan ; {@code null} si elle n'est pas active. */
    @Transactional(readOnly = true)
    public VuePublique relire(String jeton) {
        ProfilQr profil = profil(jeton);
        return profil == null || !profil.actif() ? null : vue(profil);
    }

    // ------------------------------------------------------------- rattachement

    @Override
    @Transactional
    public void associer(UUID enfantId, String jetonSha256, String numeroBracelet) {
        if (jetonSha256 == null || !EMPREINTE.matcher(jetonSha256).matches() || numeroBracelet == null
                || numeroBracelet.isBlank() || numeroBracelet.length() > 32) {
            throw new IllegalArgumentException("Empreinte de jeton ou numéro de bracelet invalide");
        }
        if (!enfants.existsById(enfantId)) {
            throw new IllegalArgumentException("Enfant inconnu");
        }
        Instant maintenant = horloge.instant();
        profils.findById(enfantId).ifPresentOrElse(
                profil -> profil.remplacer(jetonSha256, numeroBracelet.trim(), maintenant),
                () -> profils.save(new ProfilQr(enfantId, jetonSha256, numeroBracelet.trim(), maintenant)));
    }

    @Override
    @Transactional
    public void suspendre(UUID enfantId) {
        profils.findById(enfantId).ifPresent(ProfilQr::suspendre);
    }

    @Override
    @Transactional
    public void reactiver(UUID enfantId) {
        profils.findById(enfantId).ifPresent(ProfilQr::reactiver);
    }

    // -------------------------------------------------------------------- aides

    private VuePublique vue(ProfilQr profil) {
        return new VuePublique(Etat.TROUVE, profil.numeroBracelet(), medical.informationsCritiques(profil.enfantId()),
                medical.contactsVisibles(profil.enfantId()));
    }

    private ProfilQr profil(String jeton) {
        if (jeton == null || !JETON.matcher(jeton).matches()) {
            return null;
        }
        return profils.findByJetonSha256(sha256(jeton)).orElse(null);
    }

    private void exigerSourceNonBloquee(String source) {
        if (bloquees.getIfPresent(source) != null) {
            throw new ErreurMetier(CodeErreur.TROP_DE_REQUETES, "Trop de tentatives. Réessayez plus tard.");
        }
    }

    private void compterInvalide(String source) {
        if (invalides.get(source, cle -> new AtomicInteger()).incrementAndGet() > INVALIDES_PAR_MINUTE
                && bloquees.asMap().putIfAbsent(source, Boolean.TRUE) == null) {
            journal.consigner(null, "SYSTEME", "ENUMERATION_QR", "SOURCE", source, Resultat.REFUS);
            journalTechnique.warn("Énumération de jetons QR : source {} bloquée {} minutes", source,
                    DUREE_BLOCAGE.toMinutes());
        }
    }

    private void consigner(UUID enfantId, String source, Etat etat, Instant maintenant) {
        jdbc.update("""
                INSERT INTO famille.consultation_qr (id, consulte_le, enfant_id, ip_pseudonymisee, resultat)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), Timestamp.from(maintenant), enfantId, source, etat.name());
    }

    /** Pseudonyme de l'adresse IP, renouvelé chaque mois : deux mois de journal ne se recoupent pas. */
    private String pseudonymiser(String adresseIp) {
        String mois = YearMonth.from(horloge.instant().atOffset(ZoneOffset.UTC)).toString();
        return chiffrement.empreinteLibre("IP:" + mois, adresseIp == null ? "" : adresseIp).substring(0, 32);
    }

    public static String sha256(String jeton) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }
}
