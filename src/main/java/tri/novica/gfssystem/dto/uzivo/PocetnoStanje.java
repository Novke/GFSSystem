package tri.novica.gfssystem.dto.uzivo;

/** Prvi snimak posle (ponovnog) povezivanja telefona: javno i lično stanje iz istog čitanja. */
public record PocetnoStanje(JavnoStanje javno, LicnoStanje licno) {
}
