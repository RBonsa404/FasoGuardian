package bf.fasoguardian.dispositifs.domaine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Bracelet connecté et son cycle de vie (FG-DOC-07 §4.3). Son identifiant d'appareil est le numéro gravé,
 * distinct de l'IMEI ; il n'a qu'un appairage actif à la fois.
 */
@Entity
@Table(schema = "dispositifs", name = "bracelet")
public class Bracelet {

    /** Le suivi continue 72 h après une déclaration de perte (ADR 0009). */
    public static final Duration SUIVI_APRES_PERTE = Duration.ofHours(72);
    public static final int MOIS_DE_GARANTIE = 12;

    /** Transition refusée par le cycle de vie. */
    public static class TransitionIllegaleException extends RuntimeException {
        public TransitionIllegaleException(StatutBracelet statut, String action) {
            super("Action « " + action + " » impossible pour un bracelet au statut " + statut);
        }
    }

    @Id
    private UUID id;

    @Column(name = "numero_serie", nullable = false)
    private String numeroSerie;

    @Column(name = "imei_chiffre", nullable = false)
    private byte[] imeiChiffre;

    @Column(name = "imei_empreinte", nullable = false)
    private String imeiEmpreinte;

    @Column(name = "revision_materielle", nullable = false)
    private String revisionMaterielle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutBracelet statut;

    @Column(name = "version_logiciel", nullable = false)
    private String versionLogiciel;

    @Column(name = "empreinte_certificat", nullable = false)
    private String empreinteCertificat;

    @Column(name = "certificat_revoque_le")
    private Instant certificatRevoqueLe;

    @Column(name = "jeton_qr_sha256", nullable = false)
    private String jetonQrSha256;

    @Column(name = "code_appairage_empreinte")
    private String codeAppairageEmpreinte;

    @Column(name = "garantie_jusqu_au")
    private LocalDate garantieJusquAu;

    @Column(name = "suivi_jusqu_au")
    private Instant suiviJusquAu;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Version
    private long version;

    protected Bracelet() {
    }

    Bracelet(String numeroSerie, byte[] imeiChiffre, String imeiEmpreinte, String revisionMaterielle,
            String versionLogiciel, String empreinteCertificat, String jetonQrSha256, String codeAppairageEmpreinte,
            Instant maintenant) {
        this.id = UUID.randomUUID();
        this.numeroSerie = numeroSerie;
        this.imeiChiffre = imeiChiffre;
        this.imeiEmpreinte = imeiEmpreinte;
        this.revisionMaterielle = revisionMaterielle;
        this.versionLogiciel = versionLogiciel;
        this.empreinteCertificat = empreinteCertificat;
        this.jetonQrSha256 = jetonQrSha256;
        this.codeAppairageEmpreinte = codeAppairageEmpreinte;
        this.statut = StatutBracelet.EN_STOCK;
        this.creeLe = maintenant;
        this.modifieLe = maintenant;
    }

    /** Appairage : le code est consommé, le bracelet devient actif et sa garantie démarre à la première remise. */
    public void activer(Instant maintenant) {
        if (statut != StatutBracelet.EN_STOCK || certificatRevoque() || codeAppairageEmpreinte == null) {
            throw new TransitionIllegaleException(statut, "appairer");
        }
        codeAppairageEmpreinte = null;
        if (garantieJusquAu == null) {
            garantieJusquAu = LocalDate.ofInstant(maintenant, ZoneOffset.UTC).plusMonths(MOIS_DE_GARANTIE);
        }
        passer(StatutBracelet.ACTIF, maintenant);
    }

    public void declarerPerdu(Instant maintenant) {
        exiger(StatutBracelet.ACTIF, "déclarer perdu");
        suiviJusquAu = maintenant.plus(SUIVI_APRES_PERTE);
        passer(StatutBracelet.PERDU, maintenant);
    }

