package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.config.NastavnikSvojstva;
import tri.novica.gfssystem.entity.push.PushPretplata;
import tri.novica.gfssystem.repository.push.PushPretplataRepository;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Slanje Web Push poruka (spec aplikacije 5.2 i 5.3). Pozivalac predaje poruke <b>van transakcije ili posle commit-a</b>
 * ({@link #posleCommita}); ovde se u sopstvenoj transakciji pročitaju važeće pretplate primalaca i za svaku se jedan
 * zadatak stavi u red {@code pushIzvrsilac}. Zadatak pre slanja ponovo čita pretplatu (uređaj ili sesija i dalje važi,
 * endpoint je isti, adresa je i dalje dozvoljena), šifruje poruku (RFC 8291), potpisuje VAPID (RFC 8292) i šalje. Ishod:
 * 2xx nuluje greške i upisuje {@code poslednji_uspeh}; 404 i 410 brišu pretplatu; sve drugo (i 429, 5xx, mrežna greška)
 * povećava {@code uzastopne_greske}, a posle {@value #MAX_GRESAKA} uzastopnih pretplata se briše. Ništa se ne ponavlja.
 * Log: samo {@link PushEndpoint#trag} (host i 8 hex heša), id pretplate i status; nikad endpoint, ključ ni sadržaj.
 */
@Service
@Slf4j
public class PushPosiljalac {

    public static final int MAX_GRESAKA = 5;
    static final Duration VAPID_TRAJANJE = Duration.ofHours(12);
    private static final Pattern MEJL = Pattern.compile("^[^@\\s,;<>\"]+@[^@\\s,;<>\"]+\\.[^@\\s,;<>\"]+$");

    /** Jedna poruka za jednu pretplatu, spremna za red. */
    record Zadatak(Long pretplataId, String endpointHes, String trag, byte[] poruka, long ttlSekundi, boolean hitno) {
    }

    private final PushPretplataRepository pretplate;
    private final PushKljucevi kljucevi;
    private final PushTransport transport;
    private final TaskExecutor izvrsilac;
    private final TransactionTemplate citanje;
    private final Clock clock;
    private final String kontakt;
    private final SecureRandom random = new SecureRandom();

    public PushPosiljalac(PushPretplataRepository pretplate, PushKljucevi kljucevi, PushTransport transport,
                          @Qualifier("pushIzvrsilac") TaskExecutor izvrsilac, PlatformTransactionManager transakcije,
                          Clock clock, NastavnikSvojstva nastavnik, @Value("${gfs.front.url}") String frontUrl) {
        this.pretplate = pretplate;
        this.kljucevi = kljucevi;
        this.transport = transport;
        this.izvrsilac = izvrsilac;
        this.citanje = new TransactionTemplate(transakcije);
        this.citanje.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.citanje.setReadOnly(true);
        this.clock = clock;
        this.kontakt = kontakt(nastavnik.mejl(), frontUrl);
    }

    /**
     * VAPID {@code sub}: {@code mailto:} adresa nastavnika ({@code gfs.nastavnik.mejl}), a bez nje adresa prvog
     * {@code gfs.front.url} (bez završne {@code /}).
     */
    static String kontakt(String mejl, String frontUrl) {
        if (mejl != null && MEJL.matcher(mejl.strip()).matches()) {
            return "mailto:" + mejl.strip();
        }
        String prvi = frontUrl == null ? "" : frontUrl.split(",")[0].strip();
        while (prvi.endsWith("/")) {
            prvi = prvi.substring(0, prvi.length() - 1);
        }
        return prvi;
    }

    /**
     * Izvršava {@code rad} posle commit-a tekuće transakcije, ili odmah kad transakcije nema. Greška u {@code rad} se samo
     * loguje: push nikad ne sme da obori pozivaoca.
     */
    public static void posleCommita(Runnable rad) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bezbedno(rad);
                }
            });
        } else {
            bezbedno(rad);
        }
    }

    /** Čitanje u sopstvenoj transakciji (iz {@code afterCommit} povratnog poziva se ne sme koristiti tekuća). */
    public <T> T uNovojTransakciji(java.util.function.Supplier<T> rad) {
        return citanje.execute(s -> rad.get());
    }

    private static void bezbedno(Runnable rad) {
        try {
            rad.run();
        } catch (RuntimeException e) {
            log.error("Push nije stavljen u red: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Poruka svakom studentu na sve njegove važeće pretplate (jedan red čitanja, jedan zadatak po pretplati). Zove se van
     * transakcije ili posle commit-a. Vraća broj poruka stavljenih u red (pun red: poruka se propušta i ne broji).
     */
    public int studentima(Map<Long, PushPoruka> poruke, Duration ttl, boolean hitno) {
        if (poruke.isEmpty()) {
            return 0;
        }
        LocalDateTime sada = LocalDateTime.now(clock);
        List<Object[]> parovi = citanje.execute(s -> pretplate.vazeceStudenata(poruke.keySet(), sada));
        List<Zadatak> zadaci = new ArrayList<>();
        for (Object[] r : parovi == null ? List.<Object[]>of() : parovi) {
            Long studentId = ((Number) r[0]).longValue();
            PushPretplata p = (PushPretplata) r[1];
            PushPoruka poruka = poruke.get(studentId);
            if (poruka != null) {
                zadaci.add(zadatak(p, poruka, ttl, hitno));
            }
        }
        return uRed(zadaci);
    }

    /** Poruka nastavniku na sve važeće pretplate (sesije); kao {@link #studentima}. */
    public int nastavniku(PushPoruka poruka, Duration ttl, boolean hitno) {
        LocalDateTime sada = LocalDateTime.now(clock);
        List<PushPretplata> sve = citanje.execute(s -> pretplate.vazeceNastavnika(sada));
        List<Zadatak> zadaci = new ArrayList<>();
        for (PushPretplata p : sve == null ? List.<PushPretplata>of() : sve) {
            zadaci.add(zadatak(p, poruka, ttl, hitno));
        }
        return uRed(zadaci);
    }

    /** Poruka na jednu pretplatu (probno obaveštenje); {@code true} kad je stavljena u red. */
    public boolean pretplati(PushPretplata p, PushPoruka poruka, Duration ttl, boolean hitno) {
        return uRed(List.of(zadatak(p, poruka, ttl, hitno))) == 1;
    }

    private Zadatak zadatak(PushPretplata p, PushPoruka poruka, Duration ttl, boolean hitno) {
        return new Zadatak(p.getId(), p.getEndpointHes(), PushEndpoint.trag(PushEndpoint.host(p.getEndpoint()),
                p.getEndpointHes()), poruka.json(), Math.max(0, ttl.toSeconds()), hitno);
    }

    private int uRed(List<Zadatak> zadaci) {
        int uRedu = 0;
        int odbaceno = 0;
        for (Zadatak z : zadaci) {
            try {
                izvrsilac.execute(() -> posalji(z));
                uRedu++;
            } catch (TaskRejectedException e) {
                odbaceno++;
            }
        }
        if (odbaceno > 0) {
            log.warn("Red push poruka je pun: u redu={}, odbačeno={}", uRedu, odbaceno);
        }
        return uRedu;
    }

    /** Slanje jednog zadatka (nit {@code pushIzvrsilac}); nikad ne baca izuzetak. */
    void posalji(Zadatak z) {
        try {
            LocalDateTime sada = LocalDateTime.now(clock);
            Optional<PushPretplata> pretplata = citanje.execute(s -> pretplate.vazeca(z.pretplataId(), sada));
            if (pretplata == null || pretplata.isEmpty() || !z.endpointHes().equals(pretplata.get().getEndpointHes())) {
                log.debug("Push preskočen (pretplata više ne važi), pretplata={}", z.pretplataId());
                return;
            }
            PushPretplata p = pretplata.get();
            Optional<URI> endpoint = PushEndpoint.uri(p.getEndpoint());
            if (endpoint.isEmpty()) {
                pretplate.obrisi(p.getId());
                log.warn("Push pretplata obrisana (adresa nije dozvoljena), pretplata={}, trag={}", p.getId(), z.trag());
                return;
            }
            byte[] uaJavni = Base64Url.dekodiraj(p.getP256dh());
            byte[] auth = Base64Url.dekodiraj(p.getAuth());
            byte[] telo;
            try {
                telo = WebPushSifra.sifruj(uaJavni, auth, z.poruka(), random);
            } catch (IllegalArgumentException e) {
                pretplate.obrisi(p.getId());
                log.warn("Push pretplata obrisana (neispravni ključevi), pretplata={}, trag={}", p.getId(), z.trag());
                return;
            }
            URI u = endpoint.get();
            long exp = Instant.now(clock).plus(VAPID_TRAJANJE).getEpochSecond();
            String jwt = Vapid.jwt(Vapid.aud(u), exp, kontakt, kljucevi.privatniKljuc());
            int status = transport.posalji(new PushTransport.Zahtev(u, telo, z.ttlSekundi(), z.hitno(),
                    Vapid.zaglavlje(jwt, kljucevi.javniKljuc())));
            ishod(p.getId(), z.trag(), status);
        } catch (RuntimeException e) {
            log.error("Push nije poslat, pretplata={}, trag={}: {}", z.pretplataId(), z.trag(), e.getClass().getSimpleName());
        }
    }

    private void ishod(Long id, String trag, int status) {
        if (status == PushTransport.PRESKOCENO) {
            return;
        }
        if (status >= 200 && status < 300) {
            pretplate.uspeh(id, LocalDateTime.now(clock));
            log.debug("Push poslat, pretplata={}, trag={}, status={}", id, trag, status);
        } else if (status == 404 || status == 410 || status == PushTransport.NEDOZVOLJENO) {
            pretplate.obrisi(id);
            log.info("Push pretplata obrisana (servis je odbija), pretplata={}, trag={}, status={}", id, trag, status);
        } else {
            pretplate.greska(id);
            boolean obrisana = pretplate.obrisiPosleGresaka(id, MAX_GRESAKA) > 0;
            log.warn("Push nije prošao, pretplata={}, trag={}, status={}{}", id, trag, status,
                    obrisana ? ", pretplata obrisana posle " + MAX_GRESAKA + " uzastopnih grešaka" : "");
        }
    }
}
