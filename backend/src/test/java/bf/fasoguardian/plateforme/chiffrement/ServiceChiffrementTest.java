package bf.fasoguardian.plateforme.chiffrement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement.DonneeIllisibleException;
import org.junit.jupiter.api.Test;

class ServiceChiffrementTest {

    private static final String EMPREINTE = cle();

    private final Map<CategorieDonnee, Map<Integer, String>> cles = clesV1();
    private final ServiceChiffrement service = new ServiceChiffrement(new ProprietesChiffrement(cles, EMPREINTE));

    @Test
    void uneDonneeChiffreeEstIllisibleEtSeDechiffre() {
        byte[] chiffre = service.chiffrerTexte(CategorieDonnee.SANTE, "Allergie à l'arachide");

        assertThat(new String(chiffre)).doesNotContain("arachide");
        assertThat(service.dechiffrerTexte(CategorieDonnee.SANTE, chiffre)).isEqualTo("Allergie à l'arachide");
    }

    @Test
    void deuxChiffrementsDeLaMemeValeurDifferent() {
        assertThat(service.chiffrerTexte(CategorieDonnee.TELEPHONE, "+22670123456"))
                .isNotEqualTo(service.chiffrerTexte(CategorieDonnee.TELEPHONE, "+22670123456"));
    }

    @Test
    void uneDonneeAltereeEstRejetee() {
        byte[] chiffre = service.chiffrerTexte(CategorieDonnee.PIECE_KYC, "pièce");
        chiffre[chiffre.length - 1] ^= 1;

        assertThatThrownBy(() -> service.dechiffrer(CategorieDonnee.PIECE_KYC, chiffre))
                .isInstanceOf(DonneeIllisibleException.class);
    }

    @Test
    void uneDonneeNePeutPasChangerDeCategorie() {
        Map<CategorieDonnee, Map<Integer, String>> memeCle = clesV1();
        memeCle.put(CategorieDonnee.SANTE, memeCle.get(CategorieDonnee.TELEPHONE));
        ServiceChiffrement partage = new ServiceChiffrement(new ProprietesChiffrement(memeCle, EMPREINTE));
        byte[] chiffre = partage.chiffrerTexte(CategorieDonnee.TELEPHONE, "+22670123456");

        assertThatThrownBy(() -> partage.dechiffrer(CategorieDonnee.SANTE, chiffre))
                .isInstanceOf(DonneeIllisibleException.class);
    }

    @Test
    void apresRotationLesAnciennesDonneesRestentLisiblesEtSontSignaleesARechiffrer() {
        byte[] ancien = service.chiffrerTexte(CategorieDonnee.SANTE, "asthme");
        Map<CategorieDonnee, Map<Integer, String>> apresRotation = new EnumMap<>(cles);
        Map<Integer, String> sante = new HashMap<>(cles.get(CategorieDonnee.SANTE));
        sante.put(2, cle());
        apresRotation.put(CategorieDonnee.SANTE, sante);
        ServiceChiffrement tourne = new ServiceChiffrement(new ProprietesChiffrement(apresRotation, EMPREINTE));

        assertThat(tourne.dechiffrerTexte(CategorieDonnee.SANTE, ancien)).isEqualTo("asthme");
        assertThat(tourne.aRechiffrer(CategorieDonnee.SANTE, ancien)).isTrue();
        assertThat(tourne.aRechiffrer(CategorieDonnee.SANTE, tourne.chiffrerTexte(CategorieDonnee.SANTE, "asthme")))
                .isFalse();
    }

    @Test
    void lEmpreinteEstDeterministeEtPropreALaCategorie() {
        assertThat(service.empreinte(CategorieDonnee.TELEPHONE, "+22670123456"))
                .isEqualTo(service.empreinte(CategorieDonnee.TELEPHONE, "+22670123456"))
                .hasSize(64)
                .isNotEqualTo(service.empreinte(CategorieDonnee.TELEPHONE, "+22670123457"))
                .isNotEqualTo(service.empreinte(CategorieDonnee.IMEI, "+22670123456"));
    }

    @Test
    void leServiceRefuseDeDemarrerSansCleOuAvecUneCleTropCourte() {
        Map<CategorieDonnee, Map<Integer, String>> incomplet = clesV1();
        incomplet.remove(CategorieDonnee.IMEI);
        assertThatThrownBy(() -> new ServiceChiffrement(new ProprietesChiffrement(incomplet, EMPREINTE)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("IMEI");

        Map<CategorieDonnee, Map<Integer, String>> faible = clesV1();
        faible.put(CategorieDonnee.SANTE, Map.of(1, Base64.getEncoder().encodeToString(new byte[16])));
        assertThatThrownBy(() -> new ServiceChiffrement(new ProprietesChiffrement(faible, EMPREINTE)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 octets");
    }

    private static Map<CategorieDonnee, Map<Integer, String>> clesV1() {
        Map<CategorieDonnee, Map<Integer, String>> cles = new EnumMap<>(CategorieDonnee.class);
        for (CategorieDonnee categorie : CategorieDonnee.values()) {
            cles.put(categorie, Map.of(1, cle()));
        }
        return cles;
    }

    private static String cle() {
        byte[] octets = new byte[32];
        new SecureRandom().nextBytes(octets);
        return Base64.getEncoder().encodeToString(octets);
    }
}