    /** Un bracelet volé ne doit plus pouvoir se connecter : son certificat est révoqué aussitôt (US-PAR-014). */
    public void declarerVole(Instant maintenant) {
        if (statut != StatutBracelet.ACTIF && statut != StatutBracelet.PERDU) {
            throw new TransitionIllegaleException(statut, "déclarer volé");
        }
        suiviJusquAu = null;
        revoquerCertificat(maintenant);
        passer(StatutBracelet.VOLE, maintenant);
    }

    /** Bracelet perdu puis retrouvé pendant la fenêtre de suivi. */
    public void retrouver(Instant maintenant) {
        if (statut != StatutBracelet.PERDU || certificatRevoque()) {
            throw new TransitionIllegaleException(statut, "déclarer retrouvé");
        }
        suiviJusquAu = null;
        passer(StatutBracelet.ACTIF, maintenant);
    }

    /** Fin du suivi d'un bracelet perdu : il ne doit plus pouvoir se connecter. */
    public void cloreSuivi(Instant maintenant) {
        exiger(StatutBracelet.PERDU, "clore le suivi");
        suiviJusquAu = null;
        revoquerCertificat(maintenant);
        modifieLe = maintenant;
    }

    /** Retour au service après-vente : panne, désappairage ou reprise d'une unité. */
    public void retournerAuSav(Instant maintenant) {
        if (statut == StatutBracelet.REFORME || statut == StatutBracelet.EN_SAV) {
            throw new TransitionIllegaleException(statut, "retourner au SAV");
        }
        codeAppairageEmpreinte = null;
        suiviJusquAu = null;
        passer(StatutBracelet.EN_SAV, maintenant);
    }

    /**
     * Remise en stock d'une unité reconditionnée, avec un nouveau code d'appairage. Si le certificat a été
     * révoqué, l'atelier doit en avoir émis un nouveau.
     */
    public void remettreEnStock(String nouveauCodeEmpreinte, String nouvelleEmpreinteCertificat, Instant maintenant) {
        exiger(StatutBracelet.EN_SAV, "remettre en stock");
        if (nouvelleEmpreinteCertificat != null) {
            empreinteCertificat = nouvelleEmpreinteCertificat;
            certificatRevoqueLe = null;
        }
        if (certificatRevoque()) {
            throw new TransitionIllegaleException(statut, "remettre en stock sans nouveau certificat");
        }
        codeAppairageEmpreinte = nouveauCodeEmpreinte;
        passer(StatutBracelet.EN_STOCK, maintenant);
    }

    public void reformer(Instant maintenant) {
        if (statut == StatutBracelet.ACTIF || statut == StatutBracelet.REFORME) {
            throw new TransitionIllegaleException(statut, "réformer");
        }
        codeAppairageEmpreinte = null;
        suiviJusquAu = null;
        revoquerCertificat(maintenant);
        passer(StatutBracelet.REFORME, maintenant);
    }

    private void revoquerCertificat(Instant maintenant) {
        if (certificatRevoqueLe == null) {
            certificatRevoqueLe = maintenant;
        }
    }

    private void exiger(StatutBracelet attendu, String action) {
        if (statut != attendu) {
            throw new TransitionIllegaleException(statut, action);
        }
    }

    private void passer(StatutBracelet nouveau, Instant maintenant) {
        statut = nouveau;
        modifieLe = maintenant;
    }

    public UUID id() {
        return id;
    }

    public String numeroSerie() {
        return numeroSerie;
    }

    public byte[] imeiChiffre() {
        return imeiChiffre;
    }

    public String revisionMaterielle() {
        return revisionMaterielle;
    }

    public StatutBracelet statut() {
        return statut;
    }

    public String versionLogiciel() {
        return versionLogiciel;
    }

    public String empreinteCertificat() {
        return empreinteCertificat;
    }

    public boolean certificatRevoque() {
        return certificatRevoqueLe != null;
    }

    public Instant certificatRevoqueLe() {
        return certificatRevoqueLe;
    }

    public String jetonQrSha256() {
        return jetonQrSha256;
    }

    public LocalDate garantieJusquAu() {
        return garantieJusquAu;
    }

    public Instant suiviJusquAu() {
        return suiviJusquAu;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
