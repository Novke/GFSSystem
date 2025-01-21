package tri.novica.gfssystem.dto.ocenjivanje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.student.StudentInfo;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RezultatiStudentaInfo {

    private StudentInfo studentInfo;
    private List<MaxPoeniStudentaNaTestuInfo> rezultati = new ArrayList<>();
    private double poenidomaci;
    private double poeniAktivnost;
    private double ukupno;
    private Integer predlogOcene;

    public RezultatiStudentaInfo(StudentInfo studentInfo) {
        this.studentInfo = studentInfo;
    }

    public void izracunajUkupno() {
        ukupno = poeniAktivnost + poenidomaci + rezultati.stream()
                .mapToDouble(MaxPoeniStudentaNaTestuInfo::getOstvarenoPoena).sum();
        predlogOcene = ukupno < 51 ? null : (ukupno < 61 ? 6 : ukupno < 71 ? 7 : ukupno < 81 ? 8 : ukupno < 91 ? 9 : ukupno <= 100 ? 10 : 11);
    }

}
