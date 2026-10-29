package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.uzivo.PredmetKratko;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaInfo;
import tri.novica.gfssystem.dto.uzivo.StavkaIzbora;
import tri.novica.gfssystem.dto.uzivo.VrstaPrezentacije;
import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;
import tri.novica.gfssystem.entity.uzivo.Tema;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static tri.novica.gfssystem.dto.uzivo.VrstaPrezentacije.*;

class RedosledPrezentacijaTest {

    static final LocalDateTime T = LocalDateTime.of(2026, 10, 14, 10, 0);

    static PrezentacijaInfo p(long id, Integer vezba, VrstaPrezentacije vrsta, String naziv) {
        return p(id, vezba, vrsta, naziv, null);
    }

    static PrezentacijaInfo p(long id, Integer vezba, VrstaPrezentacije vrsta, String naziv, Long aktivno) {
        return new PrezentacijaInfo(id, naziv, null, new PredmetKratko(1L, "Uvod"), 5, 0, T, false,
                TelefonPrikaz.DUGMAD, true, 0, aktivno, Tema.NACRT, vezba, vrsta, 0, T, null);
    }

    /** Poređa kao servis i označi puštene (id -> vreme). */
    static List<StavkaIzbora> uRedu(List<PrezentacijaInfo> lista, Set<Long> pusteno) {
        Map<Long, LocalDateTime> m = new HashMap<>();
        pusteno.forEach(id -> m.put(id, T));
        return lista.stream().sorted(RedosledPrezentacija.REDOSLED).map(x -> new StavkaIzbora(x, m.get(x.id()))).toList();
    }

    /** Stanje prod predmeta "Uvod u primenu računara" na dan 2026-10-29 (nazivi, brojevi, vrste). */
    static List<PrezentacijaInfo> uvod() {
        return List.of(
                p(1, 0, PITANJA, "Upoznavanje"),
                p(2, 1, GRADIVO, "Vezbe 1 - Brojevni sistemi, prevodjenje"),
                p(3, 1, ZADACI, "Vežbe 1 – zadaci"),
                p(4, 2, GRADIVO, "Vezbe 2 - Aritmetičke operacije"),
                p(5, 2, ZADACI, "Vežbe 2 – zadaci"),
                p(6, 3, GRADIVO, "Vezbe 3 - Označeni brojevi"),
                p(7, 3, ZADACI, "Vežbe 3 i 4 – zadaci"),
                p(8, 4, GRADIVO, "Vezbe 4 - Predstavljanje brojeva u računaru"),
                p(9, null, GRADIVO, "Probni slajdovi"));
    }

    @Test
    void redosledPoVezbiPaVrstiPaNazivuBezBrojaNaKraju() {
        List<PrezentacijaInfo> lista = new ArrayList<>(List.of(
                p(10, null, GRADIVO, "A bez broja"),
                p(11, 2, ZADACI, "b zadaci"),
                p(12, 2, GRADIVO, "Z gradivo"),
                p(13, 2, PITANJA, "pitanja"),
                p(14, 0, PITANJA, "Upoznavanje"),
                p(15, 2, GRADIVO, "a gradivo")));
        lista.sort(RedosledPrezentacija.REDOSLED);
        assertEquals(List.of(14L, 15L, 12L, 13L, 11L, 10L), lista.stream().map(PrezentacijaInfo::id).toList());
    }

    @Test
    void prodStanjeNudiDugovaneZadatkePaNistaStarijegGradiva() {
        // pušteno u aplikaciji: Upoznavanje, Vežbe 1 i 2 zadaci, Vežbe 3 i 4 gradivo (Vežbe 1 i 2 gradivo van aplikacije)
        List<Long> predlog = RedosledPrezentacija.zaGrupu(uRedu(uvod(), Set.of(1L, 3L, 5L, 6L, 8L)));
        assertEquals(List.of(7L), predlog);
    }

    @Test
    void posleGradivaNudiNjegoveZadatkePaSledecuVezbu() {
        // 24.10. posle "Vežbe 2" gradiva: prvo Vežbe 2 – zadaci, pa Vežbe 3
        List<Long> predlog = RedosledPrezentacija.zaGrupu(uRedu(uvod(), Set.of(1L, 3L, 4L)));
        assertEquals(List.of(5L, 6L, 7L), predlog);
    }

    @Test
    void dugovaniZadaciStarijihVezbiIduPrvi() {
        List<Long> predlog = RedosledPrezentacija.zaGrupu(uRedu(uvod(), Set.of(1L, 2L, 4L, 5L)));
        // Vežbe 1 – zadaci se duguju, pa Vežbe 3 gradivo i zadaci
        assertEquals(List.of(3L, 6L, 7L), predlog);
    }

    @Test
    void bezIjednogPustanjaSvePoReduOdPocetka() {
        assertEquals(List.of(1L, 2L, 3L), RedosledPrezentacija.zaGrupu(uRedu(uvod(), Set.of())));
    }

    @Test
    void puštanjeBezBrojaNePomeraNajdaljuVezbu() {
        assertEquals(List.of(1L, 2L, 3L), RedosledPrezentacija.zaGrupu(uRedu(uvod(), Set.of(9L))));
    }

    @Test
    void preskaceUTokuIBezBrojaINajviseTri() {
        List<PrezentacijaInfo> lista = List.of(
                p(1, 1, GRADIVO, "V1"),
                p(2, 2, GRADIVO, "V2", 50L),
                p(3, 3, GRADIVO, "V3"),
                p(4, 4, GRADIVO, "V4"),
                p(5, 5, GRADIVO, "V5"),
                p(6, 6, GRADIVO, "V6"),
                p(7, null, ZADACI, "bez broja"));
        assertEquals(List.of(3L, 4L, 5L), RedosledPrezentacija.zaGrupu(uRedu(lista, Set.of(1L))));
    }

    @Test
    void nepustenoGradivoIsteNajdaljeVezbeSeNudi() {
        List<PrezentacijaInfo> lista = List.of(
                p(1, 5, GRADIVO, "Vežbe 5 deo 1"),
                p(2, 5, GRADIVO, "Vežbe 5 deo 2"),
                p(3, 5, ZADACI, "Vežbe 5 – zadaci"),
                p(4, 6, GRADIVO, "Vežbe 6"));
        assertEquals(List.of(2L, 3L, 4L), RedosledPrezentacija.zaGrupu(uRedu(lista, Set.of(1L))));
    }

    @Test
    void bezGrupeSledecePoReduPosleZavrsene() {
        List<StavkaIzbora> red = uRedu(uvod(), Set.of());
        assertEquals(List.of(5L, 6L, 7L), RedosledPrezentacija.posle(red, 4L));
        assertEquals(List.of(8L), RedosledPrezentacija.posle(red, 7L));
        assertEquals(List.of(), RedosledPrezentacija.posle(red, 8L));
        assertEquals(List.of(), RedosledPrezentacija.posle(red, 9L), "posle prezentacije bez broja nema predloga");
        assertEquals(List.of(), RedosledPrezentacija.posle(red, 404L));
        assertEquals(List.of(), RedosledPrezentacija.posle(red, null));
    }

    @Test
    void vrstaIzBrojaPitanjaIZadataka() {
        assertEquals(GRADIVO, VrstaPrezentacije.od(0, 0));
        assertEquals(PITANJA, VrstaPrezentacije.od(3, 0));
        assertEquals(ZADACI, VrstaPrezentacije.od(3, 1));
        assertEquals(ZADACI, VrstaPrezentacije.od(1, 1));
    }
}
