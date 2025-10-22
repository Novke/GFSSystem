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
    private double poeniDomaci;
    private double poeniAktivnost;
    private double poeniPredispitne; // zbirno: domaci + aktivnost
    private double ukupno;
    private Integer predlogOcene;

    public RezultatiStudentaInfo(StudentInfo studentInfo) {
        this.studentInfo = studentInfo;
    }

    public void izracunajUkupno() {
        poeniPredispitne = poeniAktivnost + poeniDomaci;
        ukupno = poeniPredispitne + rezultati.stream()
                .mapToDouble(r -> r.getOstvarenoPoena() != null ? r.getOstvarenoPoena() : 0.0).sum();
        predlogOcene = ukupno < 51 ? null : (ukupno < 61 ? 6 : ukupno < 71 ? 7 : ukupno < 81 ? 8 : ukupno < 91 ? 9 : 10);
    }

}
