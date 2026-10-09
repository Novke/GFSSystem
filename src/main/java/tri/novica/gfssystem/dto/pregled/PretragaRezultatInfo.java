package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predavanje.PredavanjeListItem;
import tri.novica.gfssystem.dto.student.StudentListItem;
import tri.novica.gfssystem.dto.test.TestListItem;

import java.util.List;

/** Globalna pretraga ({@code GET /pretraga?q}): do 5 stavki po grupi; upit kraći od 2 znaka daje prazne nizove. */
public record PretragaRezultatInfo(List<StudentListItem> studenti, List<PredavanjeListItem> predavanja,
                                   List<TestListItem> testovi, List<GrupaInfo> grupe) {

    public static PretragaRezultatInfo prazno() {
        return new PretragaRezultatInfo(List.of(), List.of(), List.of(), List.of());
    }
}
