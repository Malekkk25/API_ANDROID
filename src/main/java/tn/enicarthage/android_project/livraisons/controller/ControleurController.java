package tn.enicarthage.android_project.livraison.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/controleur")
@CrossOrigin(origins = "*")
public class ControleurController {

    @Value("${spring.datasource.url}")
    private String dbUrl;
    private final String SCHEMA = "PROJET_SGBD.";

    // 1. RECHERCHE DE LIVRAISONS (Par date ou Livreur)
    @GetMapping("/recherche")
    public ResponseEntity<?> chercherLivraisons(@RequestParam String login, @RequestParam String password,
                                                @RequestParam(required = false) String dateFiltre,
                                                @RequestParam(required = false) String nomLivreur) {
        List<Map<String, Object>> resultats = new ArrayList<>();

        // On construit la requête dynamiquement selon les filtres
        StringBuilder sql = new StringBuilder(
                "SELECT lc.nocde, lc.dateliv, lc.etatliv, p.nompers, cl.nomclt " +
                        "FROM " + SCHEMA + "LivraisonCom lc " +
                        "JOIN " + SCHEMA + "Personnel p ON lc.livreur = p.idpers " +
                        "JOIN " + SCHEMA + "Commandes c ON lc.nocde = c.nocde " +
                        "JOIN " + SCHEMA + "Clients cl ON c.noclt = cl.noclt WHERE 1=1 "
        );

        if (dateFiltre != null) sql.append(" AND TRUNC(lc.dateliv) = TO_DATE(?, 'YYYY-MM-DD')");
        if (nomLivreur != null) sql.append(" AND UPPER(p.nompers) LIKE UPPER(?)");

        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (dateFiltre != null) ps.setString(paramIndex++, dateFiltre);
            if (nomLivreur != null) ps.setString(paramIndex++, "%" + nomLivreur + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("nocde", rs.getInt("nocde"));
                    row.put("date", rs.getDate("dateliv"));
                    row.put("etat", rs.getString("etatliv"));
                    row.put("livreur", rs.getString("nompers"));
                    row.put("client", rs.getString("nomclt"));
                    resultats.add(row);
                }
            }
            return ResponseEntity.ok(resultats);
        } catch (SQLException e) {
            return ResponseEntity.status(500).body("Erreur SQL : " + e.getMessage());
        }
    }

    // 2. DASHBOARD : Livraisons par Livreur et État
    @GetMapping("/dashboard/livreur-etat")
    public ResponseEntity<?> dashLivreurEtat(@RequestParam String login, @RequestParam String password) {
        List<Map<String, Object>> stats = new ArrayList<>();
        String sql = "SELECT p.nompers, lc.etatliv, COUNT(lc.nocde) as total " +
                "FROM " + SCHEMA + "LivraisonCom lc " +
                "JOIN " + SCHEMA + "Personnel p ON lc.livreur = p.idpers " +
                "GROUP BY p.nompers, lc.etatliv";

        return executerRequeteDashboard(sql, login, password, "nompers");
    }

    // 3. DASHBOARD : Livraisons par Client et État
    @GetMapping("/dashboard/client-etat")
    public ResponseEntity<?> dashClientEtat(@RequestParam String login, @RequestParam String password) {
        String sql = "SELECT cl.nomclt, lc.etatliv, COUNT(lc.nocde) as total " +
                "FROM " + SCHEMA + "LivraisonCom lc " +
                "JOIN " + SCHEMA + "Commandes c ON lc.nocde = c.nocde " +
                "JOIN " + SCHEMA + "Clients cl ON c.noclt = cl.noclt " +
                "GROUP BY cl.nomclt, lc.etatliv";

        return executerRequeteDashboard(sql, login, password, "nomclt");
    }

    // Méthode utilitaire pour éviter de répéter le code des Dashboards
    private ResponseEntity<?> executerRequeteDashboard(String sql, String login, String password, String cleNom) {
        List<Map<String, Object>> stats = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("nom", rs.getString(cleNom));
                row.put("etat", rs.getString("etatliv"));
                row.put("total", rs.getInt("total"));
                stats.add(row);
            }
            return ResponseEntity.ok(stats);
        } catch (SQLException e) {
            return ResponseEntity.status(500).body("Erreur SQL : " + e.getMessage());
        }
    }

    // 4. ENVOYER UN MESSAGE D'INFO AUX LIVREURS
    @PostMapping("/message/info")
    public ResponseEntity<?> sendInfo(@RequestParam String login, @RequestParam String password, @RequestParam String contenu) {
        String sql = "INSERT INTO " + SCHEMA + "Messages (expediteur, type_msg, contenu) VALUES (?, 'INFO', ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, contenu);
            ps.executeUpdate();
            return ResponseEntity.ok("Message d'information diffusé.");
        } catch (SQLException e) {
            return ResponseEntity.status(500).body("Erreur SQL : " + e.getMessage());
        }
    }
}