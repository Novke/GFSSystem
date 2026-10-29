package tri.novica.gfssystem.dto.uzivo;

/**
 * Učesnik u konzoli: poeni, da li je povezan i da li je odgovorio u trenutnoj rundi; za trenutnu rundu Zadatak slajda
 * i dodeljena varijanta. Za odgovor u trenutnoj rundi (pitanje ili zadatak, tab "Ovo pitanje" u konzoli): tačnost
 * ({@code null} bez tačnog odgovora), vreme odgovora, kratak prikaz odgovora ({@link
 * tri.novica.gfssystem.service.uzivo.KratakOdgovor}) i da li je tekst sakriven sa table; bez odgovora sve {@code null}.
 */
public record UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio, Long varijantaId,
                            Boolean tacno, Long vremeMs, String odgovor, boolean odgovorSakriven) {

    public UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio, Long varijantaId,
                         Boolean tacno) {
        this(id, ime, poeni, povezan, odgovorio, varijantaId, tacno, null, null, false);
    }

    public UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio) {
        this(id, ime, poeni, povezan, odgovorio, null, null);
    }
}
