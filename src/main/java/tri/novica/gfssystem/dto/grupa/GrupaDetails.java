package tri.novica.gfssystem.dto.grupa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrupaDetails {
    private Long id;
    private String naziv;
    private Integer godinaUpisa;
    private List<GrupaStudentInfo> studenti;
}
