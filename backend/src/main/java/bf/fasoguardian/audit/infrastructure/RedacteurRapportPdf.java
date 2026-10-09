package bf.fasoguardian.audit.infrastructure;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import bf.fasoguardian.audit.application.Conformite.Duree;
import bf.fasoguardian.audit.application.Conformite.Purge;
import bf.fasoguardian.audit.application.RedacteurRapport;
import com.lowagie.text.Document;
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
 * Rapport mensuel de conformité en PDF, une à deux pages A4. Il ne contient aucune donnée personnelle :
 * seulement des durées, des décomptes et des dates d'exécution.
 */
@Component
class RedacteurRapportPdf implements RedacteurRapport {

    private static final Font TITRE = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font RUBRIQUE = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font TEXTE = new Font(Font.HELVETICA, 10);
    private static final Font NOTE = new Font(Font.HELVETICA, 8, Font.ITALIC);
    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final DateTimeFormatter dateHeure;

    RedacteurRapportPdf(@Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.dateHeure = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH).withZone(fuseau);
    }

    @Override
    public byte[] rediger(Contenu contenu) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(document, sortie);
        document.addTitle("Rapport de conformité " + contenu.mois());
        document.addCreator("FasoGuardian");
        document.open();

        document.add(new Paragraph("Rapport de conformité — " + MOIS.format(contenu.mois().atDay(1)), TITRE));
        document.add(new Paragraph("Protection des données personnelles (loi n° 001-2021/AN) · établi le "
                + dateHeure.format(contenu.etabliLe()) + " (heure de Ouagadougou)", TEXTE));

        rubrique(document, "Analyse d'impact (AIPD)");
        if (contenu.aipd().documentee()) {
            document.add(new Paragraph("Documentée : référence " + contenu.aipd().reference() + ", validée le "
                    + JOUR.format(contenu.aipd().valideeLe()) + ". Délégué à la protection des données : "
                    + contenu.aipd().delegue() + ".", TEXTE));
        } else {
            document.add(new Paragraph("Non documentée : la mise en production avec des données réelles est bloquée.", TEXTE));
        }

        rubrique(document, "Durées de conservation appliquées");
        PdfPTable durees = tableau(new float[] {4, 5, 3}, "Donnée", "Durée", "Mécanisme");
        for (Duree duree : contenu.conservation()) {
            durees.addCell(new Phrase(duree.donnee(), TEXTE));
            durees.addCell(new Phrase(duree.duree(), TEXTE));
            durees.addCell(new Phrase(duree.mecanisme(), TEXTE));
        }
        document.add(durees);

        rubrique(document, "Purges automatiques");
        document.add(new Paragraph("Jours du mois où les purges ont tourné : " + contenu.joursDePurge() + " sur "
                + contenu.joursDuMois() + ".", TEXTE));
        if (contenu.purges().isEmpty()) {
            document.add(new Paragraph("Aucune purge n'a été exécutée sur la période.", TEXTE));
        } else {
            PdfPTable purges = tableau(new float[] {4, 2, 2, 4}, "Traitement", "Exécutions", "Éléments supprimés", "Dernière exécution");
            for (Purge purge : contenu.purges()) {
                purges.addCell(new Phrase(purge.traitement(), TEXTE));
                purges.addCell(new Phrase(Long.toString(purge.executions()), TEXTE));
                purges.addCell(new Phrase(Long.toString(purge.elements()), TEXTE));
                purges.addCell(new Phrase(dateHeure.format(purge.derniereExecution()), TEXTE));
            }
            document.add(purges);
        }

        rubrique(document, "Exercice des droits des personnes");
        document.add(new Paragraph("Demandes d'accès servies : " + contenu.accesServis() + ".", TEXTE));
        document.add(new Paragraph("Demandes d'effacement reçues : " + contenu.effacementsRecus() + " ; exécutées : "
                + contenu.effacementsExecutes() + ", dont hors du délai de trente jours : " + contenu.effacementsHorsDelai()
                + " ; en attente à ce jour : " + contenu.effacementsEnAttente() + ".", TEXTE));

        rubrique(document, "Journal d'audit");
        document.add(new Paragraph(contenu.entreeAlteree() == null
                ? "Chaîne d'empreintes vérifiée à l'établissement de ce rapport : intègre."
                : "Chaîne d'empreintes rompue à l'entrée " + contenu.entreeAlteree() + " : le journal a été altéré.", TEXTE));

        Paragraph note = new Paragraph("Ce rapport ne contient aucune donnée personnelle. Les durées de conservation sont celles "
                + "soumises à la Commission de l'informatique et des libertés dans l'analyse d'impact.", NOTE);
        note.setSpacingBefore(14);
        document.add(note);
        document.close();
        return sortie.toByteArray();
    }

    private static void rubrique(Document document, String titre) {
        Paragraph paragraphe = new Paragraph(titre, RUBRIQUE);
        paragraphe.setSpacingBefore(12);
        paragraphe.setSpacingAfter(4);
        document.add(paragraphe);
    }

    private static PdfPTable tableau(float[] largeurs, String... entetes) {
        PdfPTable tableau = new PdfPTable(largeurs);
        tableau.setWidthPercentage(100);
        for (String entete : entetes) {
            tableau.addCell(new PdfPCell(new Phrase(entete, RUBRIQUE)));
        }
        return tableau;
    }
}
