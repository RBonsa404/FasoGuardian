package bf.fasoguardian.dispositifs.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.CommandeAEnvoyer;
import bf.fasoguardian.dispositifs.Commandes;
import bf.fasoguardian.dispositifs.domaine.Bracelet;
import bf.fasoguardian.dispositifs.domaine.Commande;
import bf.fasoguardian.dispositifs.domaine.Commande.Statut;
import bf.fasoguardian.dispositifs.domaine.Commande.Type;
import bf.fasoguardian.dispositifs.domaine.ConfigurationBracelet;
import bf.fasoguardian.dispositifs.domaine.StatutBracelet;
import bf.fasoguardian.dispositifs.infrastructure.DepotAppairages;
import bf.fasoguardian.dispositifs.infrastructure.DepotBracelets;
import bf.fasoguardian.dispositifs.infrastructure.DepotCommandes;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Émission et suivi des commandes signées (FG-DOC-08 §8.1, US-SYS-011). Le message est
 * {@code base64url(corps) + "." + base64url(signature)} ; le corps porte l'identifiant de la commande, son
 * numéro croissant et sa date d'expiration, ce qui interdit le rejeu. Une commande sans accusé est réémise à
 * l'identique jusqu'à son expiration.
 */
@Service
public class CommandesBracelet implements Commandes {

    /** Délai sans accusé au bout duquel une commande est réémise. */
    static final Duration DELAI_DE_REEMISSION = Duration.ofSeconds(30);
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final DepotBracelets bracelets;
    private final DepotAppairages appairages;
    private final DepotCommandes commandes;
    private final Signataire signataire;
    private final ApplicationEventPublisher evenements;
    private final JournalAudit journal;
    private final JsonMapper json;
    private final MeterRegistry metriques;
    private final Clock horloge;

    CommandesBracelet(DepotBracelets bracelets, DepotAppairages appairages, DepotCommandes commandes,
            Signataire signataire, ApplicationEventPublisher evenements, JournalAudit journal, JsonMapper json,
            MeterRegistry metriques, Clock horloge) {
        this.bracelets = bracelets;
        this.appairages = appairages;
        this.commandes = commandes;
        this.signataire = signataire;
        this.evenements = evenements;
        this.journal = journal;
        this.json = json;
        this.metriques = metriques;
        this.horloge = horloge;
    }

    @Override
    @Transactional
    public void modeAlerte(UUID enfantId, boolean actif) {
        braceletDe(enfantId).ifPresent(bracelet -> emettre(bracelet, Type.MODE_ALERTE, Map.of("on", actif ? 1 : 0), null));
    }

    @Override
    @Transactional
    public void fenetreDeRetrait(UUID enfantId, Instant fin) {
        braceletDe(enfantId).ifPresent(bracelet -> emettre(bracelet, Type.RETRAIT,
                Map.of("until", fin == null ? 0 : fin.getEpochSecond()), null));
    }

    /** Localisation immédiate demandée par un tuteur. @return {@code false} sans bracelet en service */
    @Transactional
    public boolean localiser(UUID tuteurId, UUID enfantId) {
        Optional<Bracelet> bracelet = braceletDe(enfantId);
        bracelet.ifPresent(b -> emettre(b, Type.LOCALISER, Map.of(), tuteurId));
        return bracelet.isPresent();
    }

    /** Envoie au bracelet sa configuration courante (intervalles, mode économie). */
    @Transactional
    public void configurer(Bracelet bracelet, ConfigurationBracelet configuration, UUID tuteurId) {
        if (enService(bracelet)) {
            Map<String, Object> parametres = new LinkedHashMap<>();
            parametres.put("int", configuration.intervalleCourantS());
            parametres.put("alr", configuration.intervalleAlerteS());
            parametres.put("eco", configuration.modeEconomie() ? 1 : 0);
            emettre(bracelet, Type.CONFIGURATION, parametres, tuteurId);
        }
    }

