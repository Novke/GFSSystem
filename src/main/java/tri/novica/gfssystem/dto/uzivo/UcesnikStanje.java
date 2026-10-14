package tri.novica.gfssystem.dto.uzivo;

/** Učesnik u konzoli: poeni, da li je povezan i da li je odgovorio u trenutnoj rundi. */
public record UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio) {
}
