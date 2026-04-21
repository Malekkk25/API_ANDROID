package tn.enicarthage.android_project.livraisons.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/controleur")
@CrossOrigin(origins = "*")
public class ControleurController {

    @Autowired
    private DataSource dataSource;

    // ✅ Vérifie login/password et retourne le rôle
    private String authentifierChef(Connection conn, String login, String password) throws SQLException {
        String sql = "SELECT po.libelle FROM personnel p " +
                "JOIN postes po ON p.codeposte = po.codeposte " +
                "WHERE LOWER(p.login) = ? AND p.motp = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, login.toLowerCase().trim());
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("libelle").toUpperCase();
                return null;
            }
        }
    }

    // 1. TOUTES LES LIVRAISONS
    @GetMapping("/liste-livraisons")
    public ResponseEntity<?> getToutesLivraisons(
            @RequestParam String login,
            @RequestParam String password) {

        if (login == null || login.isBlank() || password == null || password.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Login et password obligatoires.");

        try (Connection conn = dataSource.getConnection()) {

            String role = authentifierChef(conn, login, password);
            if (role == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
            if (!role.contains("CHEF"))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Accès refusé : rôle insuffisant.");

            // ✅ PostgreSQL : pas de schéma, tables lowercase
            String sql = "SELECT lc.nocde, lc.dateliv, lc.etatliv, " +
                    "p.nompers, p.prenompers, cl.nomclt " +
                    "FROM livraisoncom lc " +
                    "JOIN personnel p ON lc.livreur = p.idpers " +
                    "JOIN commandes c ON lc.nocde   = c.nocde " +
                    "JOIN clients  cl ON c.noclt    = cl.noclt " +
                    "ORDER BY lc.dateliv DESC";

            List<Map<String, Object>> resultats = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("nocde",   rs.getInt("nocde"));
                    row.put("date",    rs.getDate("dateliv"));
                    row.put("etat",    rs.getString("etatliv"));
                    row.put("livreur", rs.getString("prenompers") + " " + rs.getString("nompers"));
                    row.put("client",  rs.getString("nomclt"));
                    resultats.add(row);
                }
            }
            return ResponseEntity.ok(resultats);

        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur SQL : " + e.getMessage());
        }
    }

    // 2. ENVOYER UN MESSAGE D'INFO
    @PostMapping("/message/info")
    public ResponseEntity<?> sendInfo(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam String contenu) {

        if (login == null || login.isBlank() || password == null || password.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Login et password obligatoires.");

        if (contenu == null || contenu.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Contenu obligatoire.");

        try (Connection conn = dataSource.getConnection()) {

            String role = authentifierChef(conn, login, password);
            if (role == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
            if (!role.contains("CHEF"))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Accès refusé : rôle insuffisant.");

            String sql = "INSERT INTO messages (expediteur, type_msg, contenu) VALUES (?, 'INFO', ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, login.toLowerCase().trim());
                ps.setString(2, contenu);
                ps.executeUpdate();
                return ResponseEntity.ok("Message d'information diffusé.");
            }

        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur SQL : " + e.getMessage());
        }
    }
}