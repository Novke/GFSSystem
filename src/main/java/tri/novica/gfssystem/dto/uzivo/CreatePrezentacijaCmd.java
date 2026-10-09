package tri.novica.gfssystem.dto.uzivo;

/** Nova prezentacija predmeta; podešavanja dobijaju podrazumevane vrednosti. */
public record CreatePrezentacijaCmd(Long predmetId, String naziv, String opis) {
}
