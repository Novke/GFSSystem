package tri.novica.gfssystem.dto.test.online;

import tri.novica.gfssystem.entity.TestGrupa;

/** Ponovno izvlačenje u pripremi: {@code grupaTesta}/{@code mesto} null = sve; {@code tipId}, {@code nivo}, {@code varijantaId} biraju ručno. */
public record IzvuciCmd(Long stavkaId, TestGrupa grupaTesta, Integer mesto, Long tipId, Integer nivo, Long varijantaId) {
}
