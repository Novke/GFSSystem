package tri.novica.gfssystem.dto.test;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Telo za {@code PATCH /test/{id}/prag-prolaza}: {@code null} uklanja prag. Gornja granica (maxPoena) se proverava u {@code TestPP}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PragProlazaCmd {
    @Min(value = 0, message = "Prag prolaza mora biti između 0 i maksimalnog broja poena.")
    private Integer pragProlaza;
}
