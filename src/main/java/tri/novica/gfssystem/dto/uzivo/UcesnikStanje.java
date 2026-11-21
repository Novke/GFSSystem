package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.Utisanje;

/**
 * Učesnik u konzoli: poeni, da li je povezan i da li je odgovorio u trenutnoj rundi; za trenutnu rundu Zadatak slajda
 * i dodeljena varijanta. Za odgovor u trenutnoj rundi (pitanje ili zadatak, tab "Ovo pitanje" u konzoli): tačnost
 * ({@code null} bez tačnog odgovora), vreme odgovora, kratak prikaz odgovora ({@link
 * tri.novica.gfssystem.service.uzivo.KratakOdgovor}) i da li je tekst sakriven sa table; bez odgovora sve {@code null}.
 * {@code stariji}: oznaka studenta iza učesnika, samo za konzolu (projektor je ne crta). Moderacija (V25), samo konzola:
 * {@code utisan} (do kraja izvođenja), {@code oznacen} (student ima oznaku ponašanja na ovom predavanju, a izvođenje bez
 * predavanja: u ovom izvođenju), {@code brojOznaka} (sve oznake studenta na kartonu; gost 0) i {@code student} (prijavljen
 * student, ima karton; gost {@code false}). {@code udeo}: udeo odgovora u trenutnoj rundi (0..1, delimično tačno kod "više
 * tačnih"), vidljiv kad i {@code tacno}.
 */
public record UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio, Long varijantaId,
                            Boolean tacno, Long vremeMs, String odgovor, boolean odgovorSakriven, boolean stariji,
                            Utisanje utisan, boolean oznacen, int brojOznaka, boolean student,
                            Double udeo) {

    public UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio, Long varijantaId,
                         Boolean tacno) {
        this(id, ime, poeni, povezan, odgovorio, varijantaId, tacno, null, null, false, false, null, false, 0, false,
                null);
    }

    public UcesnikStanje(Long id, String ime, int poeni, boolean povezan, boolean odgovorio) {
        this(id, ime, poeni, povezan, odgovorio, null, null);
    }
}
