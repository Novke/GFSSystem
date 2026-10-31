package tri.novica.gfssystem.dto.portal;

import tri.novica.gfssystem.entity.portal.IzvorOtvaranja;

import java.time.LocalDateTime;

/**
 * Panel "Za vežbanje" na čvoru banke (samo nastavnik): stanje čvora za jednu grupu studenata. {@code stanje}: OTVORENO
 * (zapis na samom čvoru: {@code izvor}, {@code prviPut}), PREKO_TEME (otvoren predak {@code tema}), DELIMICNO (otvoren
 * deo listova ispod čvora), ZATVORENO. {@code otvorenihListova} od {@code listova}: listovi čvora (bez isključenih
 * podgrupa) koje grupa sme da vežba.
 */
public record OtvorenoZaGrupu(Long grupaId, String naziv, Integer godinaUpisa, String stanje, IzvorOtvaranja izvor,
                              LocalDateTime prviPut, String tema, int otvorenihListova, int listova) {
}
