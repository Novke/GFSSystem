package tri.novica.gfssystem.dto.uzivo;

import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.utility.Promena;

/**
 * Klodova delimična izmena slajda (merge patch, {@code McpPatch}): odsutno polje ostaje kakvo jeste. Koja polja važe za koji
 * tip slajda proverava {@code PrezentacijaService}; {@code pitanje}, {@code zadatak} i {@code platno} zamenjuju se cela.
 */
public record SlajdPatch(Promena<String> naslov, Promena<String> sadrzaj, Promena<String> slikaId, Promena<String> beleske,
                         Promena<Boolean> postepeno, Promena<Prelaz> prelaz, Promena<PitanjeCmd> pitanje,
                         Promena<ZadatakSlajda> zadatak, Promena<JsonNode> platno) {

    /** Nijedno polje nije navedeno. */
    public boolean prazan() {
        return !(naslov.postavljeno() || sadrzaj.postavljeno() || slikaId.postavljeno() || beleske.postavljeno()
                || postepeno.postavljeno() || prelaz.postavljeno() || pitanje.postavljeno() || zadatak.postavljeno()
                || platno.postavljeno());
    }

    /** Izmena samo platna ({@code zameni_platno}). */
    public static SlajdPatch samoPlatno(JsonNode platno) {
        return new SlajdPatch(Promena.bez(), Promena.bez(), Promena.bez(), Promena.bez(), Promena.bez(), Promena.bez(),
                Promena.bez(), Promena.bez(), Promena.na(platno));
    }
}
