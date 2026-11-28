package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Zadatak u rezultatu za studenta; {@code resenjeSvgId} samo uz tačne odgovore. */
public record ZadatakRezultataStudenta(int rb, String naziv, String svgId, String resenjeSvgId, Double poeni, double maxPoena, List<PoljeRezultata> polja) {
}
