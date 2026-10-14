package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import tri.novica.gfssystem.config.Uloga;
import tri.novica.gfssystem.config.UzivoHandshakeInterceptor;
import tri.novica.gfssystem.dto.uzivo.NastavnickoStanje;
import tri.novica.gfssystem.dto.uzivo.OdgovorCmd;
import tri.novica.gfssystem.dto.uzivo.PocetnoStanje;
import tri.novica.gfssystem.service.uzivo.OdgovorOdbijen;
import tri.novica.gfssystem.service.uzivo.OdgovorService;
import tri.novica.gfssystem.service.uzivo.StanjeService;

import java.util.Map;

/**
 * STOMP ulazi (spec 4.3); ko sme šta proverava {@code UzivoChannelInterceptor} pre ovoga, a ovde se učesnik i izvođenje
 * uzimaju samo iz atributa sesije (rukovanje), nikad iz poruke. Odbijen odgovor i svaka greška idu samo pošiljaocu
 * na {@code /user/queue/greske} kao {@code {poruka}}; tekst izuzetka ide samo u log.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class UzivoStompController {

    static final String ODGOVOR_NIJE_PRIMLJEN = "Odgovor nije primljen. Pokušaj ponovo.";
    static final String STANJE_NIJE_DOSTUPNO = "Stanje trenutno nije dostupno. Pokušaj ponovo.";

    private final StanjeService stanjeService;
    private final OdgovorService odgovorService;

    /** Prvi snimak posle (ponovnog) povezivanja telefona: javno i lično stanje iz istog čitanja. */
    @SubscribeMapping("/izvodjenja/{id}/pocetno")
    public PocetnoStanje pocetno(@DestinationVariable Long id, SimpMessageHeaderAccessor h) {
        return stanjeService.pocetno(id, ucesnik(id, h));
    }

    /** Prvi snimak za konzolu i prikaz za publiku (nastavnički ulaz). */
    @SubscribeMapping("/izvodjenja/{id}/nastavnik-pocetno")
    public NastavnickoStanje nastavnikPocetno(@DestinationVariable Long id, SimpMessageHeaderAccessor h) {
        if (atribut(h, UzivoHandshakeInterceptor.ATR_ULOGA) != Uloga.NASTAVNIK) {
            throw new IllegalStateException("nastavnik-pocetno van nastavničke sesije");
        }
        return stanjeService.nastavnicko(id);
    }

    @MessageMapping("/izvodjenja/{id}/odgovor")
    public void odgovor(@DestinationVariable Long id, @Payload OdgovorCmd cmd, SimpMessageHeaderAccessor h) {
        odgovorService.odgovori(id, ucesnik(id, h), cmd);
    }

    @MessageExceptionHandler(OdgovorOdbijen.class)
    @SendToUser(destinations = "/queue/greske", broadcast = false)
    public Map<String, String> odbijen(OdgovorOdbijen e) {
        return Map.of("poruka", e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/greske", broadcast = false)
    public Map<String, String> greska(Exception e, SimpMessageHeaderAccessor h) {
        String odrediste = h.getDestination();
        if (e instanceof MessageConversionException || e instanceof MethodArgumentTypeMismatchException) {
            log.warn("STOMP poruka nečitljiva: odrediste={}, greska={}", odrediste, e.getClass().getSimpleName());
        } else {
            log.warn("STOMP greška: odrediste={}", odrediste, e);
        }
        boolean odgovor = odrediste != null && odrediste.endsWith("/odgovor");
        return Map.of("poruka", odgovor ? ODGOVOR_NIJE_PRIMLJEN : STANJE_NIJE_DOSTUPNO);
    }

    /** Učesnik iz sesije, samo za izvođenje sesije (interceptor to već proverava; ovde druga brana). */
    private static Long ucesnik(Long izvodjenjeId, SimpMessageHeaderAccessor h) {
        if (atribut(h, UzivoHandshakeInterceptor.ATR_ULOGA) == Uloga.STUDENT
                && atribut(h, UzivoHandshakeInterceptor.ATR_UCESNIK) instanceof Long ucesnikId
                && izvodjenjeId != null
                && izvodjenjeId.equals(atribut(h, UzivoHandshakeInterceptor.ATR_IZVODJENJE))) {
            return ucesnikId;
        }
        throw new OdgovorOdbijen(OdgovorService.NIJE_PRIJAVLJEN);
    }

    private static Object atribut(SimpMessageHeaderAccessor h, String kljuc) {
        Map<String, Object> atributi = h.getSessionAttributes();
        return atributi == null ? null : atributi.get(kljuc);
    }
}