    @Override
    @Transactional
    public void accuser(String numeroSerie, String commandeId, boolean executee) {
        Optional<Bracelet> bracelet = bracelets.findByNumeroSerie(numeroSerie);
        Optional<Commande> commande = identifiant(commandeId).flatMap(commandes::findById)
                .filter(c -> bracelet.isPresent() && c.braceletId().equals(bracelet.get().id()));
        if (commande.isEmpty()) {
            if (!executee && bracelet.isPresent()) {
                // Le bracelet signale une commande qu'il n'a pas pu authentifier et que la plateforme n'a pas émise.
                journal.consigner(null, "SYSTEME", "COMMANDE_ETRANGERE_REJETEE", "BRACELET", bracelet.get().id().toString(),
                        Resultat.REFUS);
                metriques.counter("fasoguardian.commandes", "issue", "ETRANGERE").increment();
            }
            return;
        }
        if (commande.get().accuser(executee, horloge.instant())) {
            metriques.counter("fasoguardian.commandes", "issue", executee ? "ACCUSEE" : "REFUSEE").increment();
            if (!executee) {
                journal.consigner(null, "SYSTEME", "COMMANDE_REFUSEE_PAR_LE_BRACELET", "BRACELET",
                        commande.get().braceletId().toString(), Resultat.REFUS);
            }
        }
    }

    /** Réémet les commandes restées sans accusé et clôt celles qui ont expiré. */
    @Scheduled(fixedDelayString = "${fasoguardian.commandes.reemission:PT30S}")
    @SchedulerLock(name = "dispositifs-reemission-commandes", lockAtMostFor = "PT2M")
    @Transactional
    public void reemettre() {
        Instant maintenant = horloge.instant();
        for (Commande commande : commandes.findByStatutAndEmiseLeBefore(Statut.EMISE, maintenant.minus(DELAI_DE_REEMISSION))) {
            if (!commande.expireLe().isAfter(maintenant)) {
                commande.expirer();
                metriques.counter("fasoguardian.commandes", "issue", "EXPIREE").increment();
            } else {
                bracelets.findById(commande.braceletId()).filter(CommandesBracelet::enService).ifPresent(
                        bracelet -> evenements.publishEvent(new CommandeAEnvoyer(bracelet.numeroSerie(), commande.message())));
            }
        }
    }

    // -------------------------------------------------------------------- aides

    private void emettre(Bracelet bracelet, Type type, Map<String, Object> parametres, UUID acteurId) {
        Instant maintenant = horloge.instant();
        UUID id = UUID.randomUUID();
        // Numéro strictement croissant par bracelet ; l'horloge en donne un qui survit à une restauration de la base.
        long sequence = Math.max(maintenant.toEpochMilli(),
                commandes.findFirstByBraceletIdOrderBySequenceDesc(bracelet.id()).map(Commande::sequence).orElse(0L) + 1);
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("id", id.toString());
        corps.put("dev", bracelet.numeroSerie());
        corps.put("cmd", type.code());
        corps.put("n", sequence);
        corps.put("exp", maintenant.plus(Commande.VALIDITE).getEpochSecond());
        corps.put("p", parametres);
        byte[] contenu = json.writeValueAsString(corps).getBytes(StandardCharsets.UTF_8);
        String message = BASE64.encodeToString(contenu) + "." + BASE64.encodeToString(signataire.signer(contenu));
        commandes.save(new Commande(id, bracelet.id(), type, sequence, message, maintenant));
        journal.consigner(acteurId, acteurId == null ? "SYSTEME" : "PARENT", "COMMANDE_" + type.name(), "BRACELET",
                bracelet.id().toString(), Resultat.SUCCES);
        metriques.counter("fasoguardian.commandes", "issue", "EMISE").increment();
        evenements.publishEvent(new CommandeAEnvoyer(bracelet.numeroSerie(), message));
    }

    private Optional<Bracelet> braceletDe(UUID enfantId) {
        return appairages.findByEnfantIdAndFinIsNull(enfantId).flatMap(appairage -> bracelets.findById(appairage.braceletId()))
                .filter(CommandesBracelet::enService);
    }

    private static boolean enService(Bracelet bracelet) {
        return (bracelet.statut() == StatutBracelet.ACTIF || bracelet.statut() == StatutBracelet.PERDU)
                && !bracelet.certificatRevoque();
    }

    private static Optional<UUID> identifiant(String valeur) {
        try {
            return Optional.of(UUID.fromString(valeur));
        } catch (IllegalArgumentException | NullPointerException erreur) {
            return Optional.empty();
        }
    }
}
