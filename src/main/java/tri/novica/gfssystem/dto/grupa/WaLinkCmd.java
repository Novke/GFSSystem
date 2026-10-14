package tri.novica.gfssystem.dto.grupa;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Telo za {@code PATCH /grupe/{id}/wa-link}: ključ {@code waLink} je obavezan, a {@code null} ili prazan tekst uklanja
 * link. Telo bez ključa i nepoznata polja su 400 (kao {@code PragProlazaCmd}), da greška u kucanju ne obriše link.
 */
@Data
public class WaLinkCmd {
    @Size(max = 300, message = "Link je predugačak.")
    private final String waLink;

    @JsonCreator
    public WaLinkCmd(@JsonProperty(value = "waLink", required = true) String waLink) {
        this.waLink = waLink;
    }

    /** Svako drugo polje je greška (mapper je podešen da nepoznata polja preskače, vidi {@code PragProlazaCmd}). */
    @JsonAnySetter
    void nepoznatoPolje(String ime, Object vrednost) {
        throw new IllegalArgumentException("Nepoznato polje: " + ime);
    }
}
