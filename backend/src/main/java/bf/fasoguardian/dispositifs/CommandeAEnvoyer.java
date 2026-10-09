package bf.fasoguardian.dispositifs;

/**
 * Une commande signée est prête à partir vers un bracelet. Le module qui tient la connexion au broker la
 * publie sur {@code fg/<numeroSerie>/cmd}.
 *
 * @param message message signé, tel qu'il doit être publié
 */
public record CommandeAEnvoyer(String numeroSerie, String message) {
}
