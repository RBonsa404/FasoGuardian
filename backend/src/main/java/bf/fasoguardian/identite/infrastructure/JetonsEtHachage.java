package bf.fasoguardian.identite.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import bf.fasoguardian.identite.application.Ports.EmetteurJetons;
import bf.fasoguardian.identite.application.Ports.HacheurMotDePasse;
import bf.fasoguardian.identite.application.Ports.JetonAcces;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Adaptateurs techniques de l'identité : mots de passe hachés en Argon2id, jetons JWT signés HS256 avec un
 * secret fourni par le coffre de secrets. Chaque jeton porte son usage ; un jeton d'un usage n'est jamais
 * accepté pour un autre.
 */
@Configuration
class JetonsEtHachage {

    static final String EMETTEUR = "fasoguardian";
    static final String USAGE = "usage";
    static final String USAGE_ACCES = "acces";
    static final String USAGE_PREUVE_TELEPHONE = "preuve-telephone";
    static final Duration DUREE_PREUVE = Duration.ofMinutes(15);

    @Bean
    HacheurMotDePasse hacheurMotDePasse() {
        Argon2PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return new HacheurMotDePasse() {
            @Override
            public String hacher(String motDePasse) {
                return argon2.encode(motDePasse);
            }

            @Override
            public boolean correspond(String motDePasse, String empreinte) {
                return argon2.matches(motDePasse, empreinte);
            }
        };
    }

    @Bean
    SecretKey cleJetons(@Value("${fasoguardian.jetons.secret:}") String base64) {
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException erreur) {
            throw new IllegalStateException("fasoguardian.jetons.secret n'est pas du base64 valide");
        }
        if (octets.length < 32) {
            throw new IllegalStateException("fasoguardian.jetons.secret doit faire au moins 32 octets (FG_JWT_SECRET)");
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }

    /** Décodeur des jetons d'accès présentés aux API : signature, expiration, émetteur et usage « acces ». */
    @Bean
    JwtDecoder decodeurJetonsAcces(SecretKey cleJetons) {
        return decodeur(cleJetons, USAGE_ACCES);
    }

    @Bean
    EmetteurJetons emetteurJetons(SecretKey cleJetons) {
        JwtEncoder encodeur = NimbusJwtEncoder.withSecretKey(cleJetons).build();
        JwtDecoder decodeurPreuves = decodeur(cleJetons, USAGE_PREUVE_TELEPHONE);
        return new EmetteurJetons() {
            @Override
            public JetonAcces acces(UUID utilisateurId, Set<String> roles, Instant maintenant, Duration duree) {
                JwtClaimsSet revendications = base(maintenant, duree, USAGE_ACCES)
                        .subject(utilisateurId.toString())
                        .claim("roles", List.copyOf(roles))
                        .build();
                return new JetonAcces(signer(revendications), duree.toSeconds());
            }

            @Override
            public String preuveTelephone(String telephoneE164, Instant maintenant) {
                return signer(base(maintenant, DUREE_PREUVE, USAGE_PREUVE_TELEPHONE).subject(telephoneE164).build());
            }

            @Override
            public Optional<String> lirePreuveTelephone(String jeton) {
                try {
                    return Optional.ofNullable(decodeurPreuves.decode(jeton).getSubject());
                } catch (JwtException | IllegalArgumentException | NullPointerException erreur) {
                    return Optional.empty();
                }
            }

            private String signer(JwtClaimsSet revendications) {
                return encodeur.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), revendications)).getTokenValue();
            }
        };
    }

    private static JwtClaimsSet.Builder base(Instant maintenant, Duration duree, String usage) {
        return JwtClaimsSet.builder()
                .issuer(EMETTEUR)
                .id(UUID.randomUUID().toString())
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(duree))
                .claim(USAGE, usage);
    }

    private static JwtDecoder decodeur(SecretKey cle, String usageAttendu) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(cle).macAlgorithm(MacAlgorithm.HS256).build();
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(EMETTEUR),
                jeton -> usageAttendu.equals(jeton.getClaimAsString(USAGE))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "usage inattendu", null))));
        return decodeur;
    }
}
