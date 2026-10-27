package tri.novica.gfssystem.dto.banka;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;
import tri.novica.gfssystem.utility.CeoBrojDeserializer;

/**
 * Telo za {@code PATCH /banka/varijante/{id}/nivo}: ključ {@code nivo} je obavezan i ceo broj; telo bez ključa,
 * decimalni broj, string i nepoznata polja su 400 (obrazac {@code PragProlazaCmd}). {@code null} i opseg 1-3 proverava
 * servis ({@code utility/Nivo}).
 */
@Data
public class NivoCmd {
    private final Integer nivo;

    @JsonCreator
    public NivoCmd(@JsonProperty(value = "nivo", required = true)
                   @JsonDeserialize(using = CeoBrojDeserializer.class) Integer nivo) {
        this.nivo = nivo;
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
