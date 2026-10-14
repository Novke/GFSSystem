package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.ocenjivanje.MaxPoeniStudentaNaTestuInfo;
import tri.novica.gfssystem.dto.ocenjivanje.RezultatiStudentaInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class OcenaPragoviTest {

    /** Doslovno izraz iz {@code RezultatiStudentaInfo.izracunajUkupno()} pre izdvajanja pragova. */
    private static Integer staraOcena(double ukupno) {
        return ukupno < 51 ? null : (ukupno < 61 ? 6 : ukupno < 71 ? 7 : ukupno < 81 ? 8 : ukupno < 91 ? 9 : 10);
    }

    @Test
    void granicePragova() {
        assertNull(OcenaPragovi.ocena(0));
        assertNull(OcenaPragovi.ocena(50.999));
        assertNull(OcenaPragovi.ocena(Math.nextDown(51.0)));
        assertEquals(6, OcenaPragovi.ocena(51));
        assertEquals(6, OcenaPragovi.ocena(60.99));
        assertEquals(7, OcenaPragovi.ocena(61));
        assertEquals(7, OcenaPragovi.ocena(Math.nextDown(71.0)));
        assertEquals(8, OcenaPragovi.ocena(71));
        assertEquals(8, OcenaPragovi.ocena(80.5));
        assertEquals(9, OcenaPragovi.ocena(81));
        assertEquals(9, OcenaPragovi.ocena(90.99));
        assertEquals(10, OcenaPragovi.ocena(91));
        assertEquals(10, OcenaPragovi.ocena(100));
        assertEquals(10, OcenaPragovi.ocena(250));
        assertNull(OcenaPragovi.ocena(-3));
    }

    @Test
    void doSledeceOcene() {
        assertEquals(51.0, OcenaPragovi.doSledece(0));
        assertEquals(1.0, OcenaPragovi.doSledece(50));
        assertEquals(10.0, OcenaPragovi.doSledece(51));       // tačno na pragu za 6: do 7 je ceo raspon
        assertEquals(0.5, OcenaPragovi.doSledece(60.5));
        assertEquals(10.0, OcenaPragovi.doSledece(61));
        assertEquals(10.0, OcenaPragovi.doSledece(71));
        assertEquals(1.0, OcenaPragovi.doSledece(90));
        assertNull(OcenaPragovi.doSledece(91));              // 10 nema sledeću
        assertNull(OcenaPragovi.doSledece(100));
        assertEquals(54.0, OcenaPragovi.doSledece(-3));
    }

    @Test
    void doSledeceJeUvekPozitivnoIVodiUSledecuOcenu() {
        for (int i = -500; i <= 1100; i++) {
            double ukupno = i / 10.0;
            Double fali = OcenaPragovi.doSledece(ukupno);
            Integer ocena = OcenaPragovi.ocena(ukupno);
            if (Objects.equals(ocena, 10)) {
                assertNull(fali, "ukupno " + ukupno);
                continue;
            }
            assertNotNull(fali, "ukupno " + ukupno);
            assertTrue(fali > 0, "ukupno " + ukupno);
            int sledeca = ocena == null ? 6 : ocena + 1;
            assertEquals(sledeca, OcenaPragovi.ocena(ukupno + fali + 1e-9), "ukupno " + ukupno);
            assertEquals(ocena, OcenaPragovi.ocena(ukupno + fali - 1e-9), "ukupno " + ukupno);
        }
    }

    @Test
    void istoKaoStariIzraz() {
        List<Double> vrednosti = new ArrayList<>();
        for (int i = -1000; i <= 12000; i++) vrednosti.add(i / 100.0);
        for (double prag : new double[]{51, 61, 71, 81, 91}) {
            vrednosti.add(Math.nextDown(prag));
            vrednosti.add(prag);
            vrednosti.add(Math.nextUp(prag));
        }
        vrednosti.addAll(List.of(0.0, -0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE));
        for (double v : vrednosti) {
            assertEquals(staraOcena(v), OcenaPragovi.ocena(v), "ukupno " + v);
        }
    }

    @Test
    void rezultatiStudentaKoristePragove() {
        for (double test : new double[]{0, 20, 30.5, 40, 45.25, 50, 60, 70.999, 80, 120}) {
            RezultatiStudentaInfo rez = new RezultatiStudentaInfo(null);
            rez.setPoeniAktivnost(6.5);
            rez.setPoeniDomaci(4.5);
            rez.getRezultati().add(new MaxPoeniStudentaNaTestuInfo(null, test));
            rez.getRezultati().add(new MaxPoeniStudentaNaTestuInfo(null, null));
            rez.izracunajUkupno();
            assertEquals(11 + test, rez.getUkupno());
            assertEquals(11.0, rez.getPoeniPredispitne());
            assertEquals(staraOcena(11 + test), rez.getPredlogOcene(), "test " + test);
        }
    }
}
