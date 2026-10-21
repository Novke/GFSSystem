package tri.novica.gfssystem.dto.uzivo;

/**
 * Zadatak slajda tipa ZADATAK (ulaz): čvor banke ({@code grupaId}, grupa predmeta prezentacije, namena PREDAVANJE),
 * tajmer ({@code vremeSekunde} 5..600, {@code null} = bez tajmera) i broj različitih varijanti koje se izvlače pri
 * otvaranju ({@code brojVarijanti} 1..50, {@code null} = sve aktivne). Pravila: {@code validation/SlajdPP}; da je grupa
 * iz banke ovog predmeta proverava {@code PrezentacijaService}.
 */
public record ZadatakSlajda(Long grupaId, Integer vremeSekunde, Integer brojVarijanti) {
}
