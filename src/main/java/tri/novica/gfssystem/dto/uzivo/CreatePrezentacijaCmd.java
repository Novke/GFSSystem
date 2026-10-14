package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.Tema;

/** Nova prezentacija predmeta; podešavanja dobijaju podrazumevane vrednosti ({@code tema == null} je NACRT). */
public record CreatePrezentacijaCmd(Long predmetId, String naziv, String opis, Tema tema) {
}
