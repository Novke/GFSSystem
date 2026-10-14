package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Uvoz više slajdova odjednom (PowerPoint, spec 7.7): {@code posle} = id slajda posle kog se ubacuju, null = na kraj. */
public record UvozSlajdovaCmd(Long posle, List<SlajdCmd> slajdovi) {
}
