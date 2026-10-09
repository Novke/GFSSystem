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
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.service.uzivo.IzbaceniRegistar;

import java.util.Map;
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
 * ili novi red ne prolaze. Studentove pretplate i slanja troše token bucket po sesiji (kapacitet 10, dopuna 5/s): višak
 * i poruke izbačenih učesnika se tiho odbacuju ({@code null}). ACK/NACK i transakcije nisu dozvoljeni nikome.
 */
@Component
@Slf4j
public class UzivoChannelInterceptor implements ChannelInterceptor {

    static final double KAPACITET = 10;
    static final double DOPUNA_PO_MS = 5 / 1000.0;

    private static final String ID = "(\\d{1,18})";
    private static final Pattern STUDENT_JAVNO = Pattern.compile("/topic/izvodjenja/" + ID + "/javno");
    private static final Pattern STUDENT_POCETNO = Pattern.compile("/app/izvodjenja/" + ID + "/pocetno");
    private static final Pattern STUDENT_ODGOVOR = Pattern.compile("/app/izvodjenja/" + ID + "/odgovor");
    private static final Pattern STUDENT_LICNO = Pattern.compile("/user/queue/(licno|greske)");
    private static final Pattern NASTAVNIK_TOPIK = Pattern.compile("/topic/izvodjenja/" + ID + "/(nastavnik|javno)");
    private static final Pattern NASTAVNIK_POCETNO = Pattern.compile("/app/izvodjenja/" + ID + "/nastavnik-pocetno");

    private final IzbaceniRegistar izbaceni;
    private final LongSupplier satMs;
    private final Map<String, Kofa> kofe = new ConcurrentHashMap<>();

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
        String sesija = SimpMessageHeaderAccessor.getSessionId(zaglavlja);
        if (tip == SimpMessageType.HEARTBEAT) {
            return message;
        }
        StompCommand komanda = StompHeaderAccessor.wrap(message).getCommand();
        if (komanda == StompCommand.DISCONNECT || tip == SimpMessageType.DISCONNECT) {
            if (sesija != null) kofe.remove(sesija);
            return message;
        }

        Map<String, Object> atributi = SimpMessageHeaderAccessor.getSessionAttributes(zaglavlja);
        Uloga uloga = atributi != null && atributi.get(UzivoHandshakeInterceptor.ATR_ULOGA) instanceof Uloga u ? u : null;
        if (uloga == null || komanda == null) {
            throw odbij(message, uloga, komanda, null);
        }
        String odrediste = SimpMessageHeaderAccessor.getDestination(zaglavlja);
        return switch (komanda) {
            case CONNECT, STOMP, UNSUBSCRIBE -> message;
            case SUBSCRIBE -> uloga == Uloga.NASTAVNIK
                    ? nastavnikPretplata(message, odrediste)
                    : studentPretplata(message, atributi, sesija, odrediste);
            case SEND -> {
                if (uloga == Uloga.NASTAVNIK) throw odbij(message, uloga, komanda, odrediste);
                yield studentSlanje(message, atributi, sesija, odrediste);
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

    private Message<?> studentPretplata(Message<?> m, Map<String, Object> atributi, String sesija, String odrediste) {
        Long izvodjenjeId = atributi.get(UzivoHandshakeInterceptor.ATR_IZVODJENJE) instanceof Long l ? l : null;
        boolean dozvoljeno = odgovara(STUDENT_LICNO, odrediste)
                || jeSvoje(STUDENT_JAVNO, odrediste, izvodjenjeId)
                || jeSvoje(STUDENT_POCETNO, odrediste, izvodjenjeId);
        if (!dozvoljeno) throw odbij(m, Uloga.STUDENT, StompCommand.SUBSCRIBE, odrediste);
        return uzmiToken(sesija) ? m : null;
    }

    private Message<?> studentSlanje(Message<?> m, Map<String, Object> atributi, String sesija, String odrediste) {
        Long izvodjenjeId = atributi.get(UzivoHandshakeInterceptor.ATR_IZVODJENJE) instanceof Long l ? l : null;
        if (!jeSvoje(STUDENT_ODGOVOR, odrediste, izvodjenjeId)) {
            throw odbij(m, Uloga.STUDENT, StompCommand.SEND, odrediste);
        }
        Long ucesnikId = atributi.get(UzivoHandshakeInterceptor.ATR_UCESNIK) instanceof Long l ? l : null;
        if (izbaceni.jeIzbacen(ucesnikId)) {
            log.debug("Poruka izbačenog učesnika odbačena: ucesnik={}", ucesnikId);
            return null;
        }
        if (!uzmiToken(sesija)) {
            log.debug("Previše poruka, odbačena: sesija={}", sesija);
            return null;
        }
        return m;
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

    private boolean uzmiToken(String sesija) {
        if (sesija == null) return false;
        return kofe.computeIfAbsent(sesija, s -> new Kofa(satMs.getAsLong())).uzmi(satMs.getAsLong());
    }

    private static MessageDeliveryException odbij(Message<?> m, Uloga uloga, StompCommand komanda, String odrediste) {
        String prikaz = odrediste == null ? null
                : (odrediste.length() > 120 ? odrediste.substring(0, 120) + "..." : odrediste).replaceAll("[\\r\\n]", "?");
        log.info("STOMP poruka odbijena: uloga={}, komanda={}, odrediste={}", uloga, komanda, prikaz);
        return new MessageDeliveryException(m, "Nedozvoljena poruka.");
    }

    /** Broj sesija sa kofom (za test čišćenja). */
    int brojKofa() {
        return kofe.size();
    }

    /** Token bucket jedne sesije: počinje pun, dopuna 5 tokena u sekundi, najviše 10. */
    private static final class Kofa {
        private double tokeni = KAPACITET;
        private long poslednje;

        Kofa(long sada) {
            this.poslednje = sada;
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
