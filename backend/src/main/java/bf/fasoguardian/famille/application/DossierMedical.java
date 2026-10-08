package bf.fasoguardian.famille.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.famille.domaine.ContactUrgence;
import bf.fasoguardian.famille.domaine.FicheSante;
import bf.fasoguardian.famille.domaine.RevisionSante;
import bf.fasoguardian.famille.infrastructure.DepotContacts;
import bf.fasoguardian.famille.infrastructure.DepotFichesSante;
import bf.fasoguardian.famille.infrastructure.DepotRevisionsSante;
import bf.fasoguardian.identite.Telephones;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fiche médicale et contacts d'urgence d'un enfant (US-PAR-011, US-PAR-003). Données chiffrées au niveau
 * applicatif ; chaque consultation de la fiche santé est journalisée et chaque modification crée une révision.
 */
@Service
public class DossierMedical {

    private static final Set<String> GROUPES_SANGUINS = Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
    private static final int ELEMENTS_MAX = 20;

    public enum TypeElement {
        ALLERGIE,
        PATHOLOGIE,
        TRAITEMENT,
        AUTRE
    }

    public record ElementMedical(TypeElement type, String libelle, boolean critique) {
    }

    public record FicheSanteVue(String groupeSanguin, boolean groupeSanguinSurQr, List<ElementMedical> elements,
            Instant modifieLe) {
    }

    public record RevisionVue(int nombreElements, int nombreCritiques, Instant modifieLe) {
    }

    public record ContactVue(UUID id, String lien, String nom, String telephone, boolean visibleSurQr, int rang) {
    }

    public record SaisieContact(String lien, String nom, String telephone, boolean visibleSurQr) {
    }

    /** Contenu chiffré de la fiche. */
    record Contenu(String groupeSanguin, boolean groupeSanguinSurQr, List<ElementMedical> elements) {
    }

    private final AccesEnfant acces;
    private final DepotFichesSante fiches;
    private final DepotRevisionsSante revisions;
    private final DepotContacts contacts;
    private final ServiceChiffrement chiffrement;
    private final JournalAudit journal;
    private final JsonMapper json;
    private final Clock horloge;

    DossierMedical(AccesEnfant acces, DepotFichesSante fiches, DepotRevisionsSante revisions, DepotContacts contacts,
            ServiceChiffrement chiffrement, JournalAudit journal, JsonMapper json, Clock horloge) {
        this.acces = acces;
        this.fiches = fiches;
        this.revisions = revisions;
        this.contacts = contacts;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.json = json;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------ fiche santé

    @Transactional
    public FicheSanteVue fiche(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        journal.consigner(tuteurId, "PARENT", "CONSULTATION_FICHE_SANTE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        return fiches.findById(enfantId).map(this::vue).orElseGet(() -> new FicheSanteVue(null, false, List.of(), null));
    }

    @Transactional
    public FicheSanteVue enregistrer(UUID tuteurId, UUID enfantId, String groupeSanguin, boolean groupeSanguinSurQr,
            List<ElementMedical> elements) {
        acces.exigerTuteur(tuteurId, enfantId);
        List<ElementMedical> propres = elements == null ? List.of() : elements.stream()
                .map(e -> new ElementMedical(e.type(), e.libelle() == null ? "" : e.libelle().trim(), e.critique()))
                .toList();
        if (groupeSanguin != null && !GROUPES_SANGUINS.contains(groupeSanguin)) {
            throw invalide("Groupe sanguin inconnu.");
        }
        if (propres.size() > ELEMENTS_MAX
                || propres.stream().anyMatch(e -> e.type() == null || e.libelle().isEmpty() || e.libelle().length() > 120)) {
            throw invalide("Chaque information médicale porte un type et un libellé de 120 caractères au plus (20 au maximum).");
        }
        Instant maintenant = horloge.instant();
        byte[] chiffre = chiffrement.chiffrer(CategorieDonnee.SANTE, json.writeValueAsBytes(new Contenu(groupeSanguin, groupeSanguinSurQr && groupeSanguin != null, propres)));
        FicheSante fiche = fiches.findById(enfantId).orElse(null);
        if (fiche == null) {
            fiche = fiches.save(new FicheSante(enfantId, chiffre, maintenant));
        } else {
            fiche.remplacer(chiffre, maintenant);
        }
        revisions.save(new RevisionSante(enfantId, tuteurId, propres.size(),
                (int) propres.stream().filter(ElementMedical::critique).count(), maintenant));
        journal.consigner(tuteurId, "PARENT", "MODIFICATION_FICHE_SANTE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        return new FicheSanteVue(groupeSanguin, groupeSanguinSurQr && groupeSanguin != null, propres, maintenant);
    }

    @Transactional(readOnly = true)
    public List<RevisionVue> revisions(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return revisions.findByEnfantIdOrderByModifieLeDesc(enfantId).stream()
                .map(r -> new RevisionVue(r.nombreElements(), r.nombreCritiques(), r.modifieLe())).toList();
    }

    // ------------------------------------------------------- projection publique

    /** Information médicale affichable sur la page publique : libellé du type et libellé de l'élément. */
    public record InformationCritique(String type, String libelle) {
    }

    /** Contact affichable sur la page publique : son lien avec l'enfant et le numéro à composer, sans son nom. */
    public record ContactPublic(String lien, String telephone) {
    }

    /**
     * Éléments marqués critiques par le parent, et groupe sanguin s'il a choisi de l'afficher.
     * Réservé à la page publique QR : aucun contrôle d'accès, aucune donnée non critique.
     */
    @Transactional(readOnly = true)
    public List<InformationCritique> informationsCritiques(UUID enfantId) {
        return fiches.findById(enfantId).map(fiche -> {
            Contenu contenu = json.readValue(chiffrement.dechiffrer(CategorieDonnee.SANTE, fiche.contenuChiffre()), Contenu.class);
            List<InformationCritique> informations = new java.util.ArrayList<>();
            contenu.elements().stream().filter(ElementMedical::critique)
                    .forEach(e -> informations.add(new InformationCritique(libelle(e.type()), e.libelle())));
            if (contenu.groupeSanguinSurQr() && contenu.groupeSanguin() != null) {
                informations.add(new InformationCritique("Groupe sanguin", contenu.groupeSanguin()));
            }
            return List.copyOf(informations);
        }).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<ContactPublic> contactsVisibles(UUID enfantId) {
        return contacts.findByEnfantIdOrderByRang(enfantId).stream().filter(ContactUrgence::visibleSurQr)
                .map(c -> new ContactPublic(c.lien(), chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, c.telephoneChiffre())))
                .toList();
    }

    private static String libelle(TypeElement type) {
        return switch (type) {
            case ALLERGIE -> "Allergie";
            case PATHOLOGIE -> "Pathologie";
            case TRAITEMENT -> "Traitement";
            case AUTRE -> "Information";
        };
    }

    // ---------------------------------------------------------------- contacts

    @Transactional(readOnly = true)
    public List<ContactVue> contacts(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return contacts.findByEnfantIdOrderByRang(enfantId).stream().map(this::vue).toList();
    }

    @Transactional
    public ContactVue ajouter(UUID tuteurId, UUID enfantId, SaisieContact saisie) {
        acces.exigerTuteur(tuteurId, enfantId);
        String telephone = valider(saisie);
        long existants = contacts.countByEnfantId(enfantId);
        if (existants >= ContactUrgence.MAXIMUM_PAR_ENFANT) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Cinq contacts au maximum par enfant.");
        }
        return vue(contacts.save(new ContactUrgence(enfantId, saisie.lien().trim(),
                chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, saisie.nom().trim()),
                chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, telephone), saisie.visibleSurQr(),
                (int) existants + 1, horloge.instant())));
    }

