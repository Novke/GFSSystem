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

    // Prolaznost (pravilo u utility/Prolaz): null kad test nema prag prolaza ili nema polaganja sa poenima
    private Integer brojPolozenih;
    private Integer brojPalih;
    private Double procenatProlaznosti;

    // Statistika po test grupi (A, B, C, D)
    private List<TestStatistikaPoGrupiInfo> statistikaPoGrupi = new ArrayList<>();
}
