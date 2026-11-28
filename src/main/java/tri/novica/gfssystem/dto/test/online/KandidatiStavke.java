package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Kandidati stavke: ukupno, po nivou (tačno tri broja, za dozvoljene nivoe stavke) i broj tipova (listova) sa kandidatima. */
public record KandidatiStavke(int ukupno, List<Integer> poNivou, int tipova) {
}
