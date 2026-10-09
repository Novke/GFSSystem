package tri.novica.gfssystem.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.TestGrupa;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestStatistikaPoGrupiInfo {
    private TestGrupa grupa;
    private int brojPolaganja;
    private double prosecniPoeni;
    /** Null kad test nema prag prolaza (vidi {@code Prolaz}). */
    private Double procenatProlaznosti;
}
