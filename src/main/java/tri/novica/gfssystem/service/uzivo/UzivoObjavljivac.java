package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Šalje stanja izvođenja klijentima (spec 4.3), uvek posle commit-a, pa klijent nikad ne vidi stanje koje nije u bazi.
 * <ul>
 *   <li>promena (komanda, tajmer, prijava, moderacija): odmah javno stanje, lično stanje svakom učesniku i
 *       nastavničko stanje, sve iz jednog čitanja ({@link StanjeService#snimci});</li>
 *   <li>odgovor, povezivanje i prekid veze samo zaprljaju izvođenje (i učesnika kome treba lično stanje); na 250 ms
 *       {@link #flush} po izvođenju jednom pročita stanje i pošalje nastavničko stanje i lična stanja zaprljanih
 *       učesnika. Talas od 300 odgovora za 2 s je tako najviše 4 čitanja u sekundi, a telefon vidi "primljen" za
 *       najviše ~250 ms plus čitanje;</li>
 *   <li>izbacivanje: izbačenom (koga {@code licnaZaSve} ne sadrži) lično stanje sa {@code izbacen=true}.</li>
 * </ul>
 * Čitanje i slanje za jedno izvođenje idu pod istom bravom, pa snimci istog izvođenja izlaze redom kojim su pročitani
 * (lično stanje ne nosi novu verziju, a klijent prima i jednaku). Greška jednog izvođenja se loguje i ne zaustavlja
 * ostala ni sledeći flush.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UzivoObjavljivac {

    static final long FLUSH_MS = 250;
    private static final int BROJ_BRAVA = 64;

    private final SimpMessageSendingOperations poruke;
    private final StanjeService stanjeService;
    private final PovezanostRegistar povezanost;

    /** Zaprljana izvođenja -> učesnici kojima treba lično stanje (prazan skup: samo nastavničko). */
    private final Map<Long, Set<Long>> prljavi = new ConcurrentHashMap<>();
    private final Object[] brave = napraviBrave();

    public static String javnoOdrediste(Long izvodjenjeId) {
        return "/topic/izvodjenja/" + izvodjenjeId + "/javno";
    }

    public static String nastavnikOdrediste(Long izvodjenjeId) {
        return "/topic/izvodjenja/" + izvodjenjeId + "/nastavnik";
    }

    public static final String LICNO = "/queue/licno";

    // ---------------------------------------------------------------- događaji servisa (posle commit-a)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPromena(IzvodjenjePromenjeno e) {
        Long id = e.izvodjenjeId();
        if (id == null) return;
        synchronized (brava(id)) {
            // sve što je zaprljano do sad pokriva ovo čitanje
            prljavi.remove(id);
            try {
                StanjeService.Snimci s = stanjeService.snimci(id);
                poruke.convertAndSend(javnoOdrediste(id), s.javno());
                s.licna().forEach((ucesnikId, licno) -> licnoUcesniku(ucesnikId, licno));
                poruke.convertAndSend(nastavnikOdrediste(id), s.nastavnicko());
            } catch (Exception ex) {
                zabelezi(id, ex);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOdgovor(OdgovorPrimljen e) {
        zaprljaj(e.izvodjenjeId(), e.ucesnikId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIzbacen(UcesnikIzbacen e) {
        Long id = e.izvodjenjeId();
        if (id == null || e.ucesnikId() == null) return;
        synchronized (brava(id)) {
            try {
                licnoUcesniku(e.ucesnikId(), stanjeService.licno(id, e.ucesnikId()));
            } catch (Exception ex) {
                zabelezi(id, ex);
            }
        }
    }

    // ---------------------------------------------------------------- povezanost

    @EventListener
    public void onPovezan(SessionConnectedEvent e) {
        Sesija s = sesija(e.getMessage());
        if (s == null) return;
        povezanost.povezan(s.ucesnikId(), s.sesijaId());
        zaprljaj(s.izvodjenjeId(), null);
    }

    @EventListener
    public void onPrekinut(SessionDisconnectEvent e) {
        povezanost.prekinut(e.getSessionId());
        Sesija s = sesija(e.getMessage());
        if (s != null) zaprljaj(s.izvodjenjeId(), null);
    }

    // ---------------------------------------------------------------- periodično slanje

    /** Zaprljana izvođenja: po jedno čitanje, nastavničko stanje i lična stanja zaprljanih učesnika. */
    @Scheduled(fixedRate = FLUSH_MS)
    public void flush() {
        for (Long id : List.copyOf(prljavi.keySet())) {
            synchronized (brava(id)) {
                // skinut atomično: ono što stigne posle ide u sledeći flush
                Set<Long> ucesnici = prljavi.remove(id);
                if (ucesnici == null) continue;
                try {
                    StanjeService.Snimci s = stanjeService.snimci(id);
                    poruke.convertAndSend(nastavnikOdrediste(id), s.nastavnicko());
                    for (Long ucesnikId : ucesnici) {
                        LicnoStanje licno = s.licna().get(ucesnikId);
                        if (licno != null) licnoUcesniku(ucesnikId, licno);
                    }
                } catch (Exception ex) {
                    zabelezi(id, ex);
                }
            }
        }
    }

    /** Za test: zaprljana izvođenja. */
    Set<Long> zaprljana() {
        return Set.copyOf(prljavi.keySet());
    }

    // ---------------------------------------------------------------- pomoćno

    private void zaprljaj(Long izvodjenjeId, Long ucesnikId) {
        if (izvodjenjeId == null) return;
        prljavi.compute(izvodjenjeId, (k, skup) -> {
            Set<Long> s = skup == null ? new HashSet<>() : skup;
            if (ucesnikId != null) s.add(ucesnikId);
            return s;
        });
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
    private static Sesija sesija(Message<?> m) {
        String sesijaId = SimpMessageHeaderAccessor.getSessionId(m.getHeaders());
        Map<String, Object> atributi = SimpMessageHeaderAccessor.getSessionAttributes(m.getHeaders());
        if (atributi == null && m.getHeaders().get(SimpMessageHeaderAccessor.CONNECT_MESSAGE_HEADER) instanceof Message<?> c) {
            atributi = SimpMessageHeaderAccessor.getSessionAttributes(c.getHeaders());
        }
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