    @Transactional
    public ContactVue modifier(UUID tuteurId, UUID enfantId, UUID contactId, SaisieContact saisie) {
        acces.exigerTuteur(tuteurId, enfantId);
        String telephone = valider(saisie);
        ContactUrgence contact = contact(enfantId, contactId);
        contact.modifier(saisie.lien().trim(), chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, saisie.nom().trim()),
                chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, telephone), saisie.visibleSurQr());
        return vue(contact);
    }

    @Transactional
    public void supprimer(UUID tuteurId, UUID enfantId, UUID contactId) {
        acces.exigerTuteur(tuteurId, enfantId);
        contacts.delete(contact(enfantId, contactId));
    }

    // -------------------------------------------------------------------- aides

    private ContactUrgence contact(UUID enfantId, UUID contactId) {
        return contacts.findById(contactId).filter(c -> c.enfantId().equals(enfantId))
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Contact introuvable."));
    }

    private static String valider(SaisieContact saisie) {
        if (saisie.lien() == null || saisie.lien().isBlank() || saisie.lien().length() > 40 || saisie.nom() == null
                || saisie.nom().isBlank() || saisie.nom().length() > 80) {
            throw invalide("Indiquez le nom du contact et son lien avec l'enfant.");
        }
        try {
            return Telephones.normaliserE164(saisie.telephone());
        } catch (IllegalArgumentException erreur) {
            throw new ErreurMetier(CodeErreur.TELEPHONE_INVALIDE, "Saisissez un numéro mobile à 8 chiffres.");
        }
    }

    private FicheSanteVue vue(FicheSante fiche) {
        Contenu contenu = json.readValue(chiffrement.dechiffrer(CategorieDonnee.SANTE, fiche.contenuChiffre()), Contenu.class);
        return new FicheSanteVue(contenu.groupeSanguin(), contenu.groupeSanguinSurQr(), contenu.elements(), fiche.modifieLe());
    }

    private ContactVue vue(ContactUrgence c) {
        return new ContactVue(c.id(), c.lien(), chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, c.nomChiffre()),
                chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, c.telephoneChiffre()), c.visibleSurQr(), c.rang());
    }

    private static ErreurMetier invalide(String detail) {
        return new ErreurMetier(CodeErreur.REQUETE_INVALIDE, detail);
    }
}
