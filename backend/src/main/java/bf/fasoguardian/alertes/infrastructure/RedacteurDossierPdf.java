package bf.fasoguardian.alertes.infrastructure;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import bf.fasoguardian.alertes.application.RedacteurDossier;
import bf.fasoguardian.famille.DossiersEnfants.Identification;
import bf.fasoguardian.telemetrie.TrajetsRecents.Point;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dossier de signalement en PDF, une à deux pages A4 lisibles sans FasoGuardian : identité de l'enfant,
 * signes distinctifs, informations médicales critiques, dernière position et trajet des deux dernières heures.
 */
@Component
class RedacteurDossierPdf implements RedacteurDossier {

    private static final Font TITRE = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font RUBRIQUE = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font TEXTE = new Font(Font.HELVETICA, 10);
    private static final Font NOTE = new Font(Font.HELVETICA, 8, Font.ITALIC);
    /** Au-delà, le tableau du trajet ne garde que les positions les plus récentes. */
    private static final int LIGNES_DE_TRAJET = 40;

    private final DateTimeFormatter dateHeure;
    private final DateTimeFormatter heure;
    private final ZoneId fuseau;

    RedacteurDossierPdf(@Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.fuseau = fuseau;
        this.dateHeure = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH).withZone(fuseau);
        this.heure = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRENCH).withZone(fuseau);
    }

    @Override
    public byte[] rediger(Contenu contenu) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(document, sortie);
        document.addTitle("Dossier de signalement " + contenu.reference());
        document.addCreator("FasoGuardian");
        document.open();

        document.add(new Paragraph("Dossier de signalement — enfant disparu", TITRE));
        document.add(new Paragraph("Référence " + contenu.reference() + " · établi le " + dateHeure.format(contenu.etabliLe())
                + " (heure de Ouagadougou)", TEXTE));
        document.add(new Paragraph("Établi à la demande d'un tuteur légal de l'enfant, dont le lien a été vérifié par "
                + "FasoGuardian, pour remise aux forces de sécurité.", TEXTE));

        Identification enfant = contenu.enfant();
        rubrique(document, "Enfant");
        ligne(document, "Nom et prénom", enfant.nom().toUpperCase(Locale.FRENCH) + " " + enfant.prenom());
        ligne(document, "Né(e) le", enfant.dateNaissance().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " ("
                + Period.between(enfant.dateNaissance(), LocalDate.ofInstant(contenu.etabliLe(), fuseau)).getYears() + " ans)");
        ligne(document, "Taille", enfant.tailleCm() == null ? null : enfant.tailleCm() + " cm");
        ligne(document, "Signes distinctifs", enfant.signesDistinctifs());
        ligne(document, "École", enfant.ecole());
        ligne(document, "Quartier", enfant.quartier());

        rubrique(document, "Informations médicales à connaître");
        if (enfant.informationsMedicales().isEmpty()) {
            document.add(new Paragraph("Aucune information médicale critique n'a été renseignée par la famille.", TEXTE));
        }
        for (String information : enfant.informationsMedicales()) {
            document.add(new Paragraph("• " + information, TEXTE));
        }

        rubrique(document, "Alerte");
        ligne(document, "Nature", contenu.typeAlerte());
        ligne(document, "Déclenchée le", dateHeure.format(contenu.alerteOuverteLe()));
        for (String entree : contenu.journal()) {
            document.add(new Paragraph("• " + entree, TEXTE));
        }

        rubrique(document, "Dernière position connue");
        List<Point> trajet = contenu.trajet();
        if (trajet.isEmpty()) {
            document.add(new Paragraph("Le bracelet n'a transmis aucune position au cours des deux dernières heures.", TEXTE));
        } else {
            Point derniere = trajet.get(trajet.size() - 1);
            ligne(document, "Relevée le", dateHeure.format(derniere.mesureeLe()));
            ligne(document, "Coordonnées (WGS 84)", coordonnees(derniere));
            ligne(document, "Précision", (derniere.approximative() ? "approximative, " : "") + "± " + derniere.precisionM() + " m");

            rubrique(document, "Trajet des deux dernières heures (" + trajet.size() + " positions)");
            PdfPTable tableau = new PdfPTable(new float[] {2, 5, 2});
            tableau.setWidthPercentage(100);
            tableau.setSpacingBefore(4);
            for (String entete : new String[] {"Heure", "Coordonnées (latitude, longitude)", "Précision"}) {
                PdfPCell cellule = new PdfPCell(new Phrase(entete, RUBRIQUE));
                cellule.setHorizontalAlignment(Element.ALIGN_LEFT);
                tableau.addCell(cellule);
            }
            for (Point point : trajet.subList(Math.max(0, trajet.size() - LIGNES_DE_TRAJET), trajet.size())) {
                tableau.addCell(new Phrase(heure.format(point.mesureeLe()), TEXTE));
                tableau.addCell(new Phrase(coordonnees(point), TEXTE));
                tableau.addCell(new Phrase("± " + point.precisionM() + " m", TEXTE));
            }
            document.add(tableau);
        }

        Paragraph note = new Paragraph("Ce dossier contient des données personnelles d'un enfant. Il est destiné aux seules "
                + "forces de sécurité saisies de sa disparition. Un faux signalement est puni par la loi.", NOTE);
        note.setSpacingBefore(14);
        document.add(note);
        document.close();
        return sortie.toByteArray();
    }

    private static void rubrique(Document document, String titre) {
        Paragraph paragraphe = new Paragraph(titre, RUBRIQUE);
        paragraphe.setSpacingBefore(12);
        paragraphe.setSpacingAfter(2);
        document.add(paragraphe);
    }

    /** Une ligne « libellé : valeur » ; la valeur absente est dite non renseignée plutôt que tue. */
    private static void ligne(Document document, String libelle, String valeur) {
        document.add(new Paragraph(libelle + " : " + (valeur == null || valeur.isBlank() ? "non renseigné" : valeur), TEXTE));
    }

    private static String coordonnees(Point point) {
        return String.format(Locale.ROOT, "%.5f, %.5f", point.latitude(), point.longitude());
    }
}
