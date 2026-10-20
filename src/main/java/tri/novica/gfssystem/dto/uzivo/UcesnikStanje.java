package tri.novica.gfssystem.dto.uzivo;

/**
 * Učesnik u konzoli: poeni, da li je povezan i da li je odgovorio u trenutnoj rundi; za trenutnu rundu Zadatak slajda
 * i dodeljena varijanta i tačnost odgovora (inače {@code null}).
 */
public record UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio, Long varijantaId,
                            Boolean tacno) {

    public UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio) {
        this(id, ime, poeni, povezan, odgovorio, null, null);
    }
}
