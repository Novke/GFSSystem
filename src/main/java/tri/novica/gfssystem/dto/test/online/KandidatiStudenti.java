package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Ko može da polaže: studenti grupe (čekirani) i stariji sa aktivnošću ili čekiranjem ove školske godine. */
public record KandidatiStudenti(List<StudentKratko> grupa, List<StudentKratko> stariji) {
}
