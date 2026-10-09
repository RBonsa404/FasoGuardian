package bf.fasoguardian.notifications.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.notifications.domaine.Notification;
import bf.fasoguardian.notifications.domaine.Notification.Urgence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotNotifications extends JpaRepository<Notification, UUID> {

    List<Notification> findByReferenceAndAccuseeLeIsNull(String reference);

    List<Notification> findByUrgenceAndAccuseeLeIsNullAndSmsEnvoyeLeIsNullAndCreeeLeBefore(Urgence urgence, Instant limite);

    long deleteByCreeeLeBefore(Instant limite);
}
