package tri.novica.gfssystem.dto.domaci;

import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.student.StudentInfo;

import java.util.List;

@Data
@NoArgsConstructor
public class DomaciGrupaInfo {
    private Long id;
    private String naziv;
    private Integer godinaUpisa;
//    private List<StudentInfo> studenti;
}
