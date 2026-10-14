package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.student.StudentInfo;

import java.time.LocalDate;

/**
 * Jedan red G2 tabele.
 *
 * @param prisutan       različita predavanja na kojima student ima aktivnost (bilo koje grupe)
 * @param predavanja     predavanja grupe plus predavanja drugih grupa (ili bez grupe) na kojima je student bio, pa je
 *                       {@code prisutan <= predavanja} i za ponovce i premeštene studente
 * @param domaciUradjeno urađeni domaći grupe (i oslobođeni, kao u ocenjivanju)
 * @param domaciUkupno   domaći grupe
 * @param poslednjiTest  poslednje polaganje studenta (po datumu testa, pa id-ju) ili null
 */
public record GrupaStudentStatInfo(StudentInfo student, long prisutan, long predavanja, long domaciUradjeno,
                                   long domaciUkupno, TestRef poslednjiTest) {

    /** Polaganje testa: {@code poeni} i {@code maxPoena} mogu biti null (neunet rezultat, stari test). */
    public record TestRef(Long testId, String tip, LocalDate datum, Double poeni, Integer maxPoena) {
    }
}
