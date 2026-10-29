package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Uživo rezultati studenta: zbir ({@code null} bez ijednog dugovanog pitanja) i časovi, najnoviji prvi. */
public record UzivoStudenta(RangTacnostiStavka ukupno, List<UzivoStudentaRed> casovi) {
}
