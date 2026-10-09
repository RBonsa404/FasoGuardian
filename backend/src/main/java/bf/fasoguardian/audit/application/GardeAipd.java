package bf.fasoguardian.audit.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Garde-fou de l'analyse d'impact (US-ADM-003) : sous le profil {@code prod}, le serveur refuse de démarrer
 * tant que l'AIPD n'est pas documentée, c'est-à-dire tant que sa référence, sa date de validation et le
 * contact du délégué à la protection des données ne sont pas renseignés. Aucune donnée réelle ne peut donc
 * être traitée avant.
 */
@Component
public class GardeAipd {

    /** @param documentee référence, date de validation passée et délégué sont tous renseignés */
    public record Etat(boolean documentee, String reference, LocalDate valideeLe, String delegue) {
    }

    private final Etat etat;

    GardeAipd(@Value("${fasoguardian.conformite.aipd.reference:}") String reference,
            @Value("${fasoguardian.conformite.aipd.validee-le:}") String valideeLe,
            @Value("${fasoguardian.conformite.delegue:}") String delegue, Environment environnement, Clock horloge) {
        LocalDate date = lire(valideeLe);
        boolean documentee = !reference.isBlank() && !delegue.isBlank() && date != null
                && !date.isAfter(LocalDate.now(horloge));
        this.etat = new Etat(documentee, reference.isBlank() ? null : reference.strip(), date,
                delegue.isBlank() ? null : delegue.strip());
        if (!documentee && environnement.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Mise en production bloquée : l'analyse d'impact (AIPD) n'est pas documentée. "
                    + "Renseignez FG_AIPD_REFERENCE, FG_AIPD_VALIDEE_LE (AAAA-MM-JJ) et FG_DELEGUE_PROTECTION_DONNEES.");
        }
    }

    public Etat etat() {
        return etat;
    }

    private static LocalDate lire(String date) {
        try {
            return date.isBlank() ? null : LocalDate.parse(date.strip());
        } catch (DateTimeParseException erreur) {
            throw new IllegalStateException("Date de validation de l'AIPD illisible : attendue au format AAAA-MM-JJ "
                    + "(fasoguardian.conformite.aipd.validee-le)");
        }
    }
}
