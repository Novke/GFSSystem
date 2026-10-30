package tri.novica.gfssystem.dto.push;

import java.util.List;

/**
 * Zvono studenta ({@code GET /public/nalog/obavestenja}): poslednjih 30 vidljivih obaveštenja, najnovije prvo, i
 * {@code novo} = postoji vidljivo obaveštenje novije od poslednjeg otvaranja zvona (crvena tačka).
 */
public record ZvonoInfo(boolean novo, List<StavkaZvonaInfo> stavke) {
}
