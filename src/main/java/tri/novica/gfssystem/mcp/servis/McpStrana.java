package tri.novica.gfssystem.mcp.servis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.List;

/** Jedna strana spiska za Kloda; {@code strana} je 1-based, {@code ukupno} je broj svih stavki. */
public record McpStrana<T>(List<T> stavke, int strana, int brojStrana, long ukupno) {

    static final String NEISPRAVNA_STRANA = "Neispravan parametar: strana.";

    /**
     * Zahtev za stranu {@code strana} (od 1) sa {@code velicina} stavki, bez sorta (redosled daje upit). Strana manja od 1
     * ili ofset van {@code int}-a je 400 {@value #NEISPRAVNA_STRANA}.
     */
    public static Pageable zahtev(int strana, int velicina) {
        return zahtev(strana, velicina, Sort.unsorted());
    }

    /** Kao {@link #zahtev(int, int)}, sa sortom (npr. pretraga banke: najnovije izmenjene prve). */
    public static Pageable zahtev(int strana, int velicina, Sort sort) {
        if (strana < 1 || (long) (strana - 1) * velicina > Integer.MAX_VALUE) {
            throw new SystemException(NEISPRAVNA_STRANA, HttpStatus.BAD_REQUEST);
        }
        return PageRequest.of(strana - 1, velicina, sort);
    }

    /** Strana Spring Data-e (0-based) kao strana za Kloda (1-based). */
    public static <T> McpStrana<T> iz(Page<T> strana) {
        return new McpStrana<>(strana.getContent(), strana.getNumber() + 1, strana.getTotalPages(),
                strana.getTotalElements());
    }

    /** Strana koju servis vraća kao REST model ({@code PagedModel}, 0-based) kao strana za Kloda (1-based). */
    public static <T> McpStrana<T> iz(PagedModel<T> strana) {
        PagedModel.PageMetadata m = strana.getMetadata();
        return new McpStrana<>(strana.getContent(), Math.toIntExact(m.number()) + 1, Math.toIntExact(m.totalPages()),
                m.totalElements());
    }
}
