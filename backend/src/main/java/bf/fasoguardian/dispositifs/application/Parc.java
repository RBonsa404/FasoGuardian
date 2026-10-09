package bf.fasoguardian.dispositifs.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.domaine.Appairage;
import bf.fasoguardian.dispositifs.domaine.Bracelet;
import bf.fasoguardian.dispositifs.domaine.Bracelet.TransitionIllegaleException;
import bf.fasoguardian.dispositifs.domaine.CodeAppairage;
import bf.fasoguardian.dispositifs.domaine.FabriqueBracelet;
import bf.fasoguardian.dispositifs.domaine.FabriqueBracelet.BraceletPrepare;
import bf.fasoguardian.dispositifs.domaine.MotifFin;
import bf.fasoguardian.dispositifs.domaine.StatutBracelet;
import bf.fasoguardian.dispositifs.infrastructure.DepotAppairages;
import bf.fasoguardian.dispositifs.infrastructure.DepotBracelets;
import bf.fasoguardian.dispositifs.infrastructure.DepotConfigurations;
import bf.fasoguardian.famille.ProfilsQr;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parc de bracelets tenu par le service après-vente (US-SAV-002) : enregistrement des unités préparées à
 * l'atelier, vue consolidée du cycle de vie, retours, remises en stock et réformes. L'agent ne voit jamais
 * l'identité de l'enfant qui porte un bracelet.
 */
@Service
public class Parc {

    private static final String ROLE = "SAV";
    private static final Pattern NUMERO_SERIE = Pattern.compile("FG-[0-9]{4,8}");
    private static final Pattern IMEI = Pattern.compile("[0-9]{15}");
    private static final Pattern EMPREINTE = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern VERSION = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");

    /** @param clePublique clé publique du certificat (SubjectPublicKeyInfo, base64), ou {@code null} */
    public record Enregistrement(String numeroSerie, String imei, String revisionMaterielle, String versionLogiciel,
            String empreinteCertificat, String clePublique) {
    }

    /**
     * Ce qui doit être imprimé sur la carte d'activation et gravé sur le bracelet. Ces deux secrets ne sont
     * remis qu'ici : seules leurs empreintes sont conservées.
     */
    public record CarteActivation(String numeroSerie, String codeAppairage, String jetonQr) {
    }

    public record BraceletParc(String numeroSerie, StatutBracelet statut, String revisionMaterielle,
            String versionLogiciel, boolean certificatRevoque, boolean appaire, LocalDate garantieJusquAu,
            Instant modifieLe) {
    }

    public record Periode(Instant debut, Instant fin, MotifFin motifFin) {
    }

    public record FicheBracelet(BraceletParc bracelet, String imeiMasque, String empreinteCertificat,
            List<Periode> appairages) {
    }

    public record CertificatRevoque(String numeroSerie, String empreinteCertificat, Instant revoqueLe) {
    }

    private final DepotBracelets bracelets;
    private final DepotAppairages appairages;
    private final DepotConfigurations configurations;
    private final ProfilsQr profilsQr;
    private final LiensTutelle liens;
    private final Notifications notifications;
    private final ServiceChiffrement chiffrement;
    private final JournalAudit journal;
    private final Clock horloge;
    private final SecureRandom alea = new SecureRandom();

    Parc(DepotBracelets bracelets, DepotAppairages appairages, DepotConfigurations configurations, ProfilsQr profilsQr,
            LiensTutelle liens, Notifications notifications, ServiceChiffrement chiffrement, JournalAudit journal,
            Clock horloge) {
        this.bracelets = bracelets;
        this.appairages = appairages;
        this.configurations = configurations;
        this.profilsQr = profilsQr;
        this.liens = liens;
        this.notifications = notifications;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.horloge = horloge;
    }

