package tri.novica.gfssystem.dto.cas;

import tri.novica.gfssystem.dto.anketa.AnketaStudentu;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stanje časa za studenta ({@code GET /public/nalog/cas}): predavanje na kome je "na času" (čekiran u poslednjih 5 h),
 * vreme čekiranja i aktivno izvođenje tog predavanja; "nije na času" je {@link #NIJE_NA_CASU} (sve {@code null}). Za
 * završeno predavanje {@code izvodjenje} je uvek {@code null}. {@code uToku}: kad student nije na nezavršenom
 * predavanju, današnje predavanje njegove grupe koje je u toku i na kome još nije čekiran ({@link CasUToku}), inače
 * {@code null}. {@code ankete} (poslednje polje, brza anketa): ankete za koje student sme da glasa, otvorene ili sa
 * prikazanim rezultatom, najnovije prve (najviše 5); nikad {@code null}. Puni ih REST sloj ({@code AnketaService.zaStudenta}).
 */
public record CasStanje(CasPredavanje predavanje, LocalDateTime cekiranU, CasIzvodjenje izvodjenje, CasUToku uToku,
                        List<AnketaStudentu> ankete) {
    public static final CasStanje NIJE_NA_CASU = new CasStanje(null, null, null, null);

    public CasStanje {
        ankete = ankete == null ? List.of() : List.copyOf(ankete);
    }

    public CasStanje(CasPredavanje predavanje, LocalDateTime cekiranU, CasIzvodjenje izvodjenje) {
        this(predavanje, cekiranU, izvodjenje, null);
    }

    public CasStanje(CasPredavanje predavanje, LocalDateTime cekiranU, CasIzvodjenje izvodjenje, CasUToku uToku) {
        this(predavanje, cekiranU, izvodjenje, uToku, List.of());
    }

    public CasStanje saUToku(CasUToku u) {
        return new CasStanje(predavanje, cekiranU, izvodjenje, u, ankete);
    }

    public CasStanje saAnketama(List<AnketaStudentu> a) {
        return new CasStanje(predavanje, cekiranU, izvodjenje, uToku, a);
    }
}
