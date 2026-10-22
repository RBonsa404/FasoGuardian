package bf.fasoguardian.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Le journal d'audit de la console (écran 70) met chaque action en mots : une action consignée par le serveur et
 * absente du dictionnaire de la console s'afficherait sous son seul code. Les actions sont relevées dans les sources,
 * aux appels de {@code consigner} comme aux méthodes d'aide qui reçoivent l'action en paramètre.
 */
class ActionsAuditLibelleesTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final Path CONSOLE = Path.of("..", "frontend", "projects", "console", "src", "app", "admin", "actions-audit.ts");

    private static final Pattern CONSTANTE = Pattern.compile("static final String (\\w+) = \"([A-Z][A-Z0-9_]+)\";");
    private static final Pattern AIDE = Pattern.compile("private \\w+ (\\w+)\\(([^)]*)\\)\\s*\\{");

    @Test
    void chaqueActionConsigneeALibelleDansLaConsole() throws IOException {
        String dictionnaire = Files.readString(CONSOLE);
        assertThat(actionsConsignees()).isNotEmpty().filteredOn(action -> !dictionnaire.contains("  " + action + ": $localize")).isEmpty();
    }

    private static Set<String> actionsConsignees() throws IOException {
        List<String> fichiers = new ArrayList<>();
        try (Stream<Path> chemins = Files.walk(SOURCES)) {
            chemins.filter(chemin -> chemin.toString().endsWith(".java")).forEach(chemin -> fichiers.add(lire(chemin)));
        }
        Map<String, String> constantes = new HashMap<>();
        fichiers.forEach(source -> CONSTANTE.matcher(source).results().forEach(m -> constantes.put(m.group(1), m.group(2))));

        Set<String> actions = new TreeSet<>();
        for (String source : fichiers) {
            // Appels directs : consigner(acteur, rôle, action, typeCible, cible, résultat).
            relever(source, "\\.consigner", 2, constantes, actions);
            // Méthodes d'aide du même fichier dont un paramètre s'appelle « action ».
            Matcher aide = AIDE.matcher(source);
            while (aide.find()) {
                String[] parametres = aide.group(2).split(",");
                for (int i = 0; i < parametres.length; i++) {
                    if (parametres[i].trim().endsWith(" action")) {
                        // Appel de l'aide elle-même, sans qualificatif : « journal.consigner » n'en est pas un.
                        relever(source, "(?<![.\\w])" + aide.group(1), i, constantes, actions);
                    }
                }
            }
        }
        return actions;
    }

    /** Relève l'argument de rang donné des appels reconnus par le motif, lorsqu'il est un littéral ou une constante. */
    private static void relever(String source, String methode, int rang, Map<String, String> constantes, Set<String> actions) {
        Matcher appel = Pattern.compile(methode + "\\(").matcher(source);
        while (appel.find()) {
            List<String> arguments = arguments(source, appel.end());
            if (arguments.size() <= rang) {
                continue;
            }
            String argument = arguments.get(rang).trim();
            Matcher litteral = Pattern.compile("^\"([A-Z][A-Z0-9_]+)\"$").matcher(argument);
            if (litteral.find()) {
                actions.add(litteral.group(1));
            } else {
                String nom = argument.substring(argument.lastIndexOf('.') + 1);
                if (constantes.containsKey(nom)) {
                    actions.add(constantes.get(nom));
                }
            }
        }
    }

    /** Arguments de premier niveau d'un appel, à partir de la parenthèse ouvrante. */
    private static List<String> arguments(String source, int debut) {
        List<String> arguments = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        int profondeur = 0;
        boolean chaine = false;
        for (int i = debut; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' && source.charAt(i - 1) != '\\') {
                chaine = !chaine;
            } else if (!chaine && (c == '(' || c == '{' || c == '[')) {
                profondeur++;
            } else if (!chaine && (c == ')' || c == '}' || c == ']')) {
                if (profondeur == 0) {
                    arguments.add(courant.toString());
                    return arguments;
                }
                profondeur--;
            } else if (!chaine && c == ',' && profondeur == 0) {
                arguments.add(courant.toString());
                courant.setLength(0);
                continue;
            }
            courant.append(c);
        }
        return arguments;
    }

    private static String lire(Path chemin) {
        try {
            return Files.readString(chemin);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
