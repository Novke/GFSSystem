package tri.novica.gfssystem.dto.banka;

import java.util.List;

/** Varijante masovne radnje (1 do 200 id-jeva). */
public record VarijanteCmd(List<Long> ids) {
}
