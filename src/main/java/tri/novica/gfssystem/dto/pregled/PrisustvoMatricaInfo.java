package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.student.StudentInfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * G3: studenti grupe × predavanja grupe na predmetu (opciono jedne školske godine). Prazna ćelija = nema ključa u
 * {@code tip}.
 *
 * @param predavanja po datumu, pa rb, pa id-ju
 * @param studenti   studenti grupe po broju indeksa
 */
public record PrisustvoMatricaInfo(List<PredavanjeRef> predavanja, List<Red> studenti) {

    public record PredavanjeRef(Long id, int rb, LocalDate datum, String tema) {
    }

    /** @param tip predavanjeId -> {@code PRISUSTVO}, {@code ZADATAK} ili {@code SA_ZVEZDICOM} */
    public record Red(StudentInfo student, Map<Long, String> tip) {
    }
}
