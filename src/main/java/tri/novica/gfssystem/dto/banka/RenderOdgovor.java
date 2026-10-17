package tri.novica.gfssystem.dto.banka;

/**
 * Odgovor render servisa kako ga vraća {@code LatexRender}. Uspeh nosi {@code svg} ili {@code pdf} (base64) prema
 * traženom formatu, neuspeh kompilacije {@code greska} i {@code log}. {@code odbijen} ne dolazi iz JSON-a servisa:
 * postavlja ga samo {@link #odbijen(String)} kad servis odbije zahtev (4xx), a poruka je tada telo odgovora, koje nije
 * za nastavnika.
 */
public record RenderOdgovor(boolean ok, String svg, String pdf, RenderGreskaInfo greska, String log, Long trajanjeMs,
                            boolean odbijen) {

    /** Servis je odbio zahtev (4xx): {@code ok=false}, bez loga, telo odgovora kao poruka. */
    public static RenderOdgovor odbijen(String poruka) {
        return new RenderOdgovor(false, null, null, new RenderGreskaInfo(null, poruka), null, null, true);
    }
}
