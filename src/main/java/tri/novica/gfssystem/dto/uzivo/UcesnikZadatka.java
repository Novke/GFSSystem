package tri.novica.gfssystem.dto.uzivo;

import java.util.Map;

/**
 * Učesnik runde zadatka u pregledu (samo nastavnik): dodeljena varijanta, da li je odgovorio, tačnost, tačnost po polju
 * ({@code polja}) i šta je uneo po polju ({@code uneto}, tekst za prikaz, redom šeme: {@code UnetoZadatka}); bez odgovora
 * oba {@code null}. Telefoni i projektor ovaj zapis nikad ne dobijaju.
 */
public record UcesnikZadatka(Long ucesnikId, String ime, Long varijantaId, String varijantaNaziv, boolean odgovorio,
                             Boolean tacno, Map<String, Boolean> polja, Map<String, String> uneto) {
}
