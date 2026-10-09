package bf.fasoguardian.famille;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contact d'urgence d'un enfant, pour le partage temporaire de sa position (US-SEC-001). L'appelant a vérifié
 * le lien de tutelle ; le numéro ne lui est remis que pour l'envoi du lien.
 */
public interface ContactsUrgence {

    /** @param lien lien avec l'enfant, tel que le parent l'a saisi (« Oncle ») */
    record Contact(UUID id, String lien, String telephoneE164) {
    }

    Optional<Contact> contact(UUID enfantId, UUID contactId);

    /** Contacts d'urgence de l'enfant, dans l'ordre où le parent les a classés. */
    List<Contact> contactsDe(UUID enfantId);
}
