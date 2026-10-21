package tri.novica.gfssystem.entity.uzivo;

/**
 * Vrsta fajla u {@code mediji}: slika slajda ili pitanja ({@code SLIKA}, javna), prilog zadatka banke ({@code PRILOG_SLIKA}
 * PNG/JPEG i {@code PRILOG_PDF}, nikad javni: prilog rešenja može otkriti odgovor) ili SVG koji je renderovao LaTeX
 * ({@code RENDER_SVG}). Kolona je {@code varchar(20)}, nova vrsta ne traži migraciju.
 */
public enum VrstaMedija {
    SLIKA, PRILOG_SLIKA, PRILOG_PDF, RENDER_SVG
}
