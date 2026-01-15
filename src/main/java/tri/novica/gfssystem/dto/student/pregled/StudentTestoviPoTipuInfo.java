package tri.novica.gfssystem.dto.student.pregled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentTestoviPoTipuInfo {
    private TipTestaInfo tipTesta;
    private List<StudentPregledTestInfo> polaganja = new ArrayList<>();
    private StudentPregledTestInfo najboljePolaganje;
}
