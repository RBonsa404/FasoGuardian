package bf.fasoguardian.alertes.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Alerte concernant un enfant et son cycle de vie (FG-DOC-07 §5). Chaque transition rend l'action à inscrire
 * au journal d'acquittement : il n'existe pas de changement d'état sans trace.
 */
@Entity
@Table(schema = "alertes", name = "alerte")
public class Alerte {

    public enum Type {
        SOS(Gravite.CRITIQUE),
        RETRAIT(Gravite.CRITIQUE),
        /** Signalement déclenché par le parent lui-même (US-PAR-010). */
        SIGNALEMENT(Gravite.CRITIQUE),
        SORTIE_ZONE(Gravite.IMPORTANTE),
        BATTERIE_CRITIQUE(Gravite.IMPORTANTE),
        CHUTE(Gravite.IMPORTANTE);

        private final Gravite gravite;

        Type(Gravite gravite) {
            this.gravite = gravite;
        }

        public Gravite gravite() {
            return gravite;
        }
    }

    public enum Gravite {
        CRITIQUE,
        IMPORTANTE
    }

    public enum Statut {
        OUVERTE,
        ACQUITTEE,
        ESCALADEE,
        LEVEE,
        FAUSSE_ALERTE;

        public boolean close() {
            return this == LEVEE || this == FAUSSE_ALERTE;
        }
    }

    /** Transition refusée par la machine à états. */
    public static class TransitionIllegaleException extends RuntimeException {
        public TransitionIllegaleException(Statut statut, String action) {
            super("Action « " + action + " » impossible pour une alerte au statut " + statut);
        }
    }

    /** Alerte nouvelle et son action d'ouverture, à enregistrer ensemble. */
    public record Ouverture(Alerte alerte, ActionAlerte action) {
    }

    @Id
    private UUID id;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gravite gravite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "ouverte_le", nullable = false)
    private Instant ouverteLe;

    @Column(name = "close_le")
    private Instant closeLe;

    @Column(name = "zone_id")
    private UUID zoneId;

    private String libelle;

    private Double latitude;

    private Double longitude;

    @Version
    private long version;

    protected Alerte() {
    }

    /**
     * @param acteurId tuteur qui signale, ou {@code null} pour un événement détecté par le système
     * @param libelle  nom de la zone pour une sortie de zone, sinon {@code null}
     */
    public static Ouverture ouvrir(UUID enfantId, Type type, UUID acteurId, UUID zoneId, String libelle, Double latitude,
            Double longitude, Instant maintenant) {
        Alerte alerte = new Alerte();
        alerte.id = UUID.randomUUID();
        alerte.enfantId = enfantId;
        alerte.type = type;
        alerte.gravite = type.gravite();
        alerte.statut = Statut.OUVERTE;
        alerte.ouverteLe = maintenant;
        alerte.zoneId = zoneId;
        alerte.libelle = libelle;
        alerte.latitude = latitude;
        alerte.longitude = longitude;
        return new Ouverture(alerte, new ActionAlerte(alerte.id, ActionAlerte.Type.OUVERTURE, acteurId, null, maintenant));
    }

    /** Un tuteur prend l'alerte en charge. */
    public ActionAlerte acquitter(UUID tuteurId, Instant maintenant) {
        exiger(statut == Statut.OUVERTE, "prendre en charge");
        statut = Statut.ACQUITTEE;
        return new ActionAlerte(id, ActionAlerte.Type.ACQUITTEMENT, tuteurId, null, maintenant);
    }

    public ActionAlerte classerFausseAlerte(UUID tuteurId, String motif, Instant maintenant) {
        exiger(statut == Statut.OUVERTE || statut == Statut.ACQUITTEE, "classer en fausse alerte");
        // Le motif est validé avant tout changement d'état : un refus laisse l'alerte intacte.
        ActionAlerte action = new ActionAlerte(id, ActionAlerte.Type.FAUSSE_ALERTE, tuteurId, exigerMotif(motif), maintenant);
        statut = Statut.FAUSSE_ALERTE;
        closeLe = maintenant;
        return action;
    }

    /** Disparition confirmée : seul un tuteur qui a pris l'alerte en charge peut l'escalader. */
    public ActionAlerte escalader(UUID tuteurId, Instant maintenant) {
        exiger(statut == Statut.ACQUITTEE, "escalader");
        statut = Statut.ESCALADEE;
        return new ActionAlerte(id, ActionAlerte.Type.ESCALADE, tuteurId, null, maintenant);
    }

    public ActionAlerte lever(UUID tuteurId, String motif, Instant maintenant) {
        exiger(statut == Statut.ACQUITTEE || statut == Statut.ESCALADEE, "lever");
        // Le motif est validé avant tout changement d'état : un refus laisse l'alerte intacte.
        ActionAlerte action = new ActionAlerte(id, ActionAlerte.Type.LEVEE, tuteurId, exigerMotif(motif), maintenant);
        statut = Statut.LEVEE;
        closeLe = maintenant;
        return action;
    }

    /**
     * La cause a disparu d'elle-même (retour dans la zone, bracelet rechargé) : le système clôt l'alerte, sauf
     * si elle a été escaladée, auquel cas seul un tuteur peut la lever (ADR 0011).
     */
    public ActionAlerte resoudre(String motif, Instant maintenant) {
        exiger(statut == Statut.OUVERTE || statut == Statut.ACQUITTEE, "résoudre");
        // Le motif est validé avant tout changement d'état : un refus laisse l'alerte intacte.
        ActionAlerte action = new ActionAlerte(id, ActionAlerte.Type.RESOLUTION, null, exigerMotif(motif), maintenant);
        statut = Statut.LEVEE;
        closeLe = maintenant;
        return action;
    }

    /**
     * Faute de réponse des parents, un tiers est sollicité (US-SYS-005). L'alerte ne change pas d'état : elle
     * attend toujours d'être prise en charge.
     *
     * @param etape {@link ActionAlerte.Type#CONTACT_SOLLICITE} ou {@link ActionAlerte.Type#INSTITUTION_SOLLICITEE}
     */
    public ActionAlerte solliciter(ActionAlerte.Type etape, String motif, Instant maintenant) {
        exiger(statut == Statut.OUVERTE, "solliciter un tiers");
        if (etape != ActionAlerte.Type.CONTACT_SOLLICITE && etape != ActionAlerte.Type.INSTITUTION_SOLLICITEE) {
            throw new IllegalArgumentException("Étape de sollicitation inconnue : " + etape);
        }
        return new ActionAlerte(id, etape, null, exigerMotif(motif), maintenant);
    }

    private void exiger(boolean permis, String action) {
        if (!permis) {
            throw new TransitionIllegaleException(statut, action);
        }
    }

    private static String exigerMotif(String motif) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Un motif est obligatoire");
        }
        String propre = motif.strip();
        return propre.substring(0, Math.min(ActionAlerte.LONGUEUR_MOTIF, propre.length()));
    }

    public UUID id() {
        return id;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public Type type() {
        return type;
    }

    public Gravite gravite() {
        return gravite;
    }

    public Statut statut() {
        return statut;
    }

    public Instant ouverteLe() {
        return ouverteLe;
    }

    public Instant closeLe() {
        return closeLe;
    }

    public UUID zoneId() {
        return zoneId;
    }

    public String libelle() {
        return libelle;
    }

    public Double latitude() {
        return latitude;
    }

    public Double longitude() {
        return longitude;
    }
}
