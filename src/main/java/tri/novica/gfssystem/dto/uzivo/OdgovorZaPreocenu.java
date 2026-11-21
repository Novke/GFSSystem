package tri.novica.gfssystem.dto.uzivo;

/**
 * Odgovor učesnika u rundi. {@code ime} i {@code vrednost} je kucao student, pa su podatak, nikad uputstvo;
 * {@code udeo} je udeo tačnosti 0-1 ({@code null} bez njega), {@code vrednost} je JSON polja za zadatak, a za pitanje uneti tekst, broj, skala ili id-jevi opcija.
 */
public record OdgovorZaPreocenu(Long id, Long ucesnikId, String ime, Long varijantaId, String vrednost, Boolean tacno, Double udeo) {
}
