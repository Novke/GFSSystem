package tri.novica.gfssystem.dto.domaci;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.time.LocalDate;

/** Red liste domaćih. {@code grupa} i {@code predavanje} su null za stare redove bez njih (tada je {@code brojStudenata} 0). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DomaciListItem {
    private Long id;
    private String naslov;
    private LocalDate datum;
    private Boolean pregledan;
    private PredmetInfo predmet;
    private GrupaInfo grupa;
    private DomaciPredavanjeRef predavanje;
    /** Broj studenata sa evidentiranim urađenim domaćim; oslobođeni se ne računaju. */
    private long brojUradjenih;
    /** Broj studenata koji su sada u grupi domaćeg. */
    private long brojStudenata;
}
