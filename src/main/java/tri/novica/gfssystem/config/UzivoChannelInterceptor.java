package tri.novica.gfssystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import tri.novica.gfssystem.service.uzivo.IzbaceniRegistar;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autorizacija svake dolazne STOMP poruke po ulozi iz atributa sesije (spec 4.3); sve što nije izričito dozvoljeno je
 * greška ({@link MessageDeliveryException} -> ERROR frame i zatvorena sesija).
 * <ul>
 *   <li>student (X = njegovo izvođenje): SUBSCRIBE {@code /topic/izvodjenja/X/javno}, {@code /user/queue/licno},
 *       {@code /user/queue/greske}, {@code /app/izvodjenja/X/pocetno}; SEND samo {@code /app/izvodjenja/X/odgovor};</li>
 *   <li>nastavnik: SUBSCRIBE {@code /topic/izvodjenja/{id}/nastavnik|javno} i {@code /app/izvodjenja/{id}/nastavnik-pocetno};
 *       SEND ništa.</li>
 * </ul>
 * Odredišta se proveravaju celim regexom ({@code matches}, nikad {@code startsWith}), pa {@code ../}, kosa crta na kraju
 * ili novi red ne prolaze. Svaki studentov frame (CONNECT, SUBSCRIBE, UNSUBSCRIBE, SEND, heartbeat, DISCONNECT klijenta) troši
 * token bucket <b>po učesniku</b> (sve njegove sesije dele jednu kofu; kapacitet 15, dopuna 5/s). Višak
 * tokena: SEND (odgovor) i heartbeat preko granice se tiho odbacuju ({@code null}), kao i SEND izbačenih učesnika;
 * CONNECT, SUBSCRIBE i UNSUBSCRIBE preko granice su greška (ERROR frame, veza se zatvara, klijent se ponovo poveže).
 * DISCONNECT uvek prolazi i ne vraća kapacitet; sintetički DISCONNECT na kraju veze se ne naplaćuje, pa ponovno
 * povezivanje košta 5 tokena (CONNECT i četiri pretplate) i dva brza reload-a staju u kofu. Kofa se briše samo kad je
 * puna (posle kraja sesije ili periodično), jer je puna kofa ista kao nova. ACK/NACK i transakcije nisu dozvoljeni
 * nikome; nastavnik (iza basic-auth-a) se ne meri.
 * <p>
 * Granice po studentu (memorija i broj poruka koje server šalje ne rastu bez kraja): u jednoj sesiji isto odredište
 * (ili isti id pretplate) najviše jednom i najviše {@value #MAX_PRETPLATA} pretplata; najviše {@value #MAX_SESIJA}
 * aktivne sesije po učesniku (CONNECT preko toga je ERROR). Sesije se vode od CONNECT-a do {@link SessionDisconnectEvent}.
 * <p>
 * Dolazni frame-ovi se obrađuju na pool-u (bez {@code preserveReceiveOrder}), a {@link SessionDisconnectEvent} stiže
 * sinhrono sa WebSocket niti: kad je pool zatrpan (talas prijava), kraj kratke veze može da stigne <b>pre</b> njenog
 * CONNECT-a ili SUBSCRIBE-a. Zato se završene sesije pamte {@value #ZAVRSENE_MS} ms ({@code zavrsene}); CONNECT i SUBSCRIBE
 * za završenu sesiju ne zauzimaju mesto (odbijaju se), upis se posle dodavanja još jednom proverava (trka sa krajem), a
 * {@link #pocisti()} uklanja iz {@code sesije}/{@code pretplate} sve što pripada završenim sesijama (rezerva).
 */
@Component
@Slf4j
public class UzivoChannelInterceptor implements ChannelInterceptor {

    static final double KAPACITET = 15;
    static final double DOPUNA_PO_MS = 5 / 1000.0;
    static final String PREVISE_PORUKA = "Previše poruka.";
    static final int MAX_PRETPLATA = 6;
    static final int MAX_SESIJA = 3;
    static final String PREVISE_VEZA = "Previše otvorenih veza.";
    /** Koliko dugo se pamti završena sesija (zakasneli CONNECT/SUBSCRIBE sa pool-a stižu za nekoliko sekundi). */
    static final long ZAVRSENE_MS = 5 * 60_000;

    private static final String ID = "(\\d{1,18})";
    private static final Pattern STUDENT_JAVNO = Pattern.compile("/topic/izvodjenja/" + ID + "/javno");
    private static final Pattern STUDENT_POCETNO = Pattern.compile("/app/izvodjenja/" + ID + "/pocetno");
    private static final Pattern STUDENT_ODGOVOR = Pattern.compile("/app/izvodjenja/" + ID + "/odgovor");
    private static final Pattern STUDENT_LICNO = Pattern.compile("/user/queue/(licno|greske)");
    private static final Pattern NASTAVNIK_TOPIK = Pattern.compile("/topic/izvodjenja/" + ID + "/(nastavnik|javno)");
    private static final Pattern NASTAVNIK_POCETNO = Pattern.compile("/app/izvodjenja/" + ID + "/nastavnik-pocetno");

    private final IzbaceniRegistar izbaceni;
    private final LongSupplier satMs;
    private final Map<Long, Kofa> kofe = new ConcurrentHashMap<>();
    /** Pretplate studentskih sesija: id sesije -> (id pretplate -> odredište). */
    private final Map<String, Map<String, String>> pretplate = new ConcurrentHashMap<>();
    /** Aktivne sesije po učesniku (od CONNECT-a do kraja veze). */
    private final Map<Long, Set<String>> sesije = new ConcurrentHashMap<>();
    /** Završene sesije (id -> trenutak kraja, monotoni ms), da zakasneli frame ne zauzme mesto posle kraja veze. */
    private final Map<String, Long> zavrsene = new ConcurrentHashMap<>();

    @Autowired
    public UzivoChannelInterceptor(IzbaceniRegistar izbaceni) {
        this(izbaceni, () -> System.nanoTime() / 1_000_000);
    }

    /** Za test: monotoni sat u milisekundama. */
    UzivoChannelInterceptor(IzbaceniRegistar izbaceni, LongSupplier satMs) {
        this.izbaceni = izbaceni;
        this.satMs = satMs;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        MessageHeaders zaglavlja = message.getHeaders();
        SimpMessageType tip = SimpMessageHeaderAccessor.getMessageType(zaglavlja);
        Map<String, Object> atributi = SimpMessageHeaderAccessor.getSessionAttributes(zaglavlja);
        Uloga uloga = atributi != null && atributi.get(UzivoHandshakeInterceptor.ATR_ULOGA) instanceof Uloga u ? u : null;
        Long ucesnikId = uloga == Uloga.STUDENT && atributi.get(UzivoHandshakeInterceptor.ATR_UCESNIK) instanceof Long l
                ? l : null;
        boolean student = ucesnikId != null;

        if (tip == SimpMessageType.HEARTBEAT) {
            return !student || uzmiToken(ucesnikId) ? message : null;
        }
        StompCommand komanda = StompHeaderAccessor.wrap(message).getCommand();
        if (komanda == StompCommand.DISCONNECT || tip == SimpMessageType.DISCONNECT) {
            // nikad se ne odbacuje (kraj veze mora do brokera) i ne vraća kapacitet (kofa se briše tek kad je puna).
            // Token troši samo DISCONNECT klijenta: StompSubProtocolHandler svakom frame-u klijenta stavi simpHeartbeat,
            // a sintetički DISCONNECT (afterSessionEnded, zatvoren tab ili reload) ga nema i ne naplaćuje se
            if (student && zaglavlja.containsKey(SimpMessageHeaderAccessor.HEART_BEAT_HEADER)) uzmiToken(ucesnikId);
            return message;
        }
        if (uloga == null || komanda == null || (uloga == Uloga.STUDENT && !student)) {
            throw odbij(message, uloga, komanda, null);
        }
        String odrediste = SimpMessageHeaderAccessor.getDestination(zaglavlja);
        return switch (komanda) {
            case CONNECT, STOMP -> {
                if (student) {
                    naplatiIliOdbij(message, ucesnikId, komanda);
                    prijaviSesiju(message, ucesnikId);
                }
                yield message;
            }
            case UNSUBSCRIBE -> {
                if (student) {
                    naplatiIliOdbij(message, ucesnikId, komanda);
                    odjaviPretplatu(message);
                }
                yield message;
            }
            case SUBSCRIBE -> student
                    ? studentPretplata(message, atributi, ucesnikId, odrediste)
                    : nastavnikPretplata(message, odrediste);
            case SEND -> {
                if (!student) throw odbij(message, uloga, komanda, odrediste);
                yield studentSlanje(message, atributi, ucesnikId, odrediste);
            }
            default -> throw odbij(message, uloga, komanda, odrediste);
        };
    }

    private Message<?> nastavnikPretplata(Message<?> m, String odrediste) {
        if (odgovara(NASTAVNIK_TOPIK, odrediste) || odgovara(NASTAVNIK_POCETNO, odrediste)) {
            return m;
        }
        throw odbij(m, Uloga.NASTAVNIK, StompCommand.SUBSCRIBE, odrediste);
    }

    private Message<?> studentPretplata(Message<?> m, Map<String, Object> atributi, Long ucesnikId, String odrediste) {
        Long izvodjenjeId = atributi.get(UzivoHandshakeInterceptor.ATR_IZVODJENJE) instanceof Long l ? l : null;
        boolean dozvoljeno = odgovara(STUDENT_LICNO, odrediste)
                || jeSvoje(STUDENT_JAVNO, odrediste, izvodjenjeId)
                || jeSvoje(STUDENT_POCETNO, odrediste, izvodjenjeId);
        if (!dozvoljeno) throw odbij(m, Uloga.STUDENT, StompCommand.SUBSCRIBE, odrediste);
        String sesija = SimpMessageHeaderAccessor.getSessionId(m.getHeaders());
        String pretplata = SimpMessageHeaderAccessor.getSubscriptionId(m.getHeaders());
        if (sesija == null || pretplata == null) throw odbij(m, Uloga.STUDENT, StompCommand.SUBSCRIBE, odrediste);
        if (zavrsene.containsKey(sesija)) throw zakasneli(m, ucesnikId, StompCommand.SUBSCRIBE);
        Map<String, String> svoje = pretplate.computeIfAbsent(sesija, k -> new ConcurrentHashMap<>());
        synchronized (svoje) {
            if (svoje.containsKey(pretplata) || svoje.containsValue(odrediste) || svoje.size() >= MAX_PRETPLATA) {
                log.info("Pretplata odbijena (dupla ili preko {}): ucesnik={}, odrediste={}", MAX_PRETPLATA, ucesnikId,
                        odrediste);
                throw new MessageDeliveryException(m, "Nedozvoljena poruka.");
            }
            naplatiIliOdbij(m, ucesnikId, StompCommand.SUBSCRIBE);
            svoje.put(pretplata, odrediste);
        }
        // trka sa krajem veze: onPrekid upisuje kraj pre brisanja, pa ako je kraj stigao u međuvremenu, vidi se ovde
        if (zavrsene.containsKey(sesija)) {
            pretplate.remove(sesija);
            throw zakasneli(m, ucesnikId, StompCommand.SUBSCRIBE);
        }
        return m;
    }

    /** Frame sesije koja je već završena (stigao sa pool-a posle {@link SessionDisconnectEvent}): ne zauzima mesto. */
    private static MessageDeliveryException zakasneli(Message<?> m, Long ucesnikId, StompCommand komanda) {
        log.debug("Frame završene sesije odbijen: ucesnik={}, komanda={}", ucesnikId, komanda);
        return new MessageDeliveryException(m, "Veza je završena.");
    }

    private void odjaviPretplatu(Message<?> m) {
        String sesija = SimpMessageHeaderAccessor.getSessionId(m.getHeaders());
        String pretplata = SimpMessageHeaderAccessor.getSubscriptionId(m.getHeaders());
        if (sesija == null || pretplata == null) return;
        Map<String, String> svoje = pretplate.get(sesija);
        if (svoje != null) {
            synchronized (svoje) {
                svoje.remove(pretplata);
            }
        }
    }

    /** CONNECT studenta: nova sesija se upisuje ako učesnik nema već {@value #MAX_SESIJA} aktivne (inače ERROR). */
    private void prijaviSesiju(Message<?> m, Long ucesnikId) {
        String sesija = SimpMessageHeaderAccessor.getSessionId(m.getHeaders());
        if (sesija == null) throw odbij(m, Uloga.STUDENT, StompCommand.CONNECT, null);
        if (zavrsene.containsKey(sesija)) throw zakasneli(m, ucesnikId, StompCommand.CONNECT);
        boolean[] primljena = {false};
        sesije.compute(ucesnikId, (k, skup) -> {
            Set<String> s = skup == null ? ConcurrentHashMap.newKeySet() : skup;
            if (s.contains(sesija) || s.size() < MAX_SESIJA) {
                s.add(sesija);
                primljena[0] = true;
            }
            return s;
        });
        if (!primljena[0]) {
            log.info("Previše otvorenih veza, CONNECT odbijen: ucesnik={}", ucesnikId);
            throw new MessageDeliveryException(m, PREVISE_VEZA);
        }
        // trka sa krajem veze (vidi gore): kraj upisan u međuvremenu -> mesto se odmah vraća
        if (zavrsene.containsKey(sesija)) {
            odjaviSesiju(ucesnikId, sesija);
            throw zakasneli(m, ucesnikId, StompCommand.CONNECT);
        }
    }

    private void odjaviSesiju(Long ucesnikId, String sesija) {
        sesije.computeIfPresent(ucesnikId, (k, skup) -> {
            skup.remove(sesija);
            return skup.isEmpty() ? null : skup;
        });
    }

    /**
     * CONNECT i pretplate preko granice se ne odbacuju tiho (veza bi ostala bez početnog stanja ili bez CONNECTED):
     * izuzetak -> ERROR frame i zatvorena veza, pa se klijent ponovo poveže uz svoj backoff.
     */
    private void naplatiIliOdbij(Message<?> m, Long ucesnikId, StompCommand komanda) {
        if (!uzmiToken(ucesnikId)) {
            log.info("Previše poruka, veza se zatvara: ucesnik={}, komanda={}", ucesnikId, komanda);
            throw new MessageDeliveryException(m, PREVISE_PORUKA);
        }
    }

    private Message<?> studentSlanje(Message<?> m, Map<String, Object> atributi, Long ucesnikId, String odrediste) {
        Long izvodjenjeId = atributi.get(UzivoHandshakeInterceptor.ATR_IZVODJENJE) instanceof Long l ? l : null;
        if (!jeSvoje(STUDENT_ODGOVOR, odrediste, izvodjenjeId)) {
            throw odbij(m, Uloga.STUDENT, StompCommand.SEND, odrediste);
        }
        if (izbaceni.jeIzbacen(ucesnikId)) {
            log.debug("Poruka izbačenog učesnika odbačena: ucesnik={}", ucesnikId);
            return null;
        }
        if (!uzmiToken(ucesnikId)) {
            log.debug("Previše poruka, odbačena: ucesnik={}", ucesnikId);
            return null;
        }
        return m;
    }

    /** Kraj sesije: kofa učesnika se briše samo ako je puna (tada je ista kao nova), pa ponovno povezivanje ne dopunjuje. */
    @EventListener
    public void onPrekid(SessionDisconnectEvent e) {
        String sesija = e.getSessionId();
        long sada = satMs.getAsLong();
        // kraj se upisuje PRE brisanja: zakasneli CONNECT/SUBSCRIBE ga posle svog upisa vidi i sam se povuče
        if (sesija != null) {
            zavrsene.put(sesija, sada);
            pretplate.remove(sesija);
        }
        Map<String, Object> atributi = SimpMessageHeaderAccessor.getSessionAttributes(e.getMessage().getHeaders());
        if (atributi != null && atributi.get(UzivoHandshakeInterceptor.ATR_UCESNIK) instanceof Long ucesnikId) {
            kofe.computeIfPresent(ucesnikId, (k, kofa) -> kofa.puna(sada) ? null : kofa);
            if (sesija != null) odjaviSesiju(ucesnikId, sesija);
        }
    }

    /** Kofe koje su se u međuvremenu napunile (učesnik miruje ili je otišao) se brišu; puna kofa je ista kao nova. */
    @Scheduled(fixedDelay = 60_000)
    public void pocisti() {
        long sada = satMs.getAsLong();
        kofe.entrySet().removeIf(e -> e.getValue().puna(sada));
        // rezerva: ništa što pripada završenoj sesiji ne ostaje upisano
        pretplate.keySet().removeIf(zavrsene::containsKey);
        for (Long ucesnik : sesije.keySet()) {
            sesije.computeIfPresent(ucesnik, (k, skup) -> {
                skup.removeIf(zavrsene::containsKey);
                return skup.isEmpty() ? null : skup;
            });
        }
        zavrsene.values().removeIf(kraj -> sada - kraj > ZAVRSENE_MS);
    }

    private static boolean odgovara(Pattern p, String odrediste) {
        return odrediste != null && p.matcher(odrediste).matches();
    }

    /** Odredište po obrascu sa id-jem baš studentovog izvođenja. */
    private static boolean jeSvoje(Pattern p, String odrediste, Long izvodjenjeId) {
        if (odrediste == null || izvodjenjeId == null) return false;
        Matcher m = p.matcher(odrediste);
        return m.matches() && izvodjenjeId.equals(Long.parseLong(m.group(1)));
    }

    private boolean uzmiToken(Long ucesnikId) {
        return kofe.computeIfAbsent(ucesnikId, s -> new Kofa(satMs.getAsLong())).uzmi(satMs.getAsLong());
    }

    private static MessageDeliveryException odbij(Message<?> m, Uloga uloga, StompCommand komanda, String odrediste) {
        String prikaz = odrediste == null ? null
                : (odrediste.length() > 120 ? odrediste.substring(0, 120) + "..." : odrediste).replaceAll("[\\r\\n]", "?");
        log.info("STOMP poruka odbijena: uloga={}, komanda={}, odrediste={}", uloga, komanda, prikaz);
        return new MessageDeliveryException(m, "Nedozvoljena poruka.");
    }

    /** Broj učesnika sa kofom (za test čišćenja). */
    int brojKofa() {
        return kofe.size();
    }

    /** Broj sesija sa praćenim pretplatama i aktivnih sesija učesnika (za test čišćenja). */
    int brojPracenihSesija() {
        return pretplate.size();
    }

    int brojZavrsenih() {
        return zavrsene.size();
    }

    int brojSesija(Long ucesnikId) {
        Set<String> s = sesije.get(ucesnikId);
        return s == null ? 0 : s.size();
    }

    /** Token bucket jedne sesije: počinje pun, dopuna 5 tokena u sekundi, najviše 10. */
    private static final class Kofa {
        private double tokeni = KAPACITET;
        private long poslednje;

        Kofa(long sada) {
            this.poslednje = sada;
        }

        synchronized boolean puna(long sada) {
            return tokeni + Math.max(0, sada - poslednje) * DOPUNA_PO_MS >= KAPACITET;
        }

        synchronized boolean uzmi(long sada) {
            if (sada > poslednje) {
                tokeni = Math.min(KAPACITET, tokeni + (sada - poslednje) * DOPUNA_PO_MS);
                poslednje = sada;
            }
            if (tokeni >= 1) {
                tokeni -= 1;
                return true;
            }
            return false;
        }
    }
}
