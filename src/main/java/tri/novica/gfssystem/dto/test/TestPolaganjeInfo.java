package tri.novica.gfssystem.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.entity.TestGrupa;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestPolaganjeInfo {

    private Long id;
    private StudentInfo student;
    private TestGrupa grupa;
    private Double ostvareniPoeni;
    private Boolean prepisivao;
    private Boolean polozio;
    private String napomene;
}
