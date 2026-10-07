package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.uzivo.RangStavka;
import tri.novica.gfssystem.entity.uzivo.Odgovor;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RangListaTest {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 7, 10, 0);

    static Ucesnik ucesnik(long id, String ime, int minutaPrijave, boolean izbacen) {
        Ucesnik u = new Ucesnik();
        u.setId(id);
        u.setIme(ime);
        u.setIzbacen(izbacen);
        u.setKreirano(T0.plusMinutes(minutaPrijave));
        return u;
    }

    static PitanjeRunda runda(long id, Long slajdId, int redniBroj) {
        PitanjeRunda r = new PitanjeRunda();
        r.setId(id);
        r.setSlajdId(slajdId);
        r.setRedniBroj(redniBroj);
        return r;
    }

    static Odgovor odgovor(PitanjeRunda r, Ucesnik u, int poeni, long vremeMs) {
        Odgovor o = new Odgovor();
        o.setRunda(r);
        o.setUcesnik(u);
        o.setPoeni(poeni);
        o.setVremeMs(vremeMs);
        o.setKreirano(T0);
        return o;
    }

    @Test void zbirIzRazlicitihSlajdova() {
        Ucesnik a = ucesnik(1, "Ana", 0, false);
        Ucesnik b = ucesnik(2, "Bora", 1, false);
        PitanjeRunda r1 = runda(1, 10L, 1), r2 = runda(2, 11L, 2);
        List<Odgovor> o = List.of(odgovor(r1, a, 1000, 100), odgovor(r2, a, 500, 100), odgovor(r1, b, 700, 100));
        assertEquals(Map.of(1L, 1500, 2L, 700), RangLista.poeniPoUcesniku(o));
        List<RangStavka> rang = RangLista.izracunaj(List.of(a, b), o);
        assertEquals(List.of(new RangStavka(1, 1L, "Ana", 1500), new RangStavka(2, 2L, "Bora", 700)), rang);
    }

    @Test void samoPoslednjaRundaPoSlajdu() {
        Ucesnik a = ucesnik(1, "Ana", 0, false);
        PitanjeRunda stara = runda(1, 10L, 1), nova = runda(2, 10L, 2);
        List<Odgovor> o = List.of(odgovor(stara, a, 1000, 100), odgovor(nova, a, 0, 100));
        assertEquals(Map.of(1L, 0), RangLista.poeniPoUcesniku(o));
    }

    @Test void poslednjaRundaBezObziraNaRedosledListe() {
        Ucesnik a = ucesnik(1, "Ana", 0, false);
        PitanjeRunda stara = runda(1, 10L, 1), nova = runda(2, 10L, 2);
        List<Odgovor> o = List.of(odgovor(nova, a, 300, 100), odgovor(stara, a, 1000, 100));
        assertEquals(Map.of(1L, 300), RangLista.poeniPoUcesniku(o));
    }

    @Test void rundaBezSlajdaJeSamaZaSebe() {
        Ucesnik a = ucesnik(1, "Ana", 0, false);
        PitanjeRunda r1 = runda(1, null, 1), r2 = runda(2, null, 2);
        List<Odgovor> o = List.of(odgovor(r1, a, 400, 100), odgovor(r2, a, 600, 100));
        assertEquals(Map.of(1L, 1000), RangLista.poeniPoUcesniku(o));
    }

    @Test void jednakiPoeniManjeVremeProvoPaRanijaPrijava() {
        Ucesnik sporiji = ucesnik(1, "Spori", 0, false);
        Ucesnik brzi = ucesnik(2, "Brzi", 5, false);
        Ucesnik kasno = ucesnik(3, "Kasno", 9, false);
        Ucesnik rano = ucesnik(4, "Rano", 2, false);
        PitanjeRunda r = runda(1, 10L, 1);
        List<Odgovor> o = List.of(odgovor(r, sporiji, 800, 9000), odgovor(r, brzi, 800, 3000),
                odgovor(r, kasno, 800, 5000), odgovor(r, rano, 800, 5000));
        List<RangStavka> rang = RangLista.izracunaj(List.of(sporiji, brzi, kasno, rano), o);
        assertEquals(List.of("Brzi", "Rano", "Kasno", "Spori"), rang.stream().map(RangStavka::ime).toList());
        assertEquals(List.of(1, 2, 3, 4), rang.stream().map(RangStavka::mesto).toList());
    }

    @Test void izbaceniSeNeRangiraju() {
        Ucesnik a = ucesnik(1, "Ana", 0, false);
        Ucesnik b = ucesnik(2, "Bora", 1, true);
        PitanjeRunda r = runda(1, 10L, 1);
        List<Odgovor> o = List.of(odgovor(r, a, 100, 100), odgovor(r, b, 900, 100));
        List<RangStavka> rang = RangLista.izracunaj(List.of(a, b), o);
        assertEquals(1, rang.size());
        assertEquals("Ana", rang.get(0).ime());
        assertEquals(1, rang.get(0).mesto());
    }

    @Test void ucesnikBezOdgovoraImaNulaPoenaIIdeIza() {
        Ucesnik tih = ucesnik(1, "Tih", 0, false);
        Ucesnik pogresio = ucesnik(2, "Pogresio", 1, false);
        PitanjeRunda r = runda(1, 10L, 1);
        List<Odgovor> o = List.of(odgovor(r, pogresio, 0, 4000));
        List<RangStavka> rang = RangLista.izracunaj(List.of(tih, pogresio), o);
        assertEquals(List.of("Pogresio", "Tih"), rang.stream().map(RangStavka::ime).toList());
        assertEquals(0, rang.get(1).poeni());
    }

    @Test void prazno() {
        assertEquals(List.of(), RangLista.izracunaj(List.of(), List.of()));
        assertEquals(Map.of(), RangLista.poeniPoUcesniku(List.of()));
    }

    @Test void vremeSeBrojiSamoIzPoslednjihRundi() {
        // isti poeni; "A" je sporiji u staroj rundi koja ne vazi, pa ne sme da izgubi
        Ucesnik a = ucesnik(1, "A", 0, false);
        Ucesnik b = ucesnik(2, "B", 1, false);
        PitanjeRunda stara = runda(1, 10L, 1), nova = runda(2, 10L, 2);
        List<Odgovor> o = List.of(odgovor(stara, a, 0, 50000), odgovor(nova, a, 500, 2000), odgovor(nova, b, 500, 3000));
        assertEquals(List.of("A", "B"), RangLista.izracunaj(List.of(a, b), o).stream().map(RangStavka::ime).toList());
    }
}
