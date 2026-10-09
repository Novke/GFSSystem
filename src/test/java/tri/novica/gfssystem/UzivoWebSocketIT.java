package tri.novica.gfssystem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uživo preko pravog STOMP klijenta i prave MySQL baze (kao CI): rukovanje sa kolačićem i bez njega, pretplate po
 * ulozi, "ni ranije" (u CEKA ni tekst ni opcije, sa DUGMAD i bez Detalja tekst nikad ne stiže), odgovor i lično
 * stanje, nastavničko stanje posle odgovora, odbijeni odgovori na {@code /user/queue/greske}, ponovno povezivanje
 * ("primljen" ostaje), TACAN i kraj. Bez {@code @Transactional}: sve se zaista commit-uje, test briše svoje podatke.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UzivoWebSocketIT {

    static final String ORIGIN = "http://localhost:4200";
    static final String PITANJE_TEKST = "Koliko je dva plus dva?";
    static final long ROK_MS = 1000;
    static final long CEKANJE_MS = 2000;

    @LocalServerPort int port;
    @Autowired JsonMapper jsonMapper;
    @Autowired PredmetRepository predmetRepository;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired JdbcTemplate jdbc;

    RestClient rest;
    WebSocketStompClient stomp;
    ThreadPoolTaskScheduler klijentSat;
    final List<Klijent> klijenti = new ArrayList<>();
    Predmet predmet;
    Long prezentacijaId;

    @BeforeEach
    void setUp() {
        rest = RestClient.create("http://localhost:" + port);
        klijentSat = new ThreadPoolTaskScheduler();
        klijentSat.setPoolSize(1);
        klijentSat.setThreadNamePrefix("it-stomp-");
        klijentSat.initialize();
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter(jsonMapper));
        stomp.setTaskScheduler(klijentSat);
        stomp.setDefaultHeartbeat(new long[]{10_000, 10_000});
        stomp.setInboundMessageSizeLimit(256 * 1024);

        predmet = new Predmet();
        predmet.setNaziv("Uživo WS " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
    }

    @AfterEach
    void tearDown() {
        klijenti.forEach(Klijent::zatvori);
        stomp.stop();
        klijentSat.shutdown();
        if (prezentacijaId != null) {
            jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?",
                    prezentacijaId);
            try {
                prezentacijaService.obrisi(prezentacijaId);
            } catch (SystemException e) {
                // već obrisana
            }
        }
        predmetRepository.deleteById(predmet.getId());
    }

    @Test
    void tokIzvodjenjaKrozWebSocket() throws Exception {
        // ---- priprema: INFO + JEDAN_TACAN (bez vremena) + KRATAK_TEKST, DUGMAD, bez Detalja, takmičenje
        prezentacijaId = post("/prezentacije", Map.of("predmetId", predmet.getId(), "naziv", "WS test")).path("id").asLong();
        post("/prezentacije/" + prezentacijaId + "/slajdovi", Map.of("tip", "INFO", "naslov", "Uvod", "postepeno", false));
        Map<String, Object> pitanje = new HashMap<>();
        pitanje.put("tip", "JEDAN_TACAN");
        pitanje.put("tekst", PITANJE_TEKST);
        pitanje.put("vremeSekunde", null);
        pitanje.put("opcije", List.of(Map.of("tekst", "Četiri", "tacna", true), Map.of("tekst", "Pet", "tacna", false)));
        post("/prezentacije/" + prezentacijaId + "/slajdovi", Map.of("tip", "PITANJE", "pitanje", pitanje));
        post("/prezentacije/" + prezentacijaId + "/slajdovi", Map.of("tip", "PITANJE", "pitanje",
                Map.of("tip", "KRATAK_TEKST", "tekst", "Glavni grad Srbije?", "prihvatljiviOdgovori", List.of("Beograd"))));
        put("/prezentacije/" + prezentacijaId, Map.of("naziv", "WS test", "opis", "", "takmicenje", true,
                "telefonPrikaz", "DUGMAD", "detaljiDozvoljeni", false));

        // ---- 1. pokretanje i dva studenta
        JsonNode izvodjenje = post("/prezentacije/" + prezentacijaId + "/izvodjenja", Map.of("cuvanje", false));
        long id = izvodjenje.path("id").asLong();
        String kod = izvodjenje.path("kod").asString();
        Prijava ana = prijavi(kod, "Ana");
        Prijava bojan = prijavi(kod, "Bojan");
        assertEquals(id, ana.izvodjenjeId());

        // ---- 2. rukovanje: bez kolačića i sa lošim kolačićem ne, sa kolačićem da; nastavnik na /ws
        assertRukovanjeOdbijeno("/public/ws", null);
        assertRukovanjeOdbijeno("/public/ws", "gfs_uzivo=" + "x".repeat(32));
        Klijent nastavnik = povezi("/ws", null);
        String javno = "/topic/izvodjenja/" + id + "/javno";
        String nastavnickoOdr = "/topic/izvodjenja/" + id + "/nastavnik";
        nastavnik.pretplati(nastavnickoOdr);
        nastavnik.pretplati("/app/izvodjenja/" + id + "/nastavnik-pocetno");
        JsonNode nPocetno = nastavnik.cekaj("/app/izvodjenja/" + id + "/nastavnik-pocetno", n -> true, CEKANJE_MS);
        assertEquals("PRIJAVA", nPocetno.path("prikaz").asString());
        assertEquals(2, nPocetno.path("ucesnici").size());
        assertEquals(0, nPocetno.path("brojPovezanih").asInt());

        Klijent a = povezi("/public/ws", ana.kolacic());
        Klijent b = povezi("/public/ws", bojan.kolacic());
        assertArrayEquals(new long[]{10_000, 10_000}, a.heartbeatServera, "heartbeat 10 s / 10 s");

        // ---- 3. student A: javno + lično + greške + početno
        for (Klijent k : List.of(a, b)) {
            k.pretplati(javno);
            k.pretplati("/user/queue/licno");
            k.pretplati("/user/queue/greske");
            k.pretplati("/app/izvodjenja/" + id + "/pocetno");
        }
        JsonNode pocetnoA = a.cekaj("/app/izvodjenja/" + id + "/pocetno", n -> true, CEKANJE_MS);
        assertEquals("PRIJAVA", pocetnoA.path("javno").path("prikaz").asString());
        assertEquals(2, pocetnoA.path("javno").path("brojUcesnika").asInt());
        assertEquals(ana.ucesnikId(), pocetnoA.path("licno").path("ucesnikId").asLong());
        b.cekaj("/app/izvodjenja/" + id + "/pocetno", n -> true, CEKANJE_MS);
        // povezanost stiže u nastavničko stanje kroz flush
        nastavnik.cekaj(nastavnickoOdr, n -> n.path("brojPovezanih").asInt() == 2, CEKANJE_MS);

        // ---- 4. INFO, pa pitanje u CEKA: ni tekst ni opcije; OTVORI: id-jevi opcija bez teksta
        komanda(id, "SLEDECI");
        komanda(id, "SLEDECI");
        JsonNode ceka = a.cekaj(javno, n -> "CEKA".equals(n.path("pitanje").path("faza").asString()), CEKANJE_MS);
        assertEquals("JEDAN_TACAN", ceka.path("pitanje").path("tip").asString());
        assertPrazno(ceka.path("pitanje").path("opcije"));
        assertPrazno(ceka.path("pitanje").path("tekst"));
        assertPrazno(ceka.path("pitanje").path("rundaId"));

        JsonNode otvoreno = komanda(id, "SLEDECI");
        assertEquals("OTVORENO", otvoreno.path("faza").asString());
        long rundaId = otvoreno.path("runda").path("id").asLong();
        long tacnaOpcija = -1;
        long netacnaOpcija = -1;
        for (JsonNode o : otvoreno.path("trenutniSlajd").path("pitanje").path("opcije")) {
            if (o.path("tacna").asBoolean()) tacnaOpcija = o.path("id").asLong();
            else netacnaOpcija = o.path("id").asLong();
        }
        for (Klijent k : List.of(a, b)) {
            JsonNode s = k.cekaj(javno, n -> "OTVORENO".equals(n.path("pitanje").path("faza").asString()), ROK_MS);
            JsonNode p = s.path("pitanje");
            assertEquals(rundaId, p.path("rundaId").asLong());
            assertEquals(2, p.path("opcije").size());
            List<Long> ids = new ArrayList<>();
            for (JsonNode o : p.path("opcije")) {
                ids.add(o.path("id").asLong());
                assertPrazno(o.path("tekst"));
            }
            assertTrue(ids.contains(tacnaOpcija) && ids.contains(netacnaOpcija));
            assertPrazno(p.path("tekst"));
            assertPrazno(p.path("tacneOpcije"));
        }

        // ---- 5. A odgovara tačno: lično "primljen", nastavnik broj odgovora; ponovo -> "Već si odgovorio."
        a.posalji("/app/izvodjenja/" + id + "/odgovor", Map.of("rundaId", rundaId, "opcije", List.of(tacnaOpcija)));
        JsonNode licnoA = a.cekaj("/user/queue/licno",
                n -> n.path("odgovor").path("primljen").asBoolean() && n.path("odgovor").path("rundaId").asLong() == rundaId,
                ROK_MS);
        assertPrazno(licnoA.path("odgovor").path("tacno"));
        assertPrazno(licnoA.path("odgovor").path("poeni"));
        assertEquals(0, licnoA.path("poeni").asInt(), "poeni trenutne runde tek posle TACAN");
        nastavnik.cekaj(nastavnickoOdr, n -> n.path("brojOdgovora").asInt() == 1, ROK_MS);
        // B nije odgovorio: njemu ne stiže lično "primljen"
        assertNull(b.nadji("/user/queue/licno", n -> n.path("odgovor").path("primljen").asBoolean()));

        a.posalji("/app/izvodjenja/" + id + "/odgovor", Map.of("rundaId", rundaId, "opcije", List.of(netacnaOpcija)));
        assertEquals("Već si odgovorio.", a.cekaj("/user/queue/greske", n -> true, CEKANJE_MS).path("poruka").asString());
        // greška ide samo pošiljaocu
        assertNull(b.nadji("/user/queue/greske", n -> true));

        // ---- 6. B ne sme na nastavnički topik ni na tuđe izvođenje (posebne veze; ERROR zatvara vezu)
        Klijent b2 = povezi("/public/ws", bojan.kolacic());
        b2.pretplati(nastavnickoOdr);
        b2.cekajPrekid();
        assertNull(b2.nadji(nastavnickoOdr, n -> true), "nastavničko stanje ne sme stići studentu");
        Klijent b3 = povezi("/public/ws", bojan.kolacic());
        b3.posalji("/app/izvodjenja/" + (id + 1000) + "/odgovor", Map.of("rundaId", rundaId, "opcije", List.of(tacnaOpcija)));
        b3.cekajPrekid();
        Klijent b4 = povezi("/public/ws", bojan.kolacic());
        b4.pretplati("/app/izvodjenja/" + id + "/nastavnik-pocetno");
        b4.cekajPrekid();

        // ---- 7. zatvori; B odgovara posle zatvaranja -> "Pitanje je zatvoreno."
        komanda(id, "SLEDECI");
        b.cekaj(javno, n -> "ZATVORENO".equals(n.path("pitanje").path("faza").asString()), CEKANJE_MS);
        b.posalji("/app/izvodjenja/" + id + "/odgovor", Map.of("rundaId", rundaId, "opcije", List.of(tacnaOpcija)));
        assertEquals("Pitanje je zatvoreno.", b.cekaj("/user/queue/greske", n -> true, CEKANJE_MS).path("poruka").asString());

        // ---- 8. A prekine vezu i ponovo se poveže sa istim kolačićem: "primljen" ostaje, faza ZATVORENO
        a.zatvori();
        nastavnik.cekaj(nastavnickoOdr, n -> n.path("brojPovezanih").asInt() == 1, CEKANJE_MS);
        Klijent a2 = povezi("/public/ws", ana.kolacic());
        a2.pretplati(javno);
        a2.pretplati("/user/queue/licno");
        a2.pretplati("/user/queue/greske");
        a2.pretplati("/app/izvodjenja/" + id + "/pocetno");
        JsonNode ponovo = a2.cekaj("/app/izvodjenja/" + id + "/pocetno", n -> true, CEKANJE_MS);
        assertTrue(ponovo.path("licno").path("odgovor").path("primljen").asBoolean());
        assertEquals(rundaId, ponovo.path("licno").path("odgovor").path("rundaId").asLong());
        assertEquals("ZATVORENO", ponovo.path("javno").path("pitanje").path("faza").asString());

        // ---- 9. TACAN: A vidi da je tačno i poene, javno tačna opcija
        komanda(id, "TACAN");
        JsonNode posleTacnog = a2.cekaj("/user/queue/licno", n -> n.path("odgovor").path("tacno").asBoolean(), CEKANJE_MS);
        assertEquals(1000, posleTacnog.path("odgovor").path("poeni").asInt());
        assertEquals(1000, posleTacnog.path("poeni").asInt());
        JsonNode javnoTacno = a2.cekaj(javno, n -> n.path("pitanje").path("tacneOpcije").size() == 1, CEKANJE_MS);
        assertEquals(tacnaOpcija, javnoTacno.path("pitanje").path("tacneOpcije").path(0).asLong());
        JsonNode licnoB = b.cekaj("/user/queue/licno", n -> n.path("verzija").asLong() == posleTacnog.path("verzija").asLong(),
                CEKANJE_MS);
        assertFalse(licnoB.path("odgovor").path("primljen").asBoolean());

        // ---- 10. ZAVRSI: javno ZAVRSENO, novo rukovanje sa kolačićem odbijeno
        komanda(id, "ZAVRSI");
        a2.cekaj(javno, n -> "ZAVRSENO".equals(n.path("status").asString()), CEKANJE_MS);
        b.cekaj(javno, n -> "ZAVRSENO".equals(n.path("status").asString()), CEKANJE_MS);
        assertRukovanjeOdbijeno("/public/ws", ana.kolacic());

        // ---- "ni ranije": sa DUGMAD i bez Detalja tekst pitanja nijednom nije stigao na telefon
        for (Klijent k : List.of(a, a2, b)) {
            for (String json : k.sirovo()) {
                assertFalse(json.contains(PITANJE_TEKST), "tekst pitanja je stigao na telefon: " + json);
                assertFalse(json.contains("Četiri"), "tekst opcije je stigao na telefon: " + json);
            }
        }
    }

    @Test
    void rukovanjeSaTudjimOriginomJeOdbijeno() throws Exception {
        WebSocketHttpHeaders h = new WebSocketHttpHeaders();
        h.setOrigin("http://zlonamerni.example");
        ExecutionException e = assertThrows(ExecutionException.class, () -> stomp
                .connectAsync("ws://localhost:" + port + "/ws", h, new StompHeaders(), new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS));
        assertNotNull(e.getCause());
    }

    // ---------------------------------------------------------------- REST

    JsonNode post(String putanja, Object telo) {
        String odgovor = rest.post().uri(putanja).contentType(MediaType.APPLICATION_JSON).body(telo)
                .retrieve().body(String.class);
        return odgovor == null ? jsonMapper.nullNode() : jsonMapper.readTree(odgovor);
    }

    JsonNode put(String putanja, Object telo) {
        return jsonMapper.readTree(rest.put().uri(putanja).contentType(MediaType.APPLICATION_JSON).body(telo)
                .retrieve().body(String.class));
    }

    JsonNode komanda(long id, String tip) {
        return post("/izvodjenja/" + id + "/komande", Map.of("tip", tip));
    }

    record Prijava(long ucesnikId, long izvodjenjeId, String kolacic) {
    }

    Prijava prijavi(String kod, String ime) {
        ResponseEntity<String> r = rest.post().uri("/public/uzivo/" + kod + "/prijava")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("ime", ime))
                .retrieve().toEntity(String.class);
        String setCookie = r.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        String kolacic = setCookie.split(";", 2)[0];
        assertTrue(kolacic.startsWith("gfs_uzivo="));
        JsonNode telo = jsonMapper.readTree(r.getBody());
        return new Prijava(telo.path("ucesnikId").asLong(), telo.path("izvodjenjeId").asLong(), kolacic);
    }

    // ---------------------------------------------------------------- STOMP

    WebSocketHttpHeaders zaglavlja(String kolacic) {
        WebSocketHttpHeaders h = new WebSocketHttpHeaders();
        h.setOrigin(ORIGIN);
        if (kolacic != null) h.add(HttpHeaders.COOKIE, kolacic);
        return h;
    }

    Klijent povezi(String ulaz, String kolacic) throws Exception {
        Klijent k = new Klijent();
        k.sesija = stomp.connectAsync("ws://localhost:" + port + ulaz, zaglavlja(kolacic), new StompHeaders(), k)
                .get(5, TimeUnit.SECONDS);
        klijenti.add(k);
        return k;
    }

    void assertRukovanjeOdbijeno(String ulaz, String kolacic) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> stomp
                .connectAsync("ws://localhost:" + port + ulaz, zaglavlja(kolacic), new StompHeaders(),
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS));
        assertNotNull(e.getCause());
    }

    static void assertPrazno(JsonNode n) {
        assertTrue(n.isMissingNode() || n.isNull(), "očekivano prazno, a stiglo: " + n);
    }

    /** Jedna STOMP veza: poruke po odredištu u redovima, ERROR frame i prekid se beleže. */
    final class Klijent extends StompSessionHandlerAdapter {
        StompSession sesija;
        long[] heartbeatServera;
        final Map<String, BlockingQueue<JsonNode>> poruke = new ConcurrentHashMap<>();
        final List<String> sveSirove = new java.util.concurrent.CopyOnWriteArrayList<>();
        final BlockingQueue<String> prekidi = new LinkedBlockingQueue<>();

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            heartbeatServera = connectedHeaders.getHeartbeat();
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return JsonNode.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            prekidi.add("ERROR " + headers.getFirst("message"));
        }

        @Override
        public void handleException(StompSession s, StompCommand command, StompHeaders headers, byte[] payload,
                                    Throwable exception) {
            prekidi.add("izuzetak " + command + " " + exception);
        }

        @Override
        public void handleTransportError(StompSession s, Throwable exception) {
            prekidi.add("transport " + exception);
        }

        void pretplati(String odrediste) {
            BlockingQueue<JsonNode> red = poruke.computeIfAbsent(odrediste, k -> new LinkedBlockingQueue<>());
            sesija.subscribe(odrediste, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    JsonNode n = (JsonNode) payload;
                    sveSirove.add(n.toString());
                    red.add(n);
                }
            });
        }

        void posalji(String odrediste, Object telo) {
            sesija.send(odrediste, telo);
        }

        /** Prva poruka sa odredišta koja zadovoljava uslov (starije se preskaču) u roku. */
        JsonNode cekaj(String odrediste, Predicate<JsonNode> uslov, long rokMs) throws InterruptedException {
            BlockingQueue<JsonNode> red = poruke.get(odrediste);
            assertNotNull(red, "nema pretplate na " + odrediste);
            long kraj = System.currentTimeMillis() + rokMs;
            while (true) {
                long ostalo = kraj - System.currentTimeMillis();
                JsonNode n = ostalo <= 0 ? null : red.poll(ostalo, TimeUnit.MILLISECONDS);
                if (n == null) fail("Na " + odrediste + " nije stiglo očekivano stanje za " + rokMs + " ms");
                if (uslov.test(n)) return n;
            }
        }

        /** Poruka koja već jeste stigla i zadovoljava uslov (posle kratkog čekanja), bez skidanja iz reda. */
        JsonNode nadji(String odrediste, Predicate<JsonNode> uslov) throws InterruptedException {
            Thread.sleep(400);
            BlockingQueue<JsonNode> red = poruke.get(odrediste);
            return red == null ? null : red.stream().filter(uslov).findFirst().orElse(null);
        }

        /** Server je poslao ERROR frame i/ili zatvorio vezu. */
        void cekajPrekid() throws InterruptedException {
            String razlog = prekidi.poll(CEKANJE_MS, TimeUnit.MILLISECONDS);
            assertNotNull(razlog, "očekivan ERROR frame ili prekid veze");
            long kraj = System.currentTimeMillis() + CEKANJE_MS;
            while (sesija.isConnected() && System.currentTimeMillis() < kraj) {
                Thread.sleep(20);
            }
            assertFalse(sesija.isConnected(), "server treba da zatvori vezu posle ERROR frame-a");
        }

        List<String> sirovo() {
            return sveSirove;
        }

        void zatvori() {
            if (sesija != null && sesija.isConnected()) sesija.disconnect();
        }
    }
}
