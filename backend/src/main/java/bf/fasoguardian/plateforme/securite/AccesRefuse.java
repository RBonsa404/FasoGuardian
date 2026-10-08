package bf.fasoguardian.plateforme.securite;

import java.util.UUID;

/**
 * Publié lorsqu'un utilisateur authentifié se voit refuser une ressource. Le module audit l'inscrit au
 * journal. Le chemin ne porte que des identifiants techniques, jamais les paramètres de la requête.
 */
public record AccesRefuse(UUID acteurId, String role, String methode, String chemin) {
}
