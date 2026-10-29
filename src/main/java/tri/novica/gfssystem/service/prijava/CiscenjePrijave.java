package tri.novica.gfssystem.service.prijava;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Čišćenje starih zapisa prijave (spec 9), jednom na sat u 7. minutu. IP adrese i drugi tragovi ne žive duže od roka:
 * <ul>
 *   <li>{@code pokusaji}: 2 dana (brojači limita traže najviše sat vremena, pa je ovo i za pravilo 1 mejl/h
 *       {@code MEJL_NASTAVNIKU*} dovoljno);</li>
 *   <li>{@code kodovi_za_prijavu}: 1 dan od nastanka (kod važi 10 minuta);</li>
 *   <li>{@code lti_stanja}: istekla (rok 10 minuta) pre više od sat vremena. Svaki {@code GET /public/lti/prijava} upisuje
 *       jedno, bez ograničenja u aplikaciji, pa se ne čeka dan; red posle isteka ne služi ničemu (jednokratnost proverava
 *       samo važeće stanje);</li>
 *   <li>{@code lti_povezivanja} ({@code __Host-gfs_lti_par}, rok 30 minuta): istekla ili iskorišćena pre više od sat vremena;</li>
 *   <li>{@code nastavnicke_sesije} i {@code studentski_uredjaji}: istekli ili opozvani pre više od 30 dana;</li>
 *   <li>{@code cekiranja_na_cekanju} (ključ čekanja, rok 15 minuta): istekli ili iskorišćeni pre više od sat vremena;</li>
 *   <li>{@code obavestenja}: rešena pre više od 180 dana (otvorena se ne brišu). Pravilo "najviše jedno
 *       {@code PREVISE_KODOVA} na 24 sata" čita i rešena obaveštenja, a ona se ovde čiste tek posle 180 dana.</li>
 *   <li>{@code vezbanja} (studentski nalog): netaknuti pokušaji (bez provere i bez otvorenog rešenja) stariji od 30 dana.
 *       Ne ulaze u učinak studenta; u statistici vežbanja "Izvučeno" za starije periode zato broji samo pokušaje koji su
 *       provereni ili kojima je otvoreno rešenje.</li>
 *   <li>push (spec aplikacije 6.7): {@code push_pretplate} čiji uređaj ili sesija ne važi (istekao ili opozvan) duže od 30
 *       dana (brišu se pre uređaja i sesija; FK CASCADE bi ih i onako odneo), {@code studentska_obavestenja} starija od 90
 *       dana (zvono pokazuje 30 poslednjih) i {@code posle_casa_poslato} stariji od 60 dana.</li>
 * </ul>
 * Brisanje ide u grupama (podrazumevano 5000 redova, svaka grupa je svoj commit), da navala redova ne drži dugo
 * zaključane tabele. Greška jednog koraka se samo loguje (klasa izuzetka), ostali koraci rade. Log: samo brojevi.
 */
@Component
@Slf4j
public class CiscenjePrijave {

    static final Duration POKUSAJI = Duration.ofDays(2);
    static final Duration KODOVI = Duration.ofDays(1);
    /** Koliko posle isteka (ili korišćenja) se čuvaju LTI stanja i povezivanja. */
    static final Duration LTI_NAKON_ISTEKA = Duration.ofHours(1);
    static final Duration SESIJE_I_UREDJAJI = Duration.ofDays(30);
    static final Duration RESENA_OBAVESTENJA = Duration.ofDays(180);
    static final Duration NETAKNUTA_VEZBANJA = Duration.ofDays(30);
    static final Duration STUDENTSKA_OBAVESTENJA = Duration.ofDays(90);
    static final Duration POSLE_CASA = Duration.ofDays(60);
    static final int VELICINA_GRUPE = 5000;

    /** Koliko je redova obrisano po tabelama. */
    public record Obrisano(int pokusaji, int kodovi, int ltiStanja, int ltiPovezivanja, int nastavnickeSesije,
                           int uredjaji, int obavestenja, int kljuceviCekiranja, int netaknutaVezbanja, int pushPretplate,
                           int studentskaObavestenja, int posleCasa) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private volatile int velicinaGrupe = VELICINA_GRUPE;

