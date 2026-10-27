package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Zadatak u nastavničkom stanju ({@code null} van ZADATAK slajda). {@code izmenaBrojaVarijanti}: izbor iz konzole
 * ({@code null} = kao na slajdu, 0 = sve); {@code izmenaNivoa}: izbor nivoa iz konzole ({@code null} = kao na slajdu, 0 =
 * svi); {@code nivoZaOtvaranje} = nivo koji važi za sledeće otvaranje ({@code null} = svi);
 * {@code brojZaOtvaranje} = koliko će se izvući; {@code varijante} {@code null}
 * dok runde nema; {@code rezultat} (konzola) sve vidi; {@code javniRezultat} (projektor) po pravilima vidljivosti,
 * {@code null} dok nijedan od prekidača rezultati, TACAN i RESENJE nije uključen.
 */
public record NastavnickiZadatak(ZadatakSlajdaInfo slajd, Integer izmenaBrojaVarijanti, Integer izmenaNivoa,
                                 Integer nivoZaOtvaranje, int brojZaOtvaranje,
                                 boolean resenjePrikazano, List<VarijantaRunde> varijante, ZadatakRezultat rezultat,
                                 ZadatakRezultat javniRezultat) {
}
