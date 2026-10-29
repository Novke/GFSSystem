package tri.novica.gfssystem.service.push;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sadržaj push poruke (ugovor API-ja, "Sadržaj push poruke"): JSON {@code {naslov, tekst, putanja, oznaka}} koji service
 * worker prikazuje. {@code putanja} je relativna putanja aplikacije ({@code nalog}, {@code nalog/cas},
 * {@code nalog/predmet/<id>}, {@code raspored/termini?od=<datum>}, {@code ?obavestenja=otvori} ili prazna), nikad pun URL;
 * {@code oznaka} zamenjuje staro obaveštenje iste oznake na telefonu. Vidi se na zaključanom ekranu, pa nikad ne nosi
 * ocene, imena drugih studenata, razloge odsustva ni tekst koji je student ukucao (sastavljači su u {@code service.push}).
 */
public record PushPoruka(String naslov, String tekst, String putanja, String oznaka) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public PushPoruka {
        naslov = naslov == null ? "" : naslov;
        tekst = tekst == null ? "" : tekst;
        putanja = putanja == null ? "" : putanja;
        oznaka = oznaka == null ? "" : oznaka;
    }

    /** JSON u tačno ovom redosledu polja (UTF-8). */
    public byte[] json() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("naslov", naslov);
        m.put("tekst", tekst);
        m.put("putanja", putanja);
        m.put("oznaka", oznaka);
        return JSON.writeValueAsString(m).getBytes(StandardCharsets.UTF_8);
    }
}