    public CiscenjePrijave(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Cron se čita iz {@code gfs.prijava.ciscenje.cron} ({@code -} gasi raspored, testovi ga gase). */
    @Scheduled(cron = "${gfs.prijava.ciscenje.cron:0 7 * * * *}")
    public Obrisano ocisti() {
        LocalDateTime sada = LocalDateTime.now(clock);
        LocalDateTime lti = sada.minus(LTI_NAKON_ISTEKA);
        LocalDateTime sesije = sada.minus(SESIJE_I_UREDJAJI);
        // pretplate pre uređaja i sesija (isti rok): posle brisanja vlasnika ne bi bilo po čemu da se nađu
        int pushPretplate = brisi("push_pretplate", """
                delete from push_pretplate
                where studentski_uredjaj_id in (select id from studentski_uredjaji
                                                where istice < ? or (opozvan is not null and opozvan < ?))
                   or nastavnicka_sesija_id in (select id from nastavnicke_sesije
                                                where istice < ? or (opozvana is not null and opozvana < ?))""",
                sesije, sesije, sesije, sesije);
        Obrisano o = new Obrisano(
                brisi("pokusaji", "delete from pokusaji where vreme < ?", sada.minus(POKUSAJI)),
                brisi("kodovi_za_prijavu", "delete from kodovi_za_prijavu where kreirano < ?", sada.minus(KODOVI)),
                brisi("lti_stanja", "delete from lti_stanja where istice < ?", lti),
                brisi("lti_povezivanja",
                        "delete from lti_povezivanja where istice < ? or (iskorisceno is not null and iskorisceno < ?)", lti, lti),
                brisi("nastavnicke_sesije",
                        "delete from nastavnicke_sesije where istice < ? or (opozvana is not null and opozvana < ?)", sesije, sesije),
                brisi("studentski_uredjaji",
                        "delete from studentski_uredjaji where istice < ? or (opozvan is not null and opozvan < ?)", sesije, sesije),
                brisi("obavestenja", "delete from obavestenja where reseno is not null and reseno < ?",
                        sada.minus(RESENA_OBAVESTENJA)),
                brisi("cekiranja_na_cekanju",
                        "delete from cekiranja_na_cekanju where istice < ? or (iskorisceno is not null and iskorisceno < ?)",
                        lti, lti),
                brisi("vezbanja",
                        "delete from vezbanja where broj_provera = 0 and resenje_prikazano = 0 and izvuceno < ?",
                        sada.minus(NETAKNUTA_VEZBANJA)),
                pushPretplate,
                brisi("studentska_obavestenja", "delete from studentska_obavestenja where kreirano < ?",
                        sada.minus(STUDENTSKA_OBAVESTENJA)),
                brisi("posle_casa_poslato", "delete from posle_casa_poslato where datum < ?",
                        sada.toLocalDate().minus(POSLE_CASA)));
        log.info("Čišćenje prijave: {}", o);
        return o;
    }

    /** Briše u grupama dok grupa nije puna; vraća ukupno obrisano (0 i uz zapis u log ako korak padne). */
    private int brisi(String tabela, String sql, Object... granice) {
        int grupa = velicinaGrupe;
        int ukupno = 0;
        try {
            int obrisano;
            do {
                obrisano = jdbc.update(sql + " limit " + grupa, granice);
                ukupno += obrisano;
            } while (obrisano >= grupa);
        } catch (RuntimeException e) {
            // samo klasa: poruka može da nosi vrednosti iz upita
            log.error("Čišćenje prijave nije uspelo za {} (obrisano do greške {}): {}", tabela, ukupno, e.getClass().getSimpleName());
        }
        return ukupno;
    }

    /** Menja veličinu grupe brisanja (testovi). */
    void velicinaGrupe(int redova) {
        velicinaGrupe = Math.max(1, redova);
    }
}
