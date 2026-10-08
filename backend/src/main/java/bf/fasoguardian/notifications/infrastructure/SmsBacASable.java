package bf.fasoguardian.notifications.infrastructure;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import bf.fasoguardian.notifications.ServiceSms;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptateur SMS « bac à sable » : n'émet rien sur le réseau et conserve en mémoire les derniers messages,
 * pour le développement, la recette et les tests de bout en bout. Activé par
 * {@code fasoguardian.sms.adaptateur=bac-a-sable} ; sans adaptateur configuré, le serveur ne démarre pas.
 */
@Component
@ConditionalOnProperty(name = "fasoguardian.sms.adaptateur", havingValue = "bac-a-sable")
public class SmsBacASable implements ServiceSms {

    private static final int CAPACITE = 200;

    public record SmsEnvoye(String destinataire, String texte, Instant envoyeLe) {
    }

    private final Deque<SmsEnvoye> boite = new ArrayDeque<>();

    @Override
    public synchronized void envoyer(String destinataireE164, String texte) {
        if (boite.size() == CAPACITE) {
            boite.removeFirst();
        }
        boite.addLast(new SmsEnvoye(destinataireE164, texte, Instant.now()));
    }

    public synchronized Optional<SmsEnvoye> dernierPour(String destinataireE164) {
        return boite.reversed().stream().filter(sms -> sms.destinataire().equals(destinataireE164)).findFirst();
    }

    public synchronized List<SmsEnvoye> tous() {
        return List.copyOf(boite);
    }

    public synchronized void vider() {
        boite.clear();
    }
}
