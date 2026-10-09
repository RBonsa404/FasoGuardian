package bf.fasoguardian.alertes.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import bf.fasoguardian.alertes.domaine.ActionAlerte;
import bf.fasoguardian.alertes.domaine.ActionAlerte.Type;
import bf.fasoguardian.alertes.domaine.Alerte;
import bf.fasoguardian.alertes.domaine.Alerte.Gravite;
import bf.fasoguardian.alertes.domaine.Alerte.Statut;
import bf.fasoguardian.alertes.infrastructure.DepotActions;
import bf.fasoguardian.alertes.infrastructure.DepotAlertes;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.famille.ContactsUrgence;
import bf.fasoguardian.famille.ContactsUrgence.Contact;
import bf.fasoguardian.identite.Telephones;
import bf.fasoguardian.notifications.ServiceSms;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sollicitation en cascade (US-SYS-005). Une alerte critique que les parents n'ont pas prise en charge dans le
 * délai défini est signalée aux contacts d'urgence de l'enfant ; si elle reste sans réponse au délai suivant,
 * elle l'est au point de contact institutionnel, s'il y en a un de configuré. Les messages ne portent ni le
 * nom de l'enfant ni sa position : ils demandent de joindre la famille. Chaque étape s'ajoute au journal de
 * l'alerte, que le parent voit.
 */
@Service
public class Cascade {

    private final DepotAlertes alertes;
    private final DepotActions actions;
    private final ContactsUrgence contacts;
    private final ServiceSms sms;
    private final JournalAudit journal;
    private final MeterRegistry metriques;
    private final Clock horloge;
    private final Duration delaiDuContact;
    private final Duration delaiDeLInstitution;
    private final Optional<String> institution;

    Cascade(DepotAlertes alertes, DepotActions actions, ContactsUrgence contacts, ServiceSms sms, JournalAudit journal,
            MeterRegistry metriques, Clock horloge,
            @Value("${fasoguardian.alertes.cascade.delai-contact:PT5M}") Duration delaiDuContact,
            @Value("${fasoguardian.alertes.cascade.delai-institution:PT10M}") Duration delaiDeLInstitution,
            @Value("${fasoguardian.alertes.cascade.contact-institutionnel:}") String institution) {
        this.alertes = alertes;
        this.actions = actions;
        this.contacts = contacts;
        this.sms = sms;
        this.journal = journal;
        this.metriques = metriques;
        this.horloge = horloge;
        this.delaiDuContact = delaiDuContact;
        this.delaiDeLInstitution = delaiDeLInstitution;
        // Sans point de contact convenu, la cascade s'arrête aux contacts d'urgence de la famille.
        this.institution = institution.isBlank() ? Optional.empty() : Optional.of(Telephones.normaliserE164(institution));
    }

    @Scheduled(fixedDelayString = "${fasoguardian.alertes.cascade.cadence:PT30S}")
    @SchedulerLock(name = "alertes-cascade", lockAtMostFor = "PT2M")
    @Transactional
    public void passer() {
        solliciter();
    }

    /** @return le nombre de sollicitations faites à ce passage */
    @Transactional
    public int solliciter() {
        Instant maintenant = horloge.instant();
        int sollicitations = 0;
        for (Alerte alerte : alertes.findByStatutAndGraviteAndOuverteLeBefore(Statut.OUVERTE, Gravite.CRITIQUE,
                maintenant.minus(delaiDuContact))) {
            Optional<ActionAlerte> contact = actions.findFirstByAlerteIdAndType(alerte.id(), Type.CONTACT_SOLLICITE);
            if (contact.isEmpty()) {
                solliciterLesContacts(alerte, maintenant);
                sollicitations++;
            } else if (institution.isPresent() && !contact.get().effectueeLe().isAfter(maintenant.minus(delaiDeLInstitution))
                    && actions.findFirstByAlerteIdAndType(alerte.id(), Type.INSTITUTION_SOLLICITEE).isEmpty()) {
                solliciterLInstitution(alerte, maintenant);
                sollicitations++;
            }
        }
        return sollicitations;
    }

    private void solliciterLesContacts(Alerte alerte, Instant maintenant) {
        List<Contact> prevenus = contacts.contactsDe(alerte.enfantId());
        prevenus.forEach(contact -> sms.envoyer(contact.telephoneE164(), "FasoGuardian : une alerte concerne l'enfant dont vous "
                + "êtes le contact d'urgence, et ses parents n'ont pas encore répondu. Joignez-les ou rejoignez l'enfant sans attendre."));
        // L'étape est notée même sans contact enregistré : le journal dit alors que personne n'a pu être joint.
        actions.save(alerte.solliciter(Type.CONTACT_SOLLICITE, prevenus.isEmpty() ? "Aucun contact d'urgence enregistré"
                : prevenus.size() + (prevenus.size() > 1 ? " contacts d'urgence prévenus par SMS" : " contact d'urgence prévenu par SMS"),
                maintenant));
        journal.consigner(null, "SYSTEME", "CASCADE_CONTACT_SOLLICITE", "ALERTE", alerte.id().toString(), Resultat.SUCCES);
        metriques.counter("fasoguardian.alertes.cascade", "etape", "CONTACT").increment();
    }

    private void solliciterLInstitution(Alerte alerte, Instant maintenant) {
        sms.envoyer(institution.orElseThrow(), "FasoGuardian : une alerte critique reste sans réponse de la famille et de ses contacts. "
                + "Référence " + reference(alerte) + ". Appliquez la procédure convenue.");
        actions.save(alerte.solliciter(Type.INSTITUTION_SOLLICITEE, "Point de contact institutionnel prévenu par SMS", maintenant));
        journal.consigner(null, "SYSTEME", "CASCADE_INSTITUTION_SOLLICITEE", "ALERTE", alerte.id().toString(), Resultat.SUCCES);
        metriques.counter("fasoguardian.alertes.cascade", "etape", "INSTITUTION").increment();
    }

    /** Référence courte de l'alerte, sans rien qui identifie l'enfant. */
    private static String reference(Alerte alerte) {
        return "ALR-" + alerte.id().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}
