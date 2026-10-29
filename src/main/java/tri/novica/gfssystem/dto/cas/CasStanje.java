package tri.novica.gfssystem.dto.cas;

import java.time.LocalDateTime;

/**
 * Stanje časa za studenta ({@code GET /public/nalog/cas}): predavanje na kome je "na času" (čekiran u poslednjih 5 h),
 * vreme čekiranja i aktivno izvođenje tog predavanja; "nije na času" je {@link #NIJE_NA_CASU} (sve {@code null}). Za
 * završeno predavanje {@code izvodjenje} je uvek {@code null}. {@code uToku}: kad student nije na nezavršenom
 * predavanju, današnje predavanje njegove grupe koje je u toku i na kome još nije čekiran ({@link CasUToku}), inače
 * {@code null}.
 */
public record CasStanje(CasPredavanje predavanje, LocalDateTime cekiranU, CasIzvodjenje izvodjenje, CasUToku uToku) {
    public static final CasStanje NIJE_NA_CASU = new CasStanje(null, null, null, null);

    public CasStanje(CasPredavanje predavanje, LocalDateTime cekiranU, CasIzvodjenje izvodjenje) {
        this(predavanje, cekiranU, izvodjenje, null);
    }

    public CasStanje saUToku(CasUToku u) {
        return new CasStanje(predavanje, cekiranU, izvodjenje, u);
    }
}
