package tri.novica.gfssystem.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import tri.novica.gfssystem.service.uzivo.IzbaceniRegistar;
import tri.novica.gfssystem.service.uzivo.UcesnikIzbacen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Autorizacija STOMP poruka po ulozi (bez Springa): tačna odredišta po regexu, student samo svoje izvođenje, nastavnik
 * ne šalje ništa, token bucket po učesniku (kapacitet 15, dopuna 5/s) i tiho odbacivanje poruka izbačenih učesnika.
 */
class UzivoChannelInterceptorTest {

    final AtomicLong sadaMs = new AtomicLong(1_000_000);
    final MessageChannel kanal = mock(MessageChannel.class);
    IzbaceniRegistar izbaceni;
    UzivoChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        izbaceni = new IzbaceniRegistar();
        interceptor = new UzivoChannelInterceptor(izbaceni, sadaMs::get);
    }

    // ---------------------------------------------------------------- pomoćno

    static Map<String, Object> student(long izvodjenjeId, long ucesnikId) {
        Map<String, Object> a = new HashMap<>();
        a.put("uloga", Uloga.STUDENT);
        a.put("izvodjenjeId", izvodjenjeId);
        a.put("ucesnikId", ucesnikId);
        return a;
    }

    static Map<String, Object> nastavnik() {
        Map<String, Object> a = new HashMap<>();
        a.put("uloga", Uloga.NASTAVNIK);
        return a;
    }

    static Message<byte[]> poruka(StompCommand komanda, String odrediste, String sesija, Map<String, Object> atributi) {
        StompHeaderAccessor h = StompHeaderAccessor.create(komanda);
        h.setSessionId(sesija);
        h.setSessionAttributes(atributi);
        if (odrediste != null) h.setDestination(odrediste);
        if (komanda == StompCommand.SUBSCRIBE) h.setSubscriptionId("sub-0");
        // StompSubProtocolHandler stavlja simpHeartbeat na svaki frame klijenta (sintetički DISCONNECT ga nema)
        h.setHeader(SimpMessageHeaderAccessor.HEART_BEAT_HEADER, new long[]{0, 0});
        return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
    }

    /** DISCONNECT koji Spring sam pravi na kraju veze (afterSessionEnded): bez simpHeartbeat zaglavlja. */
    static Message<byte[]> sintetickiDisconnect(String sesija, Map<String, Object> atributi) {
        StompHeaderAccessor h = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        h.setSessionId(sesija);
        h.setSessionAttributes(atributi);
        return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
    }

    static final int KAP = 15;

    void potrosi(int n, String sesija, Map<String, Object> a) {
        for (int i = 0; i < n; i++) {
            assertNotNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", sesija, a)), "poruka " + (i + 1));
        }
    }

    /** Ponovno povezivanje kao u pravom telefonu: kraj stare veze, CONNECT i četiri pretplate. */
    void ponovoPovezi(String stara, String nova, Map<String, Object> a) {
        assertNotNull(posalji(sintetickiDisconnect(stara, a)));
        interceptor.onPrekid(prekid(stara, a));
        assertNotNull(posalji(poruka(StompCommand.CONNECT, null, nova, a)), "CONNECT");
        for (String o : new String[]{"/topic/izvodjenja/5/javno", "/user/queue/licno", "/user/queue/greske",
                "/app/izvodjenja/5/pocetno"}) {
            assertNotNull(posalji(poruka(StompCommand.SUBSCRIBE, o, nova, a)), o);
        }
    }

    Message<?> posalji(Message<?> m) {
        return interceptor.preSend(m, kanal);
    }

    void prolazi(StompCommand k, String odrediste, Map<String, Object> atributi) {
        Message<byte[]> m = poruka(k, odrediste, "s-" + odrediste, atributi);
        assertSame(m, posalji(m), k + " " + odrediste + " treba da prođe");
    }

    void odbijeno(StompCommand k, String odrediste, Map<String, Object> atributi) {
        Message<byte[]> m = poruka(k, odrediste, "s-" + odrediste, atributi);
        assertThrows(MessageDeliveryException.class, () -> posalji(m), k + " " + odrediste + " treba da bude odbijeno");
    }

    // ---------------------------------------------------------------- student

    @ParameterizedTest
    @ValueSource(strings = {"/topic/izvodjenja/5/javno", "/user/queue/licno", "/user/queue/greske",
            "/app/izvodjenja/5/pocetno"})
    void studentSePretplacujeNaSvojaOdredista(String odrediste) {
        prolazi(StompCommand.SUBSCRIBE, odrediste, student(5, 11));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/izvodjenja/5/nastavnik", "/topic/izvodjenja/6/javno", "/app/izvodjenja/6/pocetno",
            "/topic/izvodjenja/5/javno/../nastavnik", "/topic/izvodjenja/5/javno/", "/topic/izvodjenja/05x/javno",
            "/app/izvodjenja/5/nastavnik-pocetno", "/user/queue/licno/x", "/user/u-12/queue/licno", "/topic/izvodjenja/5/javno\n",
            "/queue/licno", "/topic/izvodjenja/99999999999999999999/javno", ""})
    void studentNeMozeNaTudjaOdredista(String odrediste) {
        odbijeno(StompCommand.SUBSCRIBE, odrediste, student(5, 11));
    }

    @Test
    void studentPretplataBezOdredistaJeOdbijena() {
        odbijeno(StompCommand.SUBSCRIBE, null, student(5, 11));
    }

    @Test
    void studentSaljeOdgovorSamoNaSvojeIzvodjenje() {
        prolazi(StompCommand.SEND, "/app/izvodjenja/5/odgovor", student(5, 11));
        odbijeno(StompCommand.SEND, "/app/izvodjenja/6/odgovor", student(5, 11));
        odbijeno(StompCommand.SEND, "/topic/izvodjenja/5/javno", student(5, 11));
        odbijeno(StompCommand.SEND, "/app/izvodjenja/5/odgovor/", student(5, 11));
        odbijeno(StompCommand.SEND, "/app/izvodjenja/5/pocetno", student(5, 11));
        odbijeno(StompCommand.SEND, "/user/u-11/queue/licno", student(5, 11));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACK", "NACK", "BEGIN", "COMMIT", "ABORT"})
    void transakcijeIPotvrdeSuOdbijene(String komanda) {
        odbijeno(StompCommand.valueOf(komanda), null, student(5, 11));
        odbijeno(StompCommand.valueOf(komanda), null, nastavnik());
    }

    @Test
    void connectUnsubscribeIDisconnectProlaze() {
        prolazi(StompCommand.CONNECT, null, student(5, 11));
        prolazi(StompCommand.STOMP, null, student(5, 11));
        prolazi(StompCommand.UNSUBSCRIBE, null, student(5, 11));
        prolazi(StompCommand.DISCONNECT, null, student(5, 11));
        prolazi(StompCommand.CONNECT, null, nastavnik());
    }

    @Test
    void heartbeatProlazi() {
        SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create(SimpMessageType.HEARTBEAT);
        h.setSessionId("s-hb");
        h.setSessionAttributes(student(5, 11));
        Message<byte[]> m = MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
        assertSame(m, posalji(m));
    }

    // ---------------------------------------------------------------- ograničenje brzine

    @Test
    void sesnaestaPorukaUIstojSekundiSeTihoOdbacuje() {
        Map<String, Object> a = student(5, 11);
        potrosi(KAP, "s1", a);
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a)));
        sadaMs.addAndGet(999);
        // posle 999 ms: dopunjeno 4,995 tokena -> 4 poruke, peta ne
        potrosi(4, "s1", a);
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a)));
        sadaMs.addAndGet(1000);
        Message<byte[]> m = poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a);
        assertSame(m, posalji(m), "posle 1 s opet prolazi");
    }

    @Test
    void dvaPonovnaPovezivanjaZaRedomUnutarSekundeProlaze() {
        Map<String, Object> a = student(5, 11);
        // prvo povezivanje + odgovor
        assertNotNull(posalji(poruka(StompCommand.CONNECT, null, "s1", a)));
        for (String o : new String[]{"/topic/izvodjenja/5/javno", "/user/queue/licno", "/user/queue/greske",
                "/app/izvodjenja/5/pocetno"}) {
            assertNotNull(posalji(poruka(StompCommand.SUBSCRIBE, o, "s1", a)));
        }
        sadaMs.addAndGet(400);
        ponovoPovezi("s1", "s2", a);
        sadaMs.addAndGet(400);
        ponovoPovezi("s2", "s3", a);
        // i odgovor posle toga
        assertNotNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s3", a)));
    }

    @Test
    void sintetickiDisconnectNeTrosiToken() {
        Map<String, Object> a = student(5, 11);
        potrosi(KAP - 1, "s1", a);
        assertNotNull(posalji(sintetickiDisconnect("s1", a)));
        assertNotNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s2", a)), "poslednji token ostao");
    }

    @Test
    void kofaJePoUcesnikuIDeleJeSveNjegoveSesije() {
        Map<String, Object> a = student(5, 11);
        potrosi(8, "s1", a);
        potrosi(7, "s2", a);
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a)));
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s3", a)), "nova sesija, ista kofa");
        // drugi učesnik ima svoju kofu
        assertNotNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s4", student(5, 12))));
        assertEquals(2, interceptor.brojKofa());
    }

    @Test
    void kofaSeNePuniPrekoKapaciteta() {
        Map<String, Object> a = student(5, 11);
        posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a));
        sadaMs.addAndGet(60_000);
        potrosi(KAP, "s1", a);
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a)));
    }

    @Test
    void pretplataPrekoGraniceJeGreskaANeTihoOdbacivanje() {
        Map<String, Object> a = student(5, 11);
        for (int i = 0; i < KAP; i++) {
            assertNotNull(posalji(poruka(StompCommand.SUBSCRIBE, "/app/izvodjenja/5/pocetno", "s1", a)));
        }
        // ERROR frame i zatvorena veza: klijent se ponovo poveže (backoff), umesto da ostane bez početnog stanja
        odbijenoPorukom(poruka(StompCommand.SUBSCRIBE, "/app/izvodjenja/5/pocetno", "s1", a));
        odbijenoPorukom(poruka(StompCommand.CONNECT, null, "s2", a));
        odbijenoPorukom(poruka(StompCommand.UNSUBSCRIBE, null, "s1", a));
        // odgovor se i dalje tiho odbacuje
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a)));
    }

    void odbijenoPorukom(Message<byte[]> m) {
        MessageDeliveryException e = assertThrows(MessageDeliveryException.class, () -> posalji(m));
        assertTrue(e.getMessage().startsWith("Previše poruka."), e.getMessage());
    }

    @Test
    void disconnectSaPotvrdomNeVracaKapacitet() {
        Map<String, Object> a = student(5, 11);
        potrosi(KAP, "s1", a);
        StompHeaderAccessor h = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        h.setSessionId("s1");
        h.setSessionAttributes(a);
        h.setReceipt("77");   // Spring odgovori RECEIPT-om i veza ostaje otvorena
        h.setHeader(SimpMessageHeaderAccessor.HEART_BEAT_HEADER, new long[]{0, 0});
        Message<byte[]> disconnect = MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
        assertSame(disconnect, posalji(disconnect), "DISCONNECT se nikad ne odbacuje");
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a)));
        assertEquals(1, interceptor.brojKofa());
    }

    @Test
    void disconnectKlijentaTrosiToken() {
        Map<String, Object> a = student(5, 11);
        potrosi(KAP - 1, "s1", a);
        posalji(poruka(StompCommand.DISCONNECT, null, "s1", a));
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s2", a)));
    }

    @Test
    void svakiStudentovFrameSeMeri() {
        Map<String, Object> a = student(5, 11);
        for (int i = 0; i < 6; i++) {
            assertNotNull(posalji(poruka(StompCommand.CONNECT, null, "s1", a)));
            assertNotNull(posalji(poruka(StompCommand.UNSUBSCRIBE, null, "s1", a)));
        }
        SimpMessageHeaderAccessor hb = SimpMessageHeaderAccessor.create(SimpMessageType.HEARTBEAT);
        hb.setSessionId("s1");
        hb.setSessionAttributes(a);
        for (int i = 0; i < 3; i++) {
            assertNotNull(posalji(MessageBuilder.createMessage(new byte[0], hb.getMessageHeaders())));
        }
        assertNull(posalji(MessageBuilder.createMessage(new byte[0], hb.getMessageHeaders())));
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", a)));
        // prazna kofa ne odbacuje DISCONNECT (ni klijentov ni sintetički)
        assertNotNull(posalji(poruka(StompCommand.DISCONNECT, null, "s1", a)));
        assertNotNull(posalji(sintetickiDisconnect("s1", a)));
    }

    @Test
    void krajSesijeBriseSamoPunuKofu() {
        Map<String, Object> a = student(5, 11);
        potrosi(KAP, "s1", a);
        interceptor.onPrekid(prekid("s1", a));
        assertEquals(1, interceptor.brojKofa(), "nepuna kofa ostaje: ponovno povezivanje ne dopunjuje");
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s2", a)));

        sadaMs.addAndGet(3_000);
        interceptor.onPrekid(prekid("s2", a));
        assertEquals(0, interceptor.brojKofa(), "puna kofa je ista kao nova");
    }

    @Test
    void ciscenjeBriseSamoPuneKofe() {
        posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", student(5, 11)));
        sadaMs.addAndGet(1_000);
        potrosi(KAP, "s2", student(5, 12));
        interceptor.pocisti();
        assertEquals(1, interceptor.brojKofa());
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s2", student(5, 12))));
    }

    static SessionDisconnectEvent prekid(String sesija, Map<String, Object> atributi) {
        StompHeaderAccessor h = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        h.setSessionId(sesija);
        h.setSessionAttributes(atributi);
        return new SessionDisconnectEvent(new Object(), MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()),
                sesija, CloseStatus.NORMAL);
    }

    @Test
    void nastavnikNemaKofu() {
        for (int i = 0; i < 50; i++) {
            assertNotNull(posalji(poruka(StompCommand.SUBSCRIBE, "/topic/izvodjenja/5/nastavnik", "n1", nastavnik())));
        }
        assertEquals(0, interceptor.brojKofa());
    }

    // ---------------------------------------------------------------- izbačeni

    @Test
    void izbacenUcesnikSeTihoOdbacuje() {
        izbaceni.onIzbacen(new UcesnikIzbacen(5L, 11L));
        assertNull(posalji(poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s1", student(5, 11))));
        Message<byte[]> drugi = poruka(StompCommand.SEND, "/app/izvodjenja/5/odgovor", "s2", student(5, 12));
        assertSame(drugi, posalji(drugi));
    }

    // ---------------------------------------------------------------- nastavnik

    @ParameterizedTest
    @ValueSource(strings = {"/topic/izvodjenja/5/nastavnik", "/topic/izvodjenja/77/javno", "/topic/izvodjenja/1/nastavnik",
            "/app/izvodjenja/42/nastavnik-pocetno"})
    void nastavnikSePretplacuje(String odrediste) {
        prolazi(StompCommand.SUBSCRIBE, odrediste, nastavnik());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/queue/licno", "/app/izvodjenja/5/pocetno", "/topic/izvodjenja/5/nastavnik/x",
            "/topic/izvodjenja//nastavnik", "/topic/izvodjenja/5/../6/nastavnik", "/queue/greske", "/topic/sve"})
    void nastavnikNePretplacujeNestoDrugo(String odrediste) {
        odbijeno(StompCommand.SUBSCRIBE, odrediste, nastavnik());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app/izvodjenja/5/odgovor", "/topic/izvodjenja/5/javno", "/app/izvodjenja/5/nastavnik-pocetno",
            "/queue/licno"})
    void nastavnikNeSaljeNista(String odrediste) {
        odbijeno(StompCommand.SEND, odrediste, nastavnik());
    }

    // ---------------------------------------------------------------- bez uloge

    @Test
    void porukaBezUlogeJeOdbijena() {
        odbijeno(StompCommand.SUBSCRIBE, "/topic/izvodjenja/5/javno", new HashMap<>());
        odbijeno(StompCommand.CONNECT, null, new HashMap<>());
        odbijeno(StompCommand.SEND, "/app/izvodjenja/5/odgovor", null);
        Map<String, Object> pogresnaUloga = new HashMap<>();
        pogresnaUloga.put("uloga", "NASTAVNIK");
        odbijeno(StompCommand.SUBSCRIBE, "/topic/izvodjenja/5/nastavnik", pogresnaUloga);
    }

    @Test
    void studentBezIzvodjenjaJeOdbijen() {
        Map<String, Object> a = student(5, 11);
        a.remove("izvodjenjeId");
        odbijeno(StompCommand.SUBSCRIBE, "/topic/izvodjenja/5/javno", a);
    }

    @Test
    void studentBezUcesnikaJeOdbijen() {
        Map<String, Object> a = student(5, 11);
        a.remove("ucesnikId");
        odbijeno(StompCommand.SUBSCRIBE, "/topic/izvodjenja/5/javno", a);
        odbijeno(StompCommand.CONNECT, null, a);
    }
}
