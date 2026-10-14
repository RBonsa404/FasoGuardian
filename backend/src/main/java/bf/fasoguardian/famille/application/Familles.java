package bf.fasoguardian.famille.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.famille.domaine.Enfant;
import bf.fasoguardian.famille.domaine.PhotoEnfant;
import bf.fasoguardian.famille.domaine.RevisionEnfant;
import bf.fasoguardian.famille.infrastructure.DepotEnfants;
import bf.fasoguardian.famille.infrastructure.DepotPhotos;
import bf.fasoguardian.famille.infrastructure.DepotRevisionsEnfant;
import bf.fasoguardian.identite.DossierKycApprouve;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.identite.LiensTutelle.EnfantVerifie;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import tools.jackson.databind.json.JsonMapper;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fiches enfants (US-PAR-004). La fiche naît de l'approbation du dossier KYC, sous l'identifiant qui y
 * figure ; le parent n'y accède qu'au travers d'un lien de tutelle actif.
 */
@Service
public class Familles implements AccesEnfant {

    /** Éléments qui aident à reconnaître l'enfant, tous facultatifs. */
    public record ProfilEnfant(String ecole, String quartier, Integer tailleCm, String signesDistinctifs) {

        static final ProfilEnfant VIDE = new ProfilEnfant(null, null, null, null);
    }

    /** {@code photoModifieeLe} : date de la photo, ou {@code null} s'il n'y en a pas. */
    public record FicheEnfant(UUID id, String prenom, String nom, LocalDate dateNaissance, ProfilEnfant profil,
            Instant modifieLe, Instant photoModifieeLe) {
    }

    public record Photo(byte[] contenu, String typeMime) {
    }

    /** Une photo de 2 Mo suffit à reconnaître l'enfant ; l'application l'allège avant l'envoi. */
    public static final int TAILLE_MAX_PHOTO = 2 * 1024 * 1024;

    /** Une modification : champ, date, prénom vérifié de son auteur (vide sans dossier approuvé), et s'il s'agit du demandeur. */
    public record Revision(String champ, Instant modifieLe, String auteur, boolean parVous) {
    }

    private final DepotEnfants enfants;
    private final DepotRevisionsEnfant revisions;
    private final DepotPhotos photos;
    private final LiensTutelle liens;
    private final JournalAudit journal;
    private final ServiceChiffrement chiffrement;
    private final JsonMapper json;
    private final Clock horloge;

    Familles(DepotEnfants enfants, DepotRevisionsEnfant revisions, DepotPhotos photos, LiensTutelle liens,
            JournalAudit journal, ServiceChiffrement chiffrement, JsonMapper json, Clock horloge) {
        this.enfants = enfants;
        this.revisions = revisions;
        this.photos = photos;
        this.liens = liens;
        this.journal = journal;
        this.chiffrement = chiffrement;
        this.json = json;
        this.horloge = horloge;
    }

    /** Crée la fiche de l'enfant dès que le lien est vérifié. Rejouable sans effet : la fiche n'est créée qu'une fois. */
    @ApplicationModuleListener
    void surDossierApprouve(DossierKycApprouve evenement) {
        if (enfants.existsById(evenement.enfantId())) {
            return;
        }
        EnfantVerifie verifie = liens.enfantDuDossier(evenement.dossierId());
        enfants.save(new Enfant(verifie.enfantId(), verifie.prenom(), verifie.nom(), verifie.dateNaissance(),
                horloge.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public void exigerTuteur(UUID tuteurId, UUID enfantId) {
        if (!liens.estTuteurActif(tuteurId, enfantId)) {
            refuser(tuteurId, enfantId);
        }
    }

    @Transactional(readOnly = true)
    public List<FicheEnfant> enfantsDe(UUID tuteurId) {
        return enfants.findAllById(liens.enfantsDe(tuteurId)).stream().map(this::vue).toList();
    }

    @Transactional(readOnly = true)
    public FicheEnfant fiche(UUID tuteurId, UUID enfantId) {
        exigerTuteur(tuteurId, enfantId);
        return vue(enfants.findById(enfantId).orElseThrow(Familles::introuvable));
    }

    /** Toute modification est horodatée et tracée dans l'historique (US-PAR-004). */
    @Transactional
    public FicheEnfant modifier(UUID tuteurId, UUID enfantId, String prenom, String nom, ProfilEnfant profil) {
        exigerTuteur(tuteurId, enfantId);
        Enfant enfant = enfants.findById(enfantId).orElseThrow(Familles::introuvable);
        Instant maintenant = horloge.instant();
        for (String champ : enfant.mettreAJour(prenom, nom, maintenant)) {
            revisions.save(new RevisionEnfant(enfantId, tuteurId, champ, maintenant));
        }
        if (profil != null) {
            ProfilEnfant propre = new ProfilEnfant(borner(profil.ecole(), 80), borner(profil.quartier(), 80),
                    profil.tailleCm(), borner(profil.signesDistinctifs(), 200));
            if (propre.tailleCm() != null && (propre.tailleCm() < 30 || propre.tailleCm() > 220)) {
                throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "La taille est comprise entre 30 et 220 cm.");
            }
            ProfilEnfant ancien = lireProfil(enfant);
            if (!propre.equals(ancien)) {
                enfant.definirProfil(chiffrement.chiffrer(CategorieDonnee.PROFIL_ENFANT, json.writeValueAsBytes(propre)), maintenant);
                // Un champ par trace, pour que l'historique dise ce qui a changé sans en recopier la valeur.
                if (!Objects.equals(propre.ecole(), ancien.ecole())) {
                    revisions.save(new RevisionEnfant(enfantId, tuteurId, "ecole", maintenant));
                }
                if (!Objects.equals(propre.quartier(), ancien.quartier())) {
                    revisions.save(new RevisionEnfant(enfantId, tuteurId, "quartier", maintenant));
                }
                if (!Objects.equals(propre.tailleCm(), ancien.tailleCm())) {
                    revisions.save(new RevisionEnfant(enfantId, tuteurId, "tailleCm", maintenant));
                }
                if (!Objects.equals(propre.signesDistinctifs(), ancien.signesDistinctifs())) {
                    revisions.save(new RevisionEnfant(enfantId, tuteurId, "signesDistinctifs", maintenant));
                }
            }
        }
        return vue(enfant);
    }

    private ProfilEnfant lireProfil(Enfant enfant) {
        return enfant.profilChiffre() == null ? ProfilEnfant.VIDE
                : json.readValue(chiffrement.dechiffrer(CategorieDonnee.PROFIL_ENFANT, enfant.profilChiffre()), ProfilEnfant.class);
    }

    private static String borner(String texte, int longueurMax) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        String propre = texte.trim();
        if (propre.length() > longueurMax) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Texte trop long (" + longueurMax + " caractères au plus).");
        }
        return propre;
    }

