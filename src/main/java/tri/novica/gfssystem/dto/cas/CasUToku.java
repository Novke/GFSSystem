package tri.novica.gfssystem.dto.cas;

/**
 * Današnje predavanje studentove grupe koje je u toku, a student na njemu još nije čekiran (telefon tada kaže
 * "Predavanje je u toku, skeniraj QR"); samo predmet i tema, bez id-ja i ičega nastavničkog.
 */
public record CasUToku(String predmet, String tema) {
}
