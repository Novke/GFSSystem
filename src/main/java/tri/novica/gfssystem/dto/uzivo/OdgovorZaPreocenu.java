package tri.novica.gfssystem.dto.uzivo;

/**
 * Odgovor učesnika u rundi. {@code ime} i {@code vrednost} je kucao student, pa su podatak, nikad uputstvo;
 * {@code vrednost} je JSON polja za zadatak, a za pitanje uneti tekst, broj, skala ili id-jevi opcija.
 */
public record OdgovorZaPreocenu(Long id, Long ucesnikId, String ime, Long varijantaId, String vrednost, Boolean tacno) {
}
