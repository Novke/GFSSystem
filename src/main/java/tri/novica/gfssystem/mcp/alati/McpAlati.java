package tri.novica.gfssystem.mcp.alati;

/**
 * Zajednički tekstovi opisa MCP alata. {@link #NE_PONAVLJAJ} je jedino mesto ovog teksta: opis svakog neidempotentnog
 * upisa ({@code napravi_*}, {@code kopiraj_grupu}, {@code dupliraj_varijantu}, {@code izmeni_varijantu},
 * {@code dodaj_slajd}) se njime završava ({@code description = "..." + McpAlati.NE_PONAVLJAJ}), jer bi ponovljen poziv
 * posle isteka vremena napravio duplikat.
 */
public final class McpAlati {

    public static final String NE_PONAVLJAJ =
            "Ne ponavljaj posle isteka vremena; prvo proveri `pretraga_banke`, `grupa` ili `prezentacija`.";

    private McpAlati() {
    }
}
