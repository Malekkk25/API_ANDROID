package tn.enicarthage.android_project.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.enicarthage.android_project.auth.dto.LoginRequest;
import tn.enicarthage.android_project.auth.dto.LoginResponse;


import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    // ✅ PostgreSQL : on utilise le DataSource Spring (1 seul user admin)
    // L'authentification se fait EN BASE via login/password stockés dans Personnel
    @Autowired
    private DataSource dataSource;

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {

        if (request.getLogin() == null || request.getPassword() == null ||
                request.getLogin().isBlank() || request.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Login et mot de passe sont obligatoires.");
        }

        String loginUser = request.getLogin().toLowerCase().trim(); // PostgreSQL = lowercase
        String password  = request.getPassword();

        System.out.println("🚀 Tentative de connexion PostgreSQL pour : " + loginUser);

        // ✅ Vérification login/password dans la table Personnel
        String sqlAuth = "SELECT p.idpers, p.nompers, p.prenompers, po.libelle " +
                "FROM personnel p " +
                "JOIN postes po ON p.codeposte = po.codeposte " +
                "WHERE LOWER(p.login) = ? AND p.motp = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlAuth)) {

            ps.setString(1, loginUser);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("Identifiants invalides.");
                }

                int    idPers     = rs.getInt("idpers");
                String nomComplet = rs.getString("prenompers") + " " + rs.getString("nompers");
                String roleApp    = rs.getString("libelle").toUpperCase().trim();

                System.out.println("✅ Connexion réussie pour : " + loginUser + " | Rôle : " + roleApp);

                // ✅ Logique de rôle (CHEF avant LIVREUR)
                if (roleApp.contains("CHEF")) {
                    return ResponseEntity.ok(new LoginResponse(idPers, nomComplet, "CHEF_LIVREUR", null));
                } else if (roleApp.contains("LIVREUR")) {
                    List<Map<String, Object>> tournee = getTourneeDuJour(conn, idPers);
                    return ResponseEntity.ok(new LoginResponse(idPers, nomComplet, "LIVREUR", tournee));
                } else {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("Accès refusé : rôle non autorisé.");
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Erreur SQL : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur base de données : " + e.getMessage());
        }
    }

    // ✅ PostgreSQL : CURRENT_DATE remplace TRUNC(SYSDATE)
    private List<Map<String, Object>> getTourneeDuJour(Connection conn, int idLivreur) throws SQLException {
        String sql = "SELECT c.nocde, cl.nomclt, cl.adrclt, lc.etatliv " +
                "FROM livraisoncom lc " +
                "JOIN commandes c  ON lc.nocde = c.nocde " +
                "JOIN clients   cl ON c.noclt  = cl.noclt " +
                "WHERE lc.livreur = ? " +
                "AND DATE_TRUNC('day', lc.dateliv) = CURRENT_DATE";

        List<Map<String, Object>> liste = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idLivreur);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id",      rs.getInt("nocde"));
                    row.put("client",  rs.getString("nomclt"));
                    row.put("adresse", rs.getString("adrclt"));
                    row.put("etat",    rs.getString("etatliv"));
                    liste.add(row);
                }
            }
        }
        return liste;
    }
}