package tri.novica.gfssystem.dto.domaci;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Veza domaćeg sa predavanjem na kome je zadat (id i redni broj, za link i prikaz "Predavanje 7"). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DomaciPredavanjeRef {
    private Long id;
    private int rb;
}
