package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.*;

import java.util.List;

/**
 * Stanje izvođenja za telefone i javni topik (spec 4.4). {@code rezultat} samo kad su rezultati prikazani (bez
 * sakrivenih tekstova, tačnost tek posle TACAN); {@code rangLista} (top 5, bez id-jeva) samo kad je prikazana ili na
 * kraju takmičenja.
 */
public record JavnoStanje(Long izvodjenjeId, long verzija, long serverVremeMs, StatusIzvodjenja status, String naziv,
                          String kod, Prikaz prikaz, TipSlajda slajdTip, Ekran ekran, boolean takmicenje,
                          TelefonPrikaz telefonPrikaz, boolean detaljiDozvoljeni, int brojUcesnika,
                          JavnoPitanje pitanje, Rezultat rezultat, List<RangStavka> rangLista) {
}
