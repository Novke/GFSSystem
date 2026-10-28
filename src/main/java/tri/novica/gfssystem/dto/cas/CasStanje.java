package tri.novica.gfssystem.dto.cas;

import java.time.LocalDateTime;

/**
 * Stanje časa za studenta ({@code GET /public/nalog/cas}): predavanje na kome je "na času" (čekiran u poslednjih 5 h),
 * vreme čekiranja i aktivno izvođenje tog predavanja; "nije na času" je {@link #NIJE_NA_CASU} (sve {@code null}). Za
 * završeno predavanje {@code izvodjenje} je uvek {@code null}.
 */
public record CasStanje(CasPredavanje predavanje, LocalDateTime cekiranU, CasIzvodjenje izvodjenje) {
    public static final CasStanje NIJE_NA_CASU = new CasStanje(null, null, null);
}
