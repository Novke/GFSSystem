package tri.novica.gfssystem.dto.uzivo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;
import tri.novica.gfssystem.utility.CeoBrojDeserializer;

/**
 * Telo za {@code PATCH /prezentacije/{id}/vezba}: ključ {@code vezba} je obavezan, {@code null} uklanja broj. Telo bez
 * ključa, decimalni broj, string i nepoznata polja su 400 (obrazac {@code PragProlazaCmd}); opseg 0-99 proverava servis.
 */
@Data
public class VezbaCmd {
    private final Integer vezba;

    @JsonCreator
    public VezbaCmd(@JsonProperty(value = "vezba", required = true)
                    @JsonDeserialize(using = CeoBrojDeserializer.class) Integer vezba) {
        this.vezba = vezba;
    }

    /**
     * Svako drugo polje je greška. Ne može {@code @JsonIgnoreProperties(ignoreUnknown = false)}: mapper je podešen
     * ({@code use-jackson2-defaults}) da nepoznata polja preskače.
     */
    @JsonAnySetter
    void nepoznatoPolje(String ime, Object vrednost) {
        throw new IllegalArgumentException("Nepoznato polje: " + ime);
    }
}
