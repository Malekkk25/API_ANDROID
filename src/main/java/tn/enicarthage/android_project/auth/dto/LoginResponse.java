package tn.enicarthage.android_project.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class LoginResponse {
    private int idPers;
    private String nomComplet;
    private String role;
    private List<Map<String, Object>> tournee;
}