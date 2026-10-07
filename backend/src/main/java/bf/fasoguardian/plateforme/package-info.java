/**
 * Socle technique partagé par les neuf modules métier : format d'erreur RFC 9457,
 * configuration de sécurité HTTP, OpenAPI et traitements planifiés. Ne contient
 * aucune règle métier (voir docs/adr/0003-module-plateforme.md).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Plateforme",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package bf.fasoguardian.plateforme;
