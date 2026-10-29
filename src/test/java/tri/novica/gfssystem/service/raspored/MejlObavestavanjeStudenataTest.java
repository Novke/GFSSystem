package tri.novica.gfssystem.service.raspored;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.service.mejl.MejlPoruka;
import tri.novica.gfssystem.service.mejl.MejlPoruke;
import tri.novica.gfssystem.service.mejl.MejlPosleCommita;
import tri.novica.gfssystem.service.mejl.MejlServis;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mejlovi studentima o promeni termina (pregled posle PR-a, tačka 1): jedna poruka po adresi bez obzira na velika i mala
 * slova, studenti bez adrese se preskaču, pun red se broji kao {@code neposlato}, bez SMTP-a ništa ne ide u red. Student
 * koji je na tabu "Ja" isključio mejl o otkazanom času izbacuje se pre spajanja adresa i ne broji se ni u jednom broju.
 */
class MejlObavestavanjeStudenataTest {

    static final PromenaTermina PROMENA = new PromenaTermina(PromenaTermina.Vrsta.OTKAZAN,
            new TerminOpis(7L, LocalDate.of(2030, 11, 13), LocalTime.of(10, 0), LocalTime.of(13, 0), "P", "G"), "napomena");

    final MejlServis mejl = mock(MejlServis.class);
    final MejlPosleCommita slanje = mock(MejlPosleCommita.class);
    final MejlPoruke poruke = new MejlPoruke("https://gfs.example.test");
    /** Bez reda podešavanja svi žele mejl (prazan skup isključenih); testovi podešavanja vraćaju svoj skup. */
    final PodesavanjaStudentaServis podesavanja = mock(PodesavanjaStudentaServis.class);

    static Student student(String ime, String email) {
        Student s = new Student();
        s.setIme(ime);
        s.setEmail(email);
        return s;
    }

    static Student student(long id, String ime, String email) {
        Student s = student(ime, email);
        s.setId(id);
        return s;
    }

    /** Izvršilac koji primi {@code kapacitet} zadataka (ne pokreće ih), a ostale odbije kao pun red. */
    static final class Red implements TaskExecutor {
        final List<Runnable> primljeno = new ArrayList<>();
        final int kapacitet;

        Red(int kapacitet) {
            this.kapacitet = kapacitet;
        }

        @Override
        public void execute(Runnable zadatak) {
            if (primljeno.size() >= kapacitet) {
                throw new TaskRejectedException("pun red");
            }
            primljeno.add(zadatak);
        }
    }

    @Test
    void jednaPorukaPoAdresiBezAdreseSePreskace() {
        when(mejl.dostupan()).thenReturn(true);
        Red red = new Red(100);
        var o = new MejlObavestavanjeStudenata(mejl, poruke, slanje, red, podesavanja);
        var ishod = o.obavesti(List.of(student("Ana", "ana@example.test"), student("Ana2", " ANA@Example.test "),
                student("Bora", null), student("Ceca", "  "), student("Deda", "deda@example.test")), PROMENA);
        assertEquals(new ObavestavanjeStudenata.Ishod(2, 0), ishod);
        assertEquals(2, red.primljeno.size());
    }

    @Test
    void punRedSeBrojiKaoNeposlato() {
        when(mejl.dostupan()).thenReturn(true);
        Red red = new Red(2);
        var o = new MejlObavestavanjeStudenata(mejl, poruke, slanje, red, podesavanja);
        List<Student> studenti = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            studenti.add(student("S" + i, "s" + i + "@example.test"));
        }
        assertEquals(new ObavestavanjeStudenata.Ishod(2, 3), o.obavesti(studenti, PROMENA));
    }

    @Test
    void bezSmtpaNistaUReduSviNeposlato() {
        when(mejl.dostupan()).thenReturn(false);
        Red red = new Red(100);
        var o = new MejlObavestavanjeStudenata(mejl, poruke, slanje, red, podesavanja);
        assertEquals(new ObavestavanjeStudenata.Ishod(0, 2),
                o.obavesti(List.of(student("Ana", "ana@example.test"), student("Bora", "bora@example.test")), PROMENA));
        assertTrue(red.primljeno.isEmpty());
        assertEquals(ObavestavanjeStudenata.Ishod.NISTA, o.obavesti(List.of(student("Bora", null)), PROMENA));
    }

    @Test
    void zadatakSaljePorukuNaAdresuStudenta() {
        when(mejl.dostupan()).thenReturn(true);
        Red red = new Red(10);
        List<MejlPoruka> poslate = new ArrayList<>();
        MejlPosleCommita hvata = new MejlPosleCommita(mejl, new long[0]) {
            @Override
            public void posaljiSaPokusajima(MejlPoruka poruka) {
                poslate.add(poruka);
            }
        };
        new MejlObavestavanjeStudenata(mejl, poruke, hvata, red, podesavanja)
                .obavesti(List.of(student("Ana", " ana@example.test ")), PROMENA);
        red.primljeno.forEach(Runnable::run);
        assertEquals(1, poslate.size());
        assertEquals("ana@example.test", poslate.get(0).za());
    }

    @Test
    void iskljucenMejlSeIzbacujePreSpajanjaAdresaINeBrojiSe() {
        when(mejl.dostupan()).thenReturn(true);
        when(podesavanja.bezMejlaOtkazanogCasa(any())).thenReturn(Set.of(1L, 3L));
        Red red = new Red(100);
        var o = new MejlObavestavanjeStudenata(mejl, poruke, slanje, red, podesavanja);
        // Ana (1) je isključila mejl, a Ana2 (2) deli njenu adresu: Ana2 i dalje dobija poruku; Ceca (3) ne dobija ništa
        var ishod = o.obavesti(List.of(student(1, "Ana", "ana@example.test"), student(2, "Ana2", "ANA@example.test"),
                student(3, "Ceca", "ceca@example.test"), student(4, "Deda", "deda@example.test")), PROMENA);
        assertEquals(new ObavestavanjeStudenata.Ishod(2, 0), ishod);
        // samo isključeni: ništa u red i ništa neposlato
        when(podesavanja.bezMejlaOtkazanogCasa(any())).thenReturn(Set.of(3L));
        assertEquals(ObavestavanjeStudenata.Ishod.NISTA, o.obavesti(List.of(student(3, "Ceca", "ceca@example.test")), PROMENA));
        assertEquals(2, red.primljeno.size());
    }

    @Test
    void bezSmtpaNeposlatoBrojiSamoOneKojiZeleMejl() {
        when(mejl.dostupan()).thenReturn(false);
        when(podesavanja.bezMejlaOtkazanogCasa(any())).thenReturn(Set.of(1L));
        var o = new MejlObavestavanjeStudenata(mejl, poruke, slanje, new Red(100), podesavanja);
        assertEquals(new ObavestavanjeStudenata.Ishod(0, 1),
                o.obavesti(List.of(student(1, "Ana", "ana@example.test"), student(2, "Bora", "bora@example.test")), PROMENA));
    }
}
