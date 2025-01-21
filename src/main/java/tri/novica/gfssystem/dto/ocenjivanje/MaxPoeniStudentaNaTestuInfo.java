package tri.novica.gfssystem.dto.ocenjivanje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaxPoeniStudentaNaTestuInfo {
    private TipTestaInfo tipTesta;
    private Double ostvarenoPoena;
}
