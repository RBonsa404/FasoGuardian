package bf.fasoguardian.abonnements.infrastructure;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import bf.fasoguardian.abonnements.application.RedacteurRecu;
import bf.fasoguardian.abonnements.domaine.Facture;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reçu de paiement en PDF, une page A5. Il ne porte ni le nom de l'enfant ni le numéro complet du
 * portefeuille : le parent peut le transmettre sans rien divulguer d'autre que son paiement.
 */
@Component
class RedacteurRecuPdf implements RedacteurRecu {

    private static final Font TITRE = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font RUBRIQUE = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font TEXTE = new Font(Font.HELVETICA, 10);
    private static final Font NOTE = new Font(Font.HELVETICA, 8, Font.ITALIC);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final DateTimeFormatter dateHeure;

    RedacteurRecuPdf(@Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.dateHeure = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH).withZone(fuseau);
    }

    @Override
    public byte[] rediger(Facture facture) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5, 40, 40, 40, 40);
        PdfWriter.getInstance(document, sortie);
        document.addTitle("Reçu " + facture.numero());
        document.addCreator("FasoGuardian");
        document.open();

        document.add(new Paragraph("FasoGuardian", RUBRIQUE));
        document.add(new Paragraph("Reçu de paiement", TITRE));
        document.add(new Paragraph("N° " + facture.numero() + " · émis le " + dateHeure.format(facture.emiseLe())
                + " (heure de Ouagadougou)", TEXTE));

        Paragraph abonnement = new Paragraph("Abonnement", RUBRIQUE);
        abonnement.setSpacingBefore(16);
        document.add(abonnement);
        document.add(new Paragraph("Offre : " + facture.offreLibelle(), TEXTE));
        // La fin de période est exclue : le dernier jour couvert est la veille.
        document.add(new Paragraph("Période : du " + JOUR.format(facture.periodeDebut()) + " au "
                + JOUR.format(facture.periodeFin().minusDays(1)), TEXTE));

        Paragraph paiement = new Paragraph("Paiement", RUBRIQUE);
        paiement.setSpacingBefore(12);
        document.add(paiement);
        document.add(new Paragraph("Montant payé : " + montant(facture.montantFcfa()) + " FCFA", TEXTE));
        document.add(new Paragraph("Moyen : " + facture.moyen().libelle() + " " + facture.numeroMasque(), TEXTE));

        Paragraph note = new Paragraph("Paiement confirmé par l'opérateur de mobile money. Conservez ce reçu : son numéro "
                + "vous sera demandé pour toute réclamation.", NOTE);
        note.setSpacingBefore(20);
        document.add(note);
        document.close();
        return sortie.toByteArray();
    }

    /** « 2250 » s'écrit « 2 250 ». */
    private static String montant(int fcfa) {
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRENCH);
        symboles.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symboles).format(fcfa);
    }
}
