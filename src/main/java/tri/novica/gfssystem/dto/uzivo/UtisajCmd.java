package tri.novica.gfssystem.dto.uzivo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tri.novica.gfssystem.entity.uzivo.Utisanje;

/**
 * Telo za {@code PUT /izvodjenja/{id}/ucesnici/{ucesnikId}/utisan}: ključ {@code utisan} je obavezan ({@code PITALICE},
 * {@code SVE}, ili {@code null} = ukloni utišavanje); telo bez ključa, nepoznata vrednost i nepoznata polja su 400
 * (obrazac {@code PragProlazaCmd}).
 */
@Data
public class UtisajCmd {
    private final Utisanje utisan;

    @JsonCreator
    public UtisajCmd(@JsonProperty(value = "utisan", required = true) Utisanje utisan) {
        this.utisan = utisan;
    }

    /** Svako drugo polje je greška (mapper inače preskače nepoznata polja). */
    @JsonAnySetter
    void nepoznatoPolje(String ime, Object vrednost) {
        throw new IllegalArgumentException("Nepoznato polje: " + ime);
    }
}