    /** Ajoute ou remplace la photo : JPEG ou PNG vérifié par sa signature, chiffré, tracé dans l'historique. */
    @Transactional
    public FicheEnfant definirPhoto(UUID tuteurId, UUID enfantId, byte[] contenu, String typeMime) {
        exigerTuteur(tuteurId, enfantId);
        Enfant enfant = enfants.findById(enfantId).orElseThrow(Familles::introuvable);
        if (contenu == null || contenu.length == 0 || contenu.length > TAILLE_MAX_PHOTO || !imageConforme(contenu, typeMime)) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Envoyez une photo JPEG ou PNG de 2 Mo au plus.");
        }
        Instant maintenant = horloge.instant();
        byte[] chiffre = chiffrement.chiffrer(CategorieDonnee.PROFIL_ENFANT, contenu);
        photos.findById(enfantId).ifPresentOrElse(
                photo -> photo.remplacer(chiffre, typeMime, contenu.length, maintenant),
                () -> photos.save(new PhotoEnfant(enfantId, chiffre, typeMime, contenu.length, maintenant)));
        revisions.save(new RevisionEnfant(enfantId, tuteurId, "photo", maintenant));
        return vue(enfant);
    }

    @Transactional
    public FicheEnfant retirerPhoto(UUID tuteurId, UUID enfantId) {
        exigerTuteur(tuteurId, enfantId);
        Enfant enfant = enfants.findById(enfantId).orElseThrow(Familles::introuvable);
        photos.findById(enfantId).ifPresent(photo -> {
            photos.delete(photo);
            photos.flush();
            revisions.save(new RevisionEnfant(enfantId, tuteurId, "photo", horloge.instant()));
        });
        return vue(enfant);
    }

    @Transactional(readOnly = true)
    public Optional<Photo> photo(UUID tuteurId, UUID enfantId) {
        exigerTuteur(tuteurId, enfantId);
        return photos.findById(enfantId)
                .map(photo -> new Photo(chiffrement.dechiffrer(CategorieDonnee.PROFIL_ENFANT, photo.contenuChiffre()), photo.typeMime()));
    }

    /** Le contenu doit commencer par la signature du format annoncé : un autre fichier renommé est refusé. */
    private static boolean imageConforme(byte[] contenu, String typeMime) {
        byte[] attendu = switch (typeMime == null ? "" : typeMime) {
            case "image/jpeg" -> new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
            case "image/png" -> new byte[] {(byte) 0x89, 'P', 'N', 'G'};
            default -> null;
        };
        return attendu != null && contenu.length > attendu.length
                && Arrays.equals(Arrays.copyOf(contenu, attendu.length), attendu);
    }

    @Transactional(readOnly = true)
    public List<Revision> historique(UUID tuteurId, UUID enfantId) {
        exigerTuteur(tuteurId, enfantId);
        Map<UUID, String> prenoms = new HashMap<>();
        return revisions.findByEnfantIdOrderByModifieLeDesc(enfantId).stream()
                .map(revision -> new Revision(revision.champ(), revision.modifieLe(),
                        prenoms.computeIfAbsent(revision.auteurId(), auteur -> liens.prenomDuTuteur(auteur).orElse("")),
                        revision.auteurId().equals(tuteurId)))
                .toList();
    }

    private void refuser(UUID tuteurId, UUID enfantId) {
        journal.consigner(tuteurId, "PARENT", "ACCES_REFUSE", "ENFANT", enfantId.toString(), Resultat.REFUS);
        throw introuvable();
    }

    private FicheEnfant vue(Enfant enfant) {
        return new FicheEnfant(enfant.id(), enfant.prenom(), enfant.nom(), enfant.dateNaissance(), lireProfil(enfant),
                enfant.modifieLe(), photos.findById(enfant.id()).map(PhotoEnfant::modifieeLe).orElse(null));
    }

    private static ErreurMetier introuvable() {
        return new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Enfant introuvable.");
    }
}
