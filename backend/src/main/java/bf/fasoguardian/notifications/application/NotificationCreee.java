package bf.fasoguardian.notifications.application;

import java.util.UUID;

/** Une notification vient d'être enregistrée ; elle est acheminée après la validation de la transaction. */
record NotificationCreee(UUID notificationId) {
}
