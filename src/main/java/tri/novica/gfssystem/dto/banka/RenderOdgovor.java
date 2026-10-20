package tri.novica.gfssystem.dto.banka;

/**
 * Odgovor render servisa kako ga vraća {@code LatexRender}. Uspeh nosi {@code svg}, {@code pdf} i/ili {@code png}
 * (base64) prema traženim formatima, neuspeh kompilacije {@code greska} i {@code log}. {@code odbijen} ne dolazi iz
 * JSON-a servisa: postavlja ga samo {@link #odbijen(String)} kad servis odbije zahtev (4xx), a poruka je tada telo
 * odgovora, koje nije za nastavnika.
 */
public record RenderOdgovor(boolean ok, String svg, String pdf, RenderGreskaInfo greska, String log, Long trajanjeMs,
                            boolean odbijen, String png) {

    /** Odgovor bez PNG-a (oblik pre formata {@code png}). */
    public RenderOdgovor(boolean ok, String svg, String pdf, RenderGreskaInfo greska, String log, Long trajanjeMs,
                         boolean odbijen) {
        this(ok, svg, pdf, greska, log, trajanjeMs, odbijen, null);
    }

    /** Servis je odbio zahtev (4xx): {@code ok=false}, bez loga, telo odgovora kao poruka. */
    public static RenderOdgovor odbijen(String poruka) {
        return new RenderOdgovor(false, null, null, new RenderGreskaInfo(null, poruka), null, null, true);
    }
}
