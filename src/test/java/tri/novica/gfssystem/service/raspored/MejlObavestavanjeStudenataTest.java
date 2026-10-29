package tri.novica.gfssystem.service.raspored;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.service.mejl.MejlPoruka;
import tri.novica.gfssystem.service.mejl.MejlPoruke;
import tri.novica.gfssystem.service.mejl.MejlPosleCommita;
import tri.novica.gfssystem.service.mejl.MejlServis;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mejlovi studentima o promeni termina (pregled posle PR-a, tačka 1): jedna poruka po adresi bez obzira na velika i mala
 * slova, studenti bez adrese se preskaču, pun red se broji kao {@code neposlato}, bez SMTP-a ništa ne ide u red.
 */
class MejlObavestavanjeStudenataTest {

    static final PromenaTermina PROMENA = new PromenaTermina(PromenaTermina.Vrsta.OTKAZAN,
            new TerminOpis(7L, LocalDate.of(2030, 11, 13), LocalTime.of(10, 0), LocalTime.of(13, 0), "P", "G"), "napomena");

    final MejlServis mejl = mock(MejlServis.class);
    final MejlPosleCommita slanje = mock(MejlPosleCommita.class);
    final MejlPoruke poruke = new MejlPoruke("https://gfs.example.test");

    static Student student(String ime, String email) {
        Student s = new Student();
        s.setIme(ime);
        s.setEmail(email);
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
        var o = new MejlObavestavanjeStudenata(mejl, poruke, slanje, red);
        var ishod = o.obavesti(List.of(student("Ana", "ana@example.test"), student("Ana2", " ANA@Example.test "),
                student("Bora", null), student("Ceca", "  "), student("Deda", "deda@example.test")), PROMENA);
        assertEquals(new ObavestavanjeStudenata.Ishod(2, 0), ishod);
        assertEquals(2, red.primljeno.size());
    }

    @Test
    void punRedSeBrojiKaoNeposlato() {
        when(mejl.dostupan()).thenReturn(true);
        Red red = new Red(2);
        var o = new MejlObavestavanjeStudenata(mejl, poruke, slanje, red);
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
        var o = new MejlObavestavanjeStudenata(mejl, poruke, slanje, red);
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
        new MejlObavestavanjeStudenata(mejl, poruke, hvata, red)
                .obavesti(List.of(student("Ana", " ana@example.test ")), PROMENA);
        red.primljeno.forEach(Runnable::run);
        assertEquals(1, poslate.size());
        assertEquals("ana@example.test", poslate.get(0).za());
    }
}
