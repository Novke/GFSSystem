package tri.novica.gfssystem.dto.predavanje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.time.LocalDate;

/** Red liste predavanja. {@code grupa} je null za stara predavanja bez grupe (tada je {@code brojStudenata} 0). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredavanjeListItem {
    private Long id;
    private int rb;
    private LocalDate datum;
    private String tema;
    private Boolean zavrseno;
    private PredmetInfo predmet;
    private GrupaInfo grupa;
    /** Broj različitih studenata sa bar jednom aktivnošću (prisustvo, zadatak ili zvezdica). */
    private long brojPrisutnih;
    /**
     * Koliko od prisutnih nije iz grupe predavanja (ponovci, premešteni, studenti bez grupe); 0 za predavanje bez
     * grupe. Za prikaz "31/38 +3".
     */
    private long brojStarijihPrisutnih;
    /** Broj studenata koji su sada u grupi predavanja. */
    private long brojStudenata;
}
