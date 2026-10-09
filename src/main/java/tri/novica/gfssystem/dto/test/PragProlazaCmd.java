package tri.novica.gfssystem.dto.test;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;
import tri.novica.gfssystem.utility.CeoBrojDeserializer;

/**
 * Telo za {@code PATCH /test/{id}/prag-prolaza}: ključ {@code pragProlaza} je obavezan, a vrednost {@code null} uklanja
 * prag. Telo bez ključa ({@code {}}), pogrešno otkucan ključ i nepoznata polja su 400, da greška u kucanju ne bi tiho
 * obrisala prag. Gornja granica (maxPoena) se proverava u {@code TestPP}.
 */
@Data
public class PragProlazaCmd {
    @Min(value = 0, message = "Prag prolaza mora biti između 0 i maksimalnog broja poena.")
    private final Integer pragProlaza;

    @JsonCreator
    public PragProlazaCmd(@JsonProperty(value = "pragProlaza", required = true)
                          @JsonDeserialize(using = CeoBrojDeserializer.class) Integer pragProlaza) {
        this.pragProlaza = pragProlaza;
    }

    /**
     * Svako drugo polje je greška. Ne može {@code @JsonIgnoreProperties(ignoreUnknown = false)}: to je samo
     * podrazumevana vrednost, a mapper je podešen ({@code use-jackson2-defaults}) da nepoznata polja preskače.
     */
    @JsonAnySetter
    void nepoznatoPolje(String ime, Object vrednost) {
        throw new IllegalArgumentException("Nepoznato polje: " + ime);
    }
}
