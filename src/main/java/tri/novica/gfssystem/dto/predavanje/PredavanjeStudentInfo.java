package tri.novica.gfssystem.dto.predavanje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredavanjeStudentInfo {
    private Long id;
    private String ime;
    private String prezime;
    private String indeks;
}
