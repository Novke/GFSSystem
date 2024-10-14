package tri.novica.gfssystem.dto.grupa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrupaStudentInfo {

    private Long id;
    private String ime;
    private String prezime;
    private String indeks;

}
