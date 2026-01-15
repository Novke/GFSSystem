package tri.novica.gfssystem.dto.student.pregled;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.student.StudentInfo;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentNaPredmetuDetails {
    private StudentInfo student;
    private PredmetInfo predmet;
    private String grupaNaziv;

    private List<StudentPregledAktivnostInfo> aktivnosti = new ArrayList<>();
    private List<StudentPregledDomaciInfo> domaci = new ArrayList<>();
    private List<StudentTestoviPoTipuInfo> testoviPoTipu = new ArrayList<>();

    // Sumarni podaci
    private double ukupnoPoenaAktivnost;
    private double ukupnoPoenaDomaci;
}
