package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImenaUcesnikaTest {

    final UcesnikRepository repo = mock(UcesnikRepository.class);
    final ImenaUcesnika imena = new ImenaUcesnika(repo);
    final List<Ucesnik> ucesnici = new ArrayList<>();

    ImenaUcesnikaTest() {
        when(repo.findAllByIzvodjenjeIdOrderByKreiranoAsc(5L)).thenReturn(ucesnici);
    }

    void dodaj(long id, String ime, boolean izbacen) {
        Ucesnik u = new Ucesnik();
        u.setId(id);
        u.setIme(ime);
        u.setIzbacen(izbacen);
        ucesnici.add(u);
    }

    @Test
    void validirajNormalizujeIProveravaDuzinu() {
        assertEquals("Ana Anić", imena.validiraj("  Ana \u0007  Anić "));
        assertEquals("x".repeat(40), imena.validiraj("x".repeat(40)));
        // 40 kodnih tačaka van BMP-a je i dalje 40 znakova
        assertEquals("😀".repeat(40), imena.validiraj("😀".repeat(40)));
        for (String lose : new String[]{null, "", "   ", "\u0007", "x".repeat(41)}) {
            SystemException e = assertThrows(SystemException.class, () -> imena.validiraj(lose));
            assertEquals(400, e.getCode());
            assertEquals("Ime mora imati od 1 do 40 znakova.", e.getMessage());
        }
    }

    @Test
    void slobodnoImeOstaje() {
        dodaj(1, "Ana", false);
        assertEquals("Marko", imena.jedinstvenoIme(5L, "Marko", null));
    }

    @Test
    void duplikatBezObziraNaVelikaSlovaDobijaSufiks() {
        dodaj(1, "Ana", false);
        assertEquals("ana 2", imena.jedinstvenoIme(5L, "ana", null));
        dodaj(2, "ana 2", false);
        assertEquals("ANA 3", imena.jedinstvenoIme(5L, "ANA", null));
    }

    @Test
    void izbaceniIIzuzetSeNeRacunaju() {
        dodaj(1, "Ana", true);
        assertEquals("Ana", imena.jedinstvenoIme(5L, "Ana", null));
        dodaj(2, "Ana", false);
        // preimenovanje učesnika 2 u isto ime nije duplikat sa samim sobom
        assertEquals("Ana", imena.jedinstvenoIme(5L, "Ana", 2L));
        assertEquals("Ana 2", imena.jedinstvenoIme(5L, "Ana", 3L));
    }

    @Test
    void dugackoImeSeSkracujeDaSufiksStane() {
        String ime = "a".repeat(39) + "b";
        dodaj(1, ime, false);
        String novo = imena.jedinstvenoIme(5L, ime, null);
        assertEquals("a".repeat(38) + " 2", novo);
        assertEquals(40, novo.length());
    }

    @Test
    void skracivanjeNeOstavljaDvaRazmaka() {
        String ime = "a".repeat(37) + " bc";
        dodaj(1, ime, false);
        assertEquals("a".repeat(37) + " 2", imena.jedinstvenoIme(5L, ime, null));
    }
}
