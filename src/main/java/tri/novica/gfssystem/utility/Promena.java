package tri.novica.gfssystem.utility;

/**
 * Jedno polje delimične izmene (merge patch, RFC 7386): {@code postavljeno=false} znači da ključ nije poslat i polje ostaje
 * kakvo jeste; {@code postavljeno=true} sa {@code vrednost=null} briše polje. Pravi je {@code McpPatch.promena}, koji
 * odbija {@code null} za polje koje se ne briše.
 */
public record Promena<T>(boolean postavljeno, T vrednost) {

    private static final Promena<?> BEZ = new Promena<>(false, null);

    @SuppressWarnings("unchecked")
    public static <T> Promena<T> bez() {
        return (Promena<T>) BEZ;
    }

    public static <T> Promena<T> na(T vrednost) {
        return new Promena<>(true, vrednost);
    }

    /** Nova vrednost kad je postavljena (i kad je {@code null}), inače {@code trenutno}. */
    public T primeni(T trenutno) {
        return postavljeno ? vrednost : trenutno;
    }
}
