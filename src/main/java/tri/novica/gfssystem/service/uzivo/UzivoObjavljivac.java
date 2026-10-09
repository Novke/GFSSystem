package tri.novica.gfssystem.service.uzivo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import tri.novica.gfssystem.config.StompPrincipal;
import tri.novica.gfssystem.config.Uloga;
import tri.novica.gfssystem.config.UzivoHandshakeInterceptor;
import tri.novica.gfssystem.dto.uzivo.LicnoStanje;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Šalje stanja izvođenja klijentima (spec 4.3), uvek posle commit-a, pa klijent nikad ne vidi stanje koje nije u bazi.
 * <p>
 * <b>Niti:</b> slušaoci događaja (nit zahteva ili STOMP nit, posle commit-a, možda još sa JDBC vezom zahteva) samo
 * zaprljaju izvođenje i, kad treba odmah, zakažu obradu; nikad ne čitaju bazu i ne čekaju bravu. Čitanje i slanje
 * radi samo nit objavljivača ({@link #obradi}: {@code taskScheduler} odmah posle komande, ili {@link #flush} na
 * 250 ms), pod bravom tog izvođenja, pa snimci jednog izvođenja izlaze redom kojim su pročitani (lično stanje ne nosi
 * novu verziju, a klijent prima i jednaku), a nit koja čeka bravu ne drži vezu sa bazom.
 * <ul>
 *   <li>komanda, tajmer, moderacija, izmena prezentacije ({@link IzvodjenjePromenjeno}): sve (javno, lično svima,
 *       nastavničko) iz jednog čitanja, odmah;</li>
 *   <li>prijava ({@link UcesnikPrijavljen}): sve, ali tek u flush-u, pa talas od 300 prijava pravi najviše 4 čitanja u
 *       sekundi (ne 300 čitanja i 300 x 300 ličnih poruka);</li>
 *   <li>odgovor: nastavničko i lično stanje tog učesnika u flush-u ("primljen" za najviše ~250 ms plus čitanje);
 *       povezivanje i prekid: nastavničko u flush-u;</li>
 *   <li>izbacivanje: izbačenom (koga {@code licnaZaSve} ne sadrži) lično stanje sa {@code izbacen=true}, odmah.</li>
 * </ul>
 * Greška jednog izvođenja se loguje i ne zaustavlja ostala ni sledeći flush.
 */
@Component
@Slf4j
public class UzivoObjavljivac {

    static final long FLUSH_MS = 250;
    private static final int BROJ_BRAVA = 64;
    public static final String LICNO = "/queue/licno";

    private final SimpMessageSendingOperations poruke;
    private final StanjeService stanjeService;
    private final PovezanostRegistar povezanost;
    private final Executor izvrsilac;

    /** Zaprljana izvođenja i šta im treba poslati; menja se samo kroz {@code compute}, skida se atomično. */
    private final Map<Long, Zahtev> prljavi = new ConcurrentHashMap<>();
    private final Object[] brave = napraviBrave();

    public UzivoObjavljivac(SimpMessageSendingOperations poruke, StanjeService stanjeService,
                            PovezanostRegistar povezanost, @Qualifier("taskScheduler") Executor izvrsilac) {
        this.poruke = poruke;
        this.stanjeService = stanjeService;
        this.povezanost = povezanost;
        this.izvrsilac = izvrsilac;
    }

    public static String javnoOdrediste(Long izvodjenjeId) {
        return "/topic/izvodjenja/" + izvodjenjeId + "/javno";
    }

    public static String nastavnikOdrediste(Long izvodjenjeId) {
        return "/topic/izvodjenja/" + izvodjenjeId + "/nastavnik";
    }

    /** Šta izvođenju treba poslati (skuplja se do obrade). */
    private static final class Zahtev {
        boolean sve;
        final Set<Long> licna = new HashSet<>();
        final Set<Long> izbaceni = new HashSet<>();
    }

    // ---------------------------------------------------------------- događaji servisa (posle commit-a)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPromena(IzvodjenjePromenjeno e) {
        if (zaprljaj(e.izvodjenjeId(), z -> z.sve = true)) odmah(e.izvodjenjeId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPrijava(UcesnikPrijavljen e) {
        zaprljaj(e.izvodjenjeId(), z -> z.sve = true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOdgovor(OdgovorPrimljen e) {
        if (e.ucesnikId() == null) return;
        zaprljaj(e.izvodjenjeId(), z -> z.licna.add(e.ucesnikId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIzbacen(UcesnikIzbacen e) {
        if (e.ucesnikId() == null) return;
        if (zaprljaj(e.izvodjenjeId(), z -> z.izbaceni.add(e.ucesnikId()))) odmah(e.izvodjenjeId());
    }

    // ---------------------------------------------------------------- povezanost

    @EventListener
    public void onPovezan(SessionConnectedEvent e) {
        Sesija s = sesija(e.getMessage());
        if (s == null) return;
        povezanost.povezan(s.ucesnikId(), s.sesijaId());
        zaprljaj(s.izvodjenjeId(), z -> { });
    }

    @EventListener
    public void onPrekinut(SessionDisconnectEvent e) {
        povezanost.prekinut(e.getSessionId());
        Sesija s = sesija(e.getMessage());
        if (s != null) zaprljaj(s.izvodjenjeId(), z -> { });
    }

    // ---------------------------------------------------------------- nit objavljivača

    /** Sva zaprljana izvođenja, jedno po jedno (svako jednim čitanjem). */
    @Scheduled(fixedRate = FLUSH_MS)
    public void flush() {
        for (Long id : List.copyOf(prljavi.keySet())) {
            obradi(id);
        }
    }

    /**
     * Pod bravom izvođenja: skine zahtev (ono što stigne posle čeka sledeću obradu), jednom pročita stanje i pošalje
     * šta zahtev traži. Nikad ne baca izuzetak.
     */
    void obradi(Long id) {
        synchronized (brava(id)) {
            Zahtev z = prljavi.remove(id);
            if (z == null) return;
            try {
                for (Long ucesnikId : z.izbaceni) {
                    try {
                        licnoUcesniku(ucesnikId, stanjeService.licno(id, ucesnikId));
                    } catch (Exception ex) {
                        zabelezi(id, ex);
                    }
                }
                StanjeService.Snimci s = stanjeService.snimci(id);
                if (z.sve) {
                    poruke.convertAndSend(javnoOdrediste(id), s.javno());
                    s.licna().forEach(this::licnoUcesniku);
                } else {
                    for (Long ucesnikId : z.licna) {
                        LicnoStanje licno = s.licna().get(ucesnikId);
                        if (licno != null) licnoUcesniku(ucesnikId, licno);
                    }
                }
                poruke.convertAndSend(nastavnikOdrediste(id), s.nastavnicko());
            } catch (Exception ex) {
                zabelezi(id, ex);
            }
        }
    }

    /** Za test: zaprljana izvođenja. */
    Set<Long> zaprljana() {
        return Set.copyOf(prljavi.keySet());
    }

    // ---------------------------------------------------------------- pomoćno

    private boolean zaprljaj(Long izvodjenjeId, java.util.function.Consumer<Zahtev> izmena) {
        if (izvodjenjeId == null) return false;
        prljavi.compute(izvodjenjeId, (k, z) -> {
            Zahtev n = z == null ? new Zahtev() : z;
            izmena.accept(n);
            return n;
        });
        return true;
    }

    /** Obrada odmah na niti objavljivača; ako zakazivanje ne uspe, zahtev ostaje za flush. */
    private void odmah(Long id) {
        try {
            izvrsilac.execute(() -> obradi(id));
        } catch (Exception ex) {
            log.warn("Objava nije zakazana, ide u sledeći flush: izvodjenje={}", id, ex);
        }
    }

    private void licnoUcesniku(Long ucesnikId, LicnoStanje licno) {
        try {
            poruke.convertAndSendToUser(StompPrincipal.student(ucesnikId), LICNO, licno);
        } catch (Exception ex) {
            log.warn("Lično stanje nije poslato: ucesnik={}", ucesnikId, ex);
        }
    }

    private static void zabelezi(Long izvodjenjeId, Exception ex) {
        if (ex instanceof SystemException se && Integer.valueOf(HttpStatus.NOT_FOUND.value()).equals(se.getCode())) {
            // izvođenje (ili učesnik) obrisano u međuvremenu: nema kome da se šalje
            log.debug("Stanje nije poslato, nema ga: izvodjenje={}", izvodjenjeId);
        } else {
            log.warn("Stanje nije poslato: izvodjenje={}", izvodjenjeId, ex);
        }
    }

    private Object brava(Long izvodjenjeId) {
        return brave[Math.floorMod(izvodjenjeId.hashCode(), BROJ_BRAVA)];
    }

    private static Object[] napraviBrave() {
        Object[] b = new Object[BROJ_BRAVA];
        for (int i = 0; i < b.length; i++) b[i] = new Object();
        return b;
    }

    private record Sesija(String sesijaId, Long izvodjenjeId, Long ucesnikId) {
    }

    /**
     * Studentska sesija iz poruke događaja: DISCONNECT nosi atribute sesije, a CONNECT_ACK ih nosi u originalnoj
     * CONNECT poruci ({@code simpConnectMessage}). Nastavnička sesija -> {@code null}.
     */
    public static Map<String, Object> atributiSesije(Message<?> m) {
        Map<String, Object> atributi = SimpMessageHeaderAccessor.getSessionAttributes(m.getHeaders());
        if (atributi == null && m.getHeaders().get(SimpMessageHeaderAccessor.CONNECT_MESSAGE_HEADER) instanceof Message<?> c) {
            atributi = SimpMessageHeaderAccessor.getSessionAttributes(c.getHeaders());
        }
        return atributi;
    }

    private static Sesija sesija(Message<?> m) {
        String sesijaId = SimpMessageHeaderAccessor.getSessionId(m.getHeaders());
        Map<String, Object> atributi = atributiSesije(m);
        if (sesijaId == null || atributi == null || atributi.get(UzivoHandshakeInterceptor.ATR_ULOGA) != Uloga.STUDENT) {
            return null;
        }
        if (atributi.get(UzivoHandshakeInterceptor.ATR_IZVODJENJE) instanceof Long izvodjenjeId
                && atributi.get(UzivoHandshakeInterceptor.ATR_UCESNIK) instanceof Long ucesnikId) {
            return new Sesija(sesijaId, izvodjenjeId, ucesnikId);
        }
        return null;
    }
}
