package bf.fasoguardian.famille.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Remplit le gabarit de la page publique QR, produit par la construction de {@code public-qr} (ADR 0006).
 * Trois balises y sont interprétées, sans imbrication d'une même balise : {@code fg-etat} (un seul état
 * est conservé), {@code fg-si} (bloc conditionnel) et {@code fg-pour} (répétition). Toute valeur insérée
 * à la place d'un marqueur {@code [[nom]]} est échappée.
 */
@Component
class GabaritPageQr {

    private static final Pattern ETAT = Pattern.compile("<fg-etat nom=\"([a-z]+)\"[^>]*>(.*?)</fg-etat>", Pattern.DOTALL);
    private static final Pattern SI = Pattern.compile("<fg-si condition=\"([a-zA-Z]+)\"[^>]*>(.*?)</fg-si>", Pattern.DOTALL);
    private static final Pattern POUR = Pattern.compile("<fg-pour liste=\"([a-zA-Z]+)\"[^>]*>(.*?)</fg-pour>", Pattern.DOTALL);
    private static final Pattern MARQUEUR = Pattern.compile("\\[\\[([a-zA-Z]+)\\]\\]");

    private final String gabarit;

    GabaritPageQr() {
        try (InputStream flux = GabaritPageQr.class.getResourceAsStream("/gabarits/page-qr.html")) {
            if (flux == null) {
                throw new IllegalStateException("Gabarit absent : lancer « npm run gabarit:qr » dans frontend/");
            }
            this.gabarit = new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    record Modele(String etat, Map<String, String> valeurs, Set<String> conditions,
            Map<String, List<Map<String, String>>> listes) {
    }

    String rendre(Modele modele) {
        String html = remplacer(ETAT, gabarit, bloc -> bloc.group(1).equals(modele.etat()) ? bloc.group(2) : "");
        html = remplacer(SI, html, bloc -> modele.conditions().contains(bloc.group(1)) ? bloc.group(2) : "");
        html = remplacer(POUR, html, bloc -> {
            StringBuilder repetitions = new StringBuilder();
            for (Map<String, String> element : modele.listes().getOrDefault(bloc.group(1), List.of())) {
                repetitions.append(inserer(bloc.group(2), element, true));
            }
            return repetitions.toString();
        });
        return inserer(html, modele.valeurs(), false);
    }

    /** Dans une répétition, les marqueurs absents de l'élément sont laissés au modèle général. */
    private static String inserer(String html, Map<String, String> valeurs, boolean laisserInconnus) {
        return remplacer(MARQUEUR, html, marqueur -> {
            String valeur = valeurs.get(marqueur.group(1));
            if (valeur == null) {
                return laisserInconnus ? marqueur.group() : "";
            }
            return HtmlUtils.htmlEscape(valeur, "UTF-8");
        });
    }

    private static String remplacer(Pattern motif, String texte, java.util.function.Function<Matcher, String> par) {
        Matcher correspondance = motif.matcher(texte);
        StringBuilder sortie = new StringBuilder(texte.length());
        while (correspondance.find()) {
            correspondance.appendReplacement(sortie, Matcher.quoteReplacement(par.apply(correspondance)));
        }
        return correspondance.appendTail(sortie).toString();
    }
}
