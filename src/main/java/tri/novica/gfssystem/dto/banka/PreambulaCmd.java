package tri.novica.gfssystem.dto.banka;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telo za {@code PUT /predmeti/{id}/latex-preambula}: nova preambula, a {@code null} ili prazan tekst vraća
 * podrazumevanu. Ključ {@code preambula} je obavezan i nepoznato polje je greška (400 {@code Neispravan format
 * podataka.}): promena preambule poništava render keš cele banke, pa {@code {}} ili greška u kucanju ključa ne smeju
 * tiho da je obrišu (isti obrazac kao {@code PragProlazaCmd}).
 */
public record PreambulaCmd(@JsonProperty(value = "preambula", required = true) String preambula) {

    /**
     * Svako drugo polje je greška. Ne može {@code @JsonIgnoreProperties(ignoreUnknown = false)}: to je samo
     * podrazumevana vrednost, a mapper je podešen ({@code use-jackson2-defaults}) da nepoznata polja preskače.
     */
    @JsonAnySetter
    void nepoznatoPolje(String ime, Object vrednost) {
        throw new IllegalArgumentException("Nepoznato polje: " + ime);
    }
}