    /** Enregistre un bracelet préparé à l'atelier, avec la configuration par défaut de sa révision matérielle. */
    @Transactional
    public CarteActivation enregistrer(UUID agentId, Enregistrement saisie) {
        String numero = saisie.numeroSerie().trim().toUpperCase(Locale.ROOT);
        String imei = saisie.imei().replaceAll("\\s", "");
        String certificat = empreinteCertificat(saisie.empreinteCertificat());
        if (!NUMERO_SERIE.matcher(numero).matches()) {
            throw invalide("Le numéro de série a la forme FG-2291.");
        }
        if (!IMEI.matcher(imei).matches() || !luhnValide(imei)) {
            throw invalide("L'IMEI compte 15 chiffres ; sa clé de contrôle est incorrecte.");
        }
        if (!FabriqueBracelet.revisionsConnues().contains(saisie.revisionMaterielle())) {
            throw invalide("Révision matérielle inconnue.");
        }
        if (!VERSION.matcher(saisie.versionLogiciel()).matches()) {
            throw invalide("La version du logiciel a la forme 2.4.1.");
        }
        String imeiEmpreinte = chiffrement.empreinte(CategorieDonnee.IMEI, imei);
        if (bracelets.existsByNumeroSerie(numero) || bracelets.existsByImeiEmpreinte(imeiEmpreinte)
                || bracelets.existsByEmpreinteCertificat(certificat)) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Ce numéro de série, cet IMEI ou ce certificat est déjà au parc.");
        }
        String code = CodeAppairage.generer(alea);
        byte[] jeton = new byte[16];
        alea.nextBytes(jeton);
        String jetonQr = Base64.getUrlEncoder().withoutPadding().encodeToString(jeton);
        BraceletPrepare prepare = FabriqueBracelet.preparer(numero, chiffrement.chiffrerTexte(CategorieDonnee.IMEI, imei),
                imeiEmpreinte, saisie.revisionMaterielle(), saisie.versionLogiciel(), certificat, sha256(jetonQr),
                empreinteCode(code), horloge.instant());
        prepare.bracelet().enregistrerClePublique(clePublique(saisie.clePublique()));
        bracelets.save(prepare.bracelet());
        configurations.save(prepare.configuration());
        consigner(agentId, "BRACELET_ENREGISTRE", prepare.bracelet());
        return new CarteActivation(numero, CodeAppairage.presenter(code), jetonQr);
    }

    @Transactional(readOnly = true)
    public List<BraceletParc> parc(StatutBracelet statut) {
        List<Bracelet> unites = statut == null ? bracelets.findAllByOrderByNumeroSerie()
                : bracelets.findByStatutOrderByNumeroSerie(statut);
        Set<UUID> appaires = appairages.findAll().stream().filter(a -> a.fin() == null).map(Appairage::braceletId)
                .collect(Collectors.toSet());
        return unites.stream().map(b -> vue(b, appaires.contains(b.id()))).toList();
    }

    /** Fiche d'une unité ; la consultation, qui déchiffre l'IMEI, est journalisée. */
    @Transactional
    public FicheBracelet fiche(UUID agentId, String numeroSerie) {
        Bracelet bracelet = bracelet(numeroSerie);
        String imei = chiffrement.dechiffrerTexte(CategorieDonnee.IMEI, bracelet.imeiChiffre());
        consigner(agentId, "BRACELET_CONSULTE", bracelet);
        return new FicheBracelet(vue(bracelet), "•••••••••••" + imei.substring(11), bracelet.empreinteCertificat(),
                appairages.findByBraceletIdOrderByDebutDesc(bracelet.id()).stream()
                        .map(a -> new Periode(a.debut(), a.fin(), a.motifFin())).toList());
    }

    /** Unité retournée : elle apparaît « En SAV » dans le parc et n'est plus active pour l'enfant. */
    @Transactional
    public BraceletParc retourner(UUID agentId, String numeroSerie) {
        Bracelet bracelet = bracelet(numeroSerie);
        Instant maintenant = horloge.instant();
        try {
            bracelet.retournerAuSav(maintenant);
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        }
        appairages.findByBraceletIdAndFinIsNull(bracelet.id()).ifPresent(appairage -> {
            appairage.clore(MotifFin.PANNE, maintenant);
            Message message = Message.simple("BRACELET_EN_SAV", "Bracelet au service après-vente", "le bracelet "
                    + bracelet.numeroSerie() + " est pris en charge par le service après-vente. Il n'est plus associé à votre enfant.");
            liens.tuteursActifsDe(appairage.enfantId()).forEach(tuteur -> notifications.notifier(tuteur, Urgence.INFORMATION, message));
        });
        profilsQr.dissocier(bracelet.jetonQrSha256());
        consigner(agentId, "BRACELET_RETOURNE", bracelet);
        return vue(bracelet);
    }

    /**
     * Remise en stock d'une unité reconditionnée : nouveau code d'appairage, et nouveau certificat si
     * l'ancien avait été révoqué. Le QR gravé, inchangé, n'est plus rattaché à aucun enfant.
     */
    @Transactional
    public CarteActivation remettreEnStock(UUID agentId, String numeroSerie, String nouvelleEmpreinteCertificat) {
        Bracelet bracelet = bracelet(numeroSerie);
        String certificat = nouvelleEmpreinteCertificat == null || nouvelleEmpreinteCertificat.isBlank() ? null
                : empreinteCertificat(nouvelleEmpreinteCertificat);
        if (certificat != null && !certificat.equals(bracelet.empreinteCertificat())
                && bracelets.existsByEmpreinteCertificat(certificat)) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Ce certificat appartient déjà à un bracelet du parc.");
        }
        if (certificat != null && certificat.equals(bracelet.empreinteCertificat()) && bracelet.certificatRevoque()) {
            throw invalide("Ce certificat a été révoqué : l'atelier doit en émettre un nouveau.");
        }
        String code = CodeAppairage.generer(alea);
        try {
            bracelet.remettreEnStock(empreinteCode(code), certificat, horloge.instant());
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        }
        profilsQr.dissocier(bracelet.jetonQrSha256());
        consigner(agentId, "BRACELET_REMIS_EN_STOCK", bracelet);
        return new CarteActivation(bracelet.numeroSerie(), CodeAppairage.presenter(code), null);
    }

    @Transactional
    public BraceletParc reformer(UUID agentId, String numeroSerie) {
        Bracelet bracelet = bracelet(numeroSerie);
        Instant maintenant = horloge.instant();
        try {
            bracelet.reformer(maintenant);
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        }
        appairages.findByBraceletIdAndFinIsNull(bracelet.id()).ifPresent(a -> a.clore(MotifFin.PERTE, maintenant));
        profilsQr.dissocier(bracelet.jetonQrSha256());
        consigner(agentId, "BRACELET_REFORME", bracelet);
        return vue(bracelet);
    }

    /** Certificats à refuser par le broker : source de la liste de révocation (ADR 0009). */
    @Transactional(readOnly = true)
    public List<CertificatRevoque> certificatsRevoques() {
        return bracelets.findByCertificatRevoqueLeIsNotNullOrderByCertificatRevoqueLe().stream()
                .map(b -> new CertificatRevoque(b.numeroSerie(), b.empreinteCertificat(), b.certificatRevoqueLe()))
                .toList();
    }

    // -------------------------------------------------------------------- aides

    private Bracelet bracelet(String numeroSerie) {
        return bracelets.findByNumeroSerie(numeroSerie.trim().toUpperCase(Locale.ROOT)).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Bracelet introuvable."));
    }

    private BraceletParc vue(Bracelet bracelet) {
        return vue(bracelet, appairages.findByBraceletIdAndFinIsNull(bracelet.id()).isPresent());
    }

    private static BraceletParc vue(Bracelet b, boolean appaire) {
        return new BraceletParc(b.numeroSerie(), b.statut(), b.revisionMaterielle(), b.versionLogiciel(),
                b.certificatRevoque(), appaire, b.garantieJusquAu(), b.modifieLe());
    }

    private void consigner(UUID agentId, String action, Bracelet bracelet) {
        journal.consigner(agentId, ROLE, action, "BRACELET", bracelet.id().toString(), Resultat.SUCCES);
    }

    private String empreinteCode(String code) {
        return chiffrement.empreinteLibre(Appairages.DOMAINE_CODE, code);
    }

    /** Empreinte SHA-256 d'un certificat, saisie avec ou sans deux-points, en majuscules ou non. */
    private static String empreinteCertificat(String saisie) {
        String empreinte = saisie.replaceAll("[:\\s]", "").toLowerCase(Locale.ROOT);
        if (!EMPREINTE.matcher(empreinte).matches()) {
            throw invalide("L'empreinte du certificat est un SHA-256 de 64 caractères hexadécimaux.");
        }
        return empreinte;
    }

    static boolean luhnValide(String chiffres) {
        int somme = 0;
        for (int i = 0; i < chiffres.length(); i++) {
            int chiffre = chiffres.charAt(chiffres.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                chiffre *= 2;
                if (chiffre > 9) {
                    chiffre -= 9;
                }
            }
            somme += chiffre;
        }
        return somme % 10 == 0;
    }

    private static String sha256(String valeur) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(valeur.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    private static ErreurMetier invalide(String detail) {
        return new ErreurMetier(CodeErreur.REQUETE_INVALIDE, detail);
    }

    private static ErreurMetier conflit() {
        return new ErreurMetier(CodeErreur.CONFLIT, "Cette action n'est pas possible dans l'état actuel du bracelet.");
    }

    /** Clé publique EC P-256 au format SubjectPublicKeyInfo ; absente, les SMS de repli du bracelet seront rejetés. */
    private static byte[] clePublique(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] cle = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            java.security.KeyFactory.getInstance("EC").generatePublic(new java.security.spec.X509EncodedKeySpec(cle));
            return cle;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException erreur) {
            throw invalide("La clé publique du bracelet n'est pas une clé EC au format SubjectPublicKeyInfo en base64.");
        }
    }
}
