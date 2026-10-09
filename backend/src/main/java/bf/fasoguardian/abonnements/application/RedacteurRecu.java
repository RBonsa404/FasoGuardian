package bf.fasoguardian.abonnements.application;

import bf.fasoguardian.abonnements.domaine.Facture;

/** Mise en page du reçu d'un paiement. */
public interface RedacteurRecu {

    byte[] rediger(Facture facture);
}
