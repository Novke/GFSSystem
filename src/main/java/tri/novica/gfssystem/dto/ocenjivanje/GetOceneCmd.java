package tri.novica.gfssystem.dto.ocenjivanje;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetOceneCmd {
    @NotNull
    private Long grupaId;
}
