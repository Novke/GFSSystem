package tri.novica.gfssystem.utility;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.Set;

/**
 * Provera {@link Pageable} parametara liste ({@code page}, {@code size}, {@code sort}) pre upita.
 * Veličinu stranice ograničava Spring ({@code spring.data.web.pageable.max-page-size}), ovde se proverava sort.
 */
public final class PageableUtil {

    /** Uvek dozvoljen i uvek poslednji kriterijum, da bi redosled bio jednoznačan između stranica. */
    static final String ID = "id";

    private PageableUtil() {
    }

    /**
     * Prazan sort postaje {@code podrazumevani}; polje van {@code dozvoljenaPolja} (i van {@code id}) je
     * 400 "Neispravan parametar: sort." (bez provere bi {@code sort=lozinka} dao 500 iz Spring Data-e, a sort po
     * vezanim entitetima pravio skrivene join-ove). Na kraj se dodaje {@code id desc} ako ga sort već nema.
     * Stranica i veličina se čuvaju; ofset preko {@code Integer.MAX_VALUE} je 400 "Neispravan parametar: page.".
     */
    public static Pageable proveri(Pageable pageable, Set<String> dozvoljenaPolja, Sort podrazumevani) {
        if ((long) pageable.getPageNumber() * pageable.getPageSize() > Integer.MAX_VALUE) {
            throw new SystemException("Neispravan parametar: page.", HttpStatus.BAD_REQUEST);
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : podrazumevani;
        for (Sort.Order order : sort) {
            if (!ID.equals(order.getProperty()) && !dozvoljenaPolja.contains(order.getProperty())) {
                throw new SystemException("Neispravan parametar: sort.", HttpStatus.BAD_REQUEST);
            }
        }
        if (sort.getOrderFor(ID) == null) {
            sort = sort.and(Sort.by(Sort.Order.desc(ID)));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
