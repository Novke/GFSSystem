package tri.novica.gfssystem.dto.anketa;

import java.util.List;

/** Glas studenta ({@code POST /public/nalog/ankete/{id}/glas}): indeksi izabranih odgovora. */
public record GlasCmd(List<Integer> opcije) {
}
