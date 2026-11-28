package tri.novica.gfssystem.dto.test.online;

import java.util.List;
import tri.novica.gfssystem.entity.TestGrupa;

/** Izbor po grupi testa ({@code grupaTesta == null} u režimu POJEDINACNO). */
public record GrupaIzbora(TestGrupa grupaTesta, int studenata, List<ZadatakIzbora> zadaci) {
}
