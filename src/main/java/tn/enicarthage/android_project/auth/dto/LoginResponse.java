package tn.enicarthage.android_project.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Integer idPers;
    private String nomComplet;
    private String role;
    private List<Map<String, Object>> tournee;
}
