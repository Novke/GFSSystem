package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.dto.uzivo.RangTacnostiStavka;
import tri.novica.gfssystem.service.uzivo.RangTacnosti.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RangTacnostiTest {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 28, 10, 0);

    static Runda pitanje(long id, Object slajd, int rb, Integer zatvorenoMin) {
        return new Runda(id, slajd, rb, zatvorenoMin == null ? null : T0.plusMinutes(zatvorenoMin), true, null);
    }

    static Runda zadatak(long id, Object slajd, Integer zatvorenoMin, Map<Long, Varijanta> varijante) {
        return new Runda(id, slajd, 1, zatvorenoMin == null ? null : T0.plusMinutes(zatvorenoMin), false, varijante);
    }

    static Clan clan(long id, int usaoMin) {
        return new Clan(id, T0.plusMinutes(usaoMin));
    }

    static Odg odg(long runda, long ucesnik, Boolean tacno, long ms) {
        return new Odg(runda, ucesnik, tacno, null, ms, null);
    }

    /** Odgovor sa sačuvanim udelom (V27); {@code tacno} je "potpuno tačno". */
    static Odg udeo(long runda, long ucesnik, double udeo, long ms) {
        return new Odg(runda, ucesnik, udeo >= 1, udeo, ms, null);
    }

    static RangTacnostiStavka stavka(Kandidat<String> x, Integer mesto) {
        return new RangTacnostiStavka(mesto, null, null, x.ime(), false, x.zbir().tacnih(), x.zbir().dugovanih(),
                x.zbir().odgovorenih(), x.zbir().procenat(), x.zbir().prosekMs(), x.zbir().zvezdice(), null, null, null,
                null, false);
    }

    @Test
    void propustenoPitanjeJeNetacnoAOtvorenoSeNeDuguje() {
        List<Runda> runde = List.of(pitanje(1, 10L, 1, 5), pitanje(2, 11L, 1, 10), pitanje(3, 12L, 1, null));
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 0), clan(2, 0)),
                List.of(odg(1, 1, true, 4000), odg(2, 1, true, 2000), odg(1, 2, true, 1000), odg(3, 2, false, 500)),
                Map.of(), null);
        // Ana: 2/2 (otvorena runda 3 se ne duguje bez odgovora)
        assertEquals(2.0, z.get(1L).tacnih());
        assertEquals(2, z.get(1L).dugovanih());
        assertEquals(3000L, z.get(1L).prosekMs());
        assertEquals(2.0, z.get(1L).zvezdice());
        // Bojan: runda 1 tačno, runda 2 propuštena = netačno, otvorena runda 3 odgovorena netačno = duguje
        assertEquals(1.0, z.get(2L).tacnih());
        assertEquals(3, z.get(2L).dugovanih());
        assertEquals(2, z.get(2L).odgovorenih());
        assertEquals(33, z.get(2L).procenat());
    }

    @Test
    void usaoPosleZatvaranjaNeDuguje() {
        List<Runda> runde = List.of(pitanje(1, 10L, 1, 5), pitanje(2, 11L, 1, 10));
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 7)), List.of(), Map.of(), null);
        assertEquals(1, z.get(1L).dugovanih()); // samo runda 2
        assertNull(z.get(1L).prosekMs());
    }

    @Test
    void ponovljenSlajdVaziPoslednjaRundaIAnketaNeUlazi() {
        Runda anketa = new Runda(4L, 13L, 1, T0.plusMinutes(3), false, null);
        List<Runda> runde = List.of(pitanje(1, 10L, 1, 5), pitanje(2, 10L, 2, 8), anketa);
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 0)),
                List.of(odg(1, 1, false, 1000), odg(2, 1, true, 1000), odg(4, 1, null, 1000)), Map.of(), null);
        assertEquals(1, z.get(1L).dugovanih());
        assertEquals(1.0, z.get(1L).tacnih());
    }

    @Test
    void zadatakNosiNivoRadNaPapiruNeUlazi() {
        Map<Long, Varijanta> v = Map.of(100L, new Varijanta(true, 3), 101L, new Varijanta(false, 1),
                102L, new Varijanta(true, RangTacnosti.PODRAZUMEVANI_NIVO));
        List<Runda> runde = List.of(zadatak(1, 10L, 5, v));
        Map<Long, Map<Long, Long>> dodele = Map.of(1L, Map.of(1L, 100L, 2L, 101L, 3L, 102L));
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 0), clan(2, 0), clan(3, 0)),
                List.of(new Odg(1L, 1L, true, 1.0, 9000, 100L)), dodele, null);
        assertEquals(3.0, z.get(1L).zvezdice());
        assertEquals(0, z.get(2L).dugovanih()); // rad na papiru
        assertEquals(1, z.get(3L).dugovanih()); // nije predao = netačno
        assertEquals(0.0, z.get(3L).tacnih());
    }

    @Test
    void izuzetaRundaSeNeRacuna() {
        List<Runda> runde = List.of(pitanje(1, 10L, 1, 5), pitanje(2, 11L, 1, 10));
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 0)),
                List.of(odg(1, 1, true, 1000), odg(2, 1, true, 1000)), Map.of(), 2L);
        assertEquals(1, z.get(1L).dugovanih());
    }

    static Zbir zbir(int tacnih, int dugovanih, int odgovorenih, long vremeMs, int zvezdice) {
        Zbir z = new Zbir();
        z.tacnih = tacnih;
        z.dugovanih = dugovanih;
        z.odgovorenih = odgovorenih;
        z.vremeMs = vremeMs;
        z.zvezdice = zvezdice;
        return z;
    }

    @Test
    void redosledZvezdicePaVremeIDeljenoMesto() {
        List<Kandidat<String>> k = List.of(
                new Kandidat<>("a", "Ana", zbir(2, 3, 3, 9000, 4)),     // ★4, 3 s: ispred bržih sa manje ★
                new Kandidat<>("b", "Bojan", zbir(3, 3, 3, 30000, 5)),  // ★5, 10 s
                new Kandidat<>("c", "Cvele", zbir(3, 3, 3, 6000, 3)),   // ★3, 2 s (tačnost 100 % ne pomaže)
                new Kandidat<>("d", "Dara", zbir(2, 3, 2, 4000, 3)),    // ★3, 2 s (isto mesto kao Cvele)
                new Kandidat<>("e", "Ema", zbir(2, 3, 0, 0, 3)),        // ★3, bez vremena (teorijski), iza
                new Kandidat<>("f", "Fića", zbir(0, 0, 0, 0, 0)));      // ništa nije dugovao: izostaje
        List<RangTacnostiStavka> rang = RangTacnosti.rangiraj(k, RangTacnostiTest::stavka);
        assertEquals(List.of("Bojan", "Ana", "Cvele", "Dara", "Ema"), rang.stream().map(RangTacnostiStavka::ime).toList());
        assertEquals(List.of(1, 2, 3, 3, 5), rang.stream().map(RangTacnostiStavka::mesto).toList());
        assertEquals(67, rang.get(1).procenat());
    }

    @Test
    void delimicnoNosiDeoZvezdice() {
        // A: pola na pitanju 1 (jedan od dva tačna), celo na pitanju 2; B: celo pa ništa
        List<Runda> runde = List.of(pitanje(1, 10L, 1, 5), pitanje(2, 11L, 1, 10));
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 0), clan(2, 0)),
                List.of(udeo(1, 1, 0.5, 2000), udeo(2, 1, 1.0, 2000), udeo(1, 2, 1.0, 2000), udeo(2, 2, 0.0, 2000)),
                Map.of(), null);
        assertEquals(1.5, z.get(1L).zvezdice());
        assertEquals(1.5, z.get(1L).tacnih());
        assertEquals(75, z.get(1L).procenat());
        assertEquals(1.0, z.get(2L).zvezdice());
        assertEquals(1.0, z.get(2L).tacnih());
        assertEquals(50, z.get(2L).procenat());

        List<RangTacnostiStavka> rang = RangTacnosti.rangiraj(List.of(new Kandidat<>("b", "B", z.get(2L)),
                new Kandidat<>("a", "A", z.get(1L))), RangTacnostiTest::stavka);
        assertEquals(List.of("A", "B"), rang.stream().map(RangTacnostiStavka::ime).toList());
        assertEquals(List.of(1, 2), rang.stream().map(RangTacnostiStavka::mesto).toList());
        assertEquals(1.5, rang.getFirst().zvezdice());
    }

    @Test
    void zadatakNosiUdeoPutaNivo() {
        Map<Long, Varijanta> v = Map.of(100L, new Varijanta(true, 3));
        List<Runda> runde = List.of(zadatak(1, 10L, 5, v));
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 0), clan(2, 0)),
                List.of(new Odg(1L, 1L, true, 1.0, 9000, 100L), new Odg(1L, 2L, false, 0.0, 9000, 100L)),
                Map.of(1L, Map.of(1L, 100L, 2L, 100L)), null);
        assertEquals(3.0, z.get(1L).zvezdice());
        assertEquals(1.0, z.get(1L).tacnih());
        assertEquals(0.0, z.get(2L).zvezdice());
    }

    @Test
    void udeoNullPadaNaTacno() {
        // red upisan starijim imidžom (posle rollback-a): bez udela važi tacno 1/0
        List<Runda> runde = List.of(pitanje(1, 10L, 1, 5));
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 0), clan(2, 0)),
                List.of(odg(1, 1, true, 1000), odg(1, 2, false, 1000)), Map.of(), null);
        assertEquals(1.0, z.get(1L).zvezdice());
        assertEquals(0.0, z.get(2L).zvezdice());
    }

    @Test
    void dveTreceZaokruzeno() {
        // dva od tri tačna = 2/3; stavka i zbir nose 0,67, a isti prikazani broj deli mesto
        List<Runda> runde = List.of(pitanje(1, 10L, 1, 5));
        Map<Long, Zbir> z = RangTacnosti.poUcesniku(runde, List.of(clan(1, 0), clan(2, 0), clan(3, 0)),
                List.of(udeo(1, 1, 2.0 / 3, 3000), udeo(1, 2, 0.667, 3000), udeo(1, 3, 1.0 / 3, 3000)), Map.of(), null);
        assertEquals(0.67, z.get(1L).zvezdice());
        assertEquals(0.67, z.get(1L).tacnih());
        assertEquals(67, z.get(1L).procenat());
        List<RangTacnostiStavka> rang = RangTacnosti.rangiraj(List.of(new Kandidat<>("a", "A", z.get(1L)),
                new Kandidat<>("b", "B", z.get(2L)), new Kandidat<>("c", "C", z.get(3L))), RangTacnostiTest::stavka);
        assertEquals(0.67, rang.getFirst().zvezdice());
        assertEquals(0.67, rang.getFirst().tacnih());
        assertEquals(List.of(1, 1, 3), rang.stream().map(RangTacnostiStavka::mesto).toList(), "0,6667 i 0,667 su obe 0,67");
        assertEquals(0.33, rang.get(2).zvezdice());
    }

    @Test
    void zaokruzivanjeNaPolaNagore() {
        assertEquals(0.13, Ocenjivac.zaokruzi(0.125));
        assertEquals(1.01, Ocenjivac.zaokruzi(1.005));
        assertEquals(3.5, Ocenjivac.zaokruzi(3.5));
        assertEquals(0.0, Ocenjivac.zaokruzi(0.0));
        assertEquals(0.67, Ocenjivac.zaokruzi((Double) (2.0 / 3)));
        assertNull(Ocenjivac.zaokruzi((Double) null));
    }

    @Test
    void kratkaImenaZaProjektor() {
        Student s = new Student();
        s.setIme(" Novica ");
        s.setPrezime("trifković");
        assertEquals("Novica T.", RangTacnostiService.kratkoIme(s));
        s.setPrezime(null);
        assertEquals("Novica", RangTacnostiService.kratkoIme(s));
        assertEquals("Ana Š.", RangTacnostiService.kratkoIme("Ana Marija  šarić"));
        assertEquals("Ana", RangTacnostiService.kratkoIme(" Ana "));
        assertEquals(List.of("Ana Kovač", "Ana Kostić", "Bojan P."), RangTacnostiService.kratkaImena(
                List.of("Ana K.", "Ana K.", "Bojan P."), List.of("Ana Kovač", "Ana Kostić", "Bojan Petrov")));
    }

    @Test
    void kljucImenaSpajaVelikaSlovaDijakritikeIRazmake() {
        assertEquals(RangTacnosti.kljucImena("  Đorđe  Šarić "), RangTacnosti.kljucImena("djordje sarić".replace("dj", "đ")));
        assertEquals("dorde saric", RangTacnosti.kljucImena("Đorđe   Šarić"));
    }
}
