package tn.enicarthage.android_project.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.enicarthage.android_project.auth.dto.LoginRequest;
import tn.enicarthage.android_project.auth.dto.LoginResponse;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {

        // ✅ Validation des entrées null
        if (request.getLogin() == null || request.getPassword() == null ||
                request.getLogin().isBlank() || request.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Login et mot de passe sont obligatoires.");
        }

        String oracleUser = request.getLogin().toUpperCase().trim();
        String password = request.getPassword();

        System.out.println("🚀 Tentative de connexion Oracle pour : " + oracleUser);

        try (Connection conn = DriverManager.getConnection(dbUrl, oracleUser, password)) {

            System.out.println("✅ Session Oracle établie avec succès.");

            String sqlUser = "SELECT p.idpers, p.nompers, p.prenompers, po.libelle " +
                    "FROM PROJET_SGBD.Personnel p " +
                    "JOIN PROJET_SGBD.Postes po ON p.codeposte = po.codeposte " +
                    "WHERE UPPER(p.Login) = ?";

            int idPers = 0;
            String nomComplet = "";
            String roleApp = "";

            try (PreparedStatement ps = conn.prepareStatement(sqlUser)) {
                ps.setString(1, oracleUser);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        idPers = rs.getInt("idpers");
                        nomComplet = rs.getString("prenompers") + " " + rs.getString("nompers");
                        roleApp = rs.getString("libelle").toUpperCase().trim(); // ✅ trim() ajouté
                    } else {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body("Utilisateur reconnu par Oracle mais absent de la table Personnel.");
                    }
                }
            }

            // ✅ Logique de rôle améliorée (ordre important : CHEF avant LIVREUR)
            if (roleApp.contains("CHEF")) {
                return ResponseEntity.ok(new LoginResponse(idPers, nomComplet, "CHEF_LIVREUR", null));
            } else if (roleApp.contains("LIVREUR")) {
                List<Map<String, Object>> tournee = getTourneeDuJour(conn, idPers);
                return ResponseEntity.ok(new LoginResponse(idPers, nomComplet, "LIVREUR", tournee));
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Accès refusé : rôle non autorisé.");
            }

        } catch (SQLException e) {
            System.err.println("❌ Erreur SQL [" + e.getErrorCode() + "] : " + e.getMessage());

            return switch (e.getErrorCode()) {
                case 1017 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Identifiants invalides.");
                case 28000 -> ResponseEntity.status(HttpStatus.LOCKED)
                        .body("Compte Oracle verrouillé.");
                case 28001 -> ResponseEntity.status(HttpStatus.FORBIDDEN)  // ✅ Ajouté
                        .body("Mot de passe expiré.");
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur base de données : " + e.getMessage());
            };
        }
    }

    private List<Map<String, Object>> getTourneeDuJour(Connection conn, int idLivreur) throws SQLException {
        String sql = "SELECT c.nocde, cl.nomclt, cl.adrclt, lc.etatliv " +
                "FROM PROJET_SGBD.LivraisonCom lc " +
                "JOIN PROJET_SGBD.Commandes c ON lc.nocde = c.nocde " +
                "JOIN PROJET_SGBD.Clients cl ON c.noclt = cl.noclt " +
                "WHERE lc.livreur = ? AND TRUNC(lc.dateliv) = TRUNC(SYSDATE)";

        List<Map<String, Object>> liste = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idLivreur);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("nocde"));
                    row.put("client", rs.getString("nomclt"));
                    row.put("adresse", rs.getString("adrclt"));
                    row.put("etat", rs.getString("etatliv"));
                    liste.add(row);
                }
            }
        }
        return liste;
    }
}