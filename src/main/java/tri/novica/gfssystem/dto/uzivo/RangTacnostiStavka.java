package tri.novica.gfssystem.dto.uzivo;

/**
 * Red ranga tačnosti (spec rang tačnosti 4). {@code mesto} je {@code null} samo za studenta grupe bez ijednog dugovanog
 * pitanja (dno liste grupe). {@code ucesnikId}: samo u rangu jednog izvođenja; {@code studentId}: učesnik povezan sa
 * studentom; {@code gost}: učesnik bez studenta (spojen po imenu u okviru predavanja). {@code procenat} = ceo procenat
 * {@code tacnih/dugovanih}, {@code prosekMs} = prosečno vreme odgovorenih ({@code null} bez odgovora), {@code zvezdice} =
 * ★ tačnih odgovora. {@code poeni}: samo u rangu izvođenja takmičenja; {@code izvodjenja}: u zbiru predavanja broj
 * izvođenja u kojima je dugovao bar jedno pitanje; {@code casova}: u zbiru grupe broj časova (predavanja, a izvođenje bez
 * predavanja kao poseban čas). {@code indeks}: kanonski indeks studenta ({@code GD5}, bez godine), {@code null} za gosta i
 * na projektoru.
 */
public record RangTacnostiStavka(Integer mesto, Long ucesnikId, Long studentId, String ime, boolean gost, int tacnih,
                                 int dugovanih, int odgovorenih, Integer procenat, Long prosekMs, int zvezdice,
                                 Integer poeni, Integer izvodjenja, Integer casova, String indeks) {
}
