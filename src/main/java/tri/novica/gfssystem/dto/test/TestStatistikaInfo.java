package tri.novica.gfssystem.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestStatistikaInfo {
    // Ukupna statistika
    private int ukupnoPolaganja;
    private double prosecniPoeni;
    private double minPoeni;
    private double maxPoeni;
    private double standardnaDevijacija;

    // Prolaznost
    private int brojPolozenih;
    private int brojPalih;
    private double procenatProlaznosti;

    // Statistika po test grupi (A, B, C, D)
    private List<TestStatistikaPoGrupiInfo> statistikaPoGrupi = new ArrayList<>();
}
