package tri.novica.gfssystem.mcp.servis;

/** Da li alat vraća PNG render ({@code slika}) i da li slika uključuje rešenje ({@code resenje}). */
public record ZahtevSlike(boolean slika, boolean resenje) {

    public static final ZahtevSlike NE = new ZahtevSlike(false, false);
}
