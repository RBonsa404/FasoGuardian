package bf.fasoguardian.abonnements.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Palier commercial (FG-DOC-11 §3.3) : son prix et ce qu'il ouvre. */
@Entity
@Table(schema = "abonnements", name = "offre")
public class Offre {

    @Id
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Column(name = "prix_fcfa", nullable = false)
    private int prixFcfa;

    @Column(name = "intervalle_s", nullable = false)
    private int intervalleS;

    @Column(name = "zones_maximum", nullable = false)
    private int zonesMaximum;

    @Column(name = "historique_jours", nullable = false)
    private int historiqueJours;

    @Column(nullable = false)
    private boolean souscriptible;

    @Column(nullable = false)
    private int rang;

    protected Offre() {
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    public int prixFcfa() {
        return prixFcfa;
    }

    public int intervalleS() {
        return intervalleS;
    }

    public int zonesMaximum() {
        return zonesMaximum;
    }

    public int historiqueJours() {
        return historiqueJours;
    }

    public boolean souscriptible() {
        return souscriptible;
    }

    public int rang() {
        return rang;
    }
}
