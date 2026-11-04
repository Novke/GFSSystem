package tri.novica.gfssystem.dto.uzivo;

/**
 * Učesnik u konzoli: poeni, da li je povezan i da li je odgovorio u trenutnoj rundi; za trenutnu rundu Zadatak slajda
 * i dodeljena varijanta. Za odgovor u trenutnoj rundi (pitanje ili zadatak, tab "Ovo pitanje" u konzoli): tačnost
 * ({@code null} bez tačnog odgovora), vreme odgovora, kratak prikaz odgovora ({@link
 * tri.novica.gfssystem.service.uzivo.KratakOdgovor}) i da li je tekst sakriven sa table; bez odgovora sve {@code null}.
 * {@code stariji}: oznaka studenta iza učesnika, samo za konzolu (projektor je ne crta).
 */
public record UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio, Long varijantaId,
                            Boolean tacno, Long vremeMs, String odgovor, boolean odgovorSakriven, boolean stariji) {

    public UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio, Long varijantaId,
                         Boolean tacno) {
        this(id, ime, poeni, povezan, odgovorio, varijantaId, tacno, null, null, false, false);
    }

    public UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio) {
        this(id, ime, poeni, povezan, odgovorio, null, null);
    }
}
