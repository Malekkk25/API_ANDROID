package tn.enicarthage.android_project.livraisons.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class MessageController {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    // 1. CONVERSATION
    @GetMapping("/conversation")
    public ResponseEntity<?> getMessages(@RequestParam String login,
                                         @RequestParam String password,
                                         @RequestParam Long user1,
                                         @RequestParam Long user2) {

        List<Map<String, Object>> resultats = new ArrayList<>();
        String sql = "SELECT * FROM PROJET_SGBD.MESSAGES WHERE (ID_EXPEDITEUR = ? AND ID_DESTINATAIRE = ?) OR (ID_EXPEDITEUR = ? AND ID_DESTINATAIRE = ?) ORDER BY DATE_ENVOI ASC";

        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, user1); ps.setLong(2, user2);
            ps.setLong(3, user2); ps.setLong(4, user1);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultats.add(mapResultSetToMessage(rs));
                }
            }
            return ResponseEntity.ok(resultats);
        } catch (SQLException e) {
            return ResponseEntity.status(500).body("SQL: " + e.getMessage());
        }
    }

    // 2. ENVOYER - IDENTIQUE LIVREURCONTROLLER
    @PostMapping("/envoyer")
    public ResponseEntity<?> sendMessage(@RequestParam String login,
                                         @RequestParam String password,
                                         @RequestParam Long idExpediteur,
                                         @RequestParam Long idDestinataire,
                                         @RequestParam String contenu,
                                         @RequestParam(defaultValue = "0") Integer isUrgent) {

        System.out.println(">>> INPUT: login='" + login + "' exped=" + idExpediteur + " → " + idDestinataire);

        // CONNEXION AVEC LOGIN UPPERCASE (Oracle case-sensitive)
        String loginUpper = login.toUpperCase();

        String sql = "INSERT INTO PROJET_SGBD.MESSAGES (ID, ID_EXPEDITEUR, ID_DESTINATAIRE, CONTENU, IS_URGENT, DATE_ENVOI) VALUES (PROJET_SGBD.SEQ_MESSAGES.NEXTVAL, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = DriverManager.getConnection(dbUrl, loginUpper, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idExpediteur);
            ps.setLong(2, idDestinataire);
            ps.setString(3, contenu);
            ps.setInt(4, isUrgent);

            int rows = ps.executeUpdate();

            Map<String, String> response = new HashMap<>();
            response.put("status", rows > 0 ? "success" : "error");
            response.put("rows", String.valueOf(rows));

            System.out.println(">>> SUCCESS: " + rows + " lignes");
            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            System.out.println(">>> FAIL: " + e.getMessage());
            return ResponseEntity.status(500).body("SQL: " + e.getMessage());
        }
    }

    // 3. TOUS LES LIVREURS
    @GetMapping("/tous-livreurs")
    public ResponseEntity<?> getTousLivreurs(@RequestParam String login, @RequestParam String password) {
        List<Map<String, Object>> livreurs = new ArrayList<>();

        String sql = """
        SELECT p.idpers, p.nompers, p.prenompers, p.telpers, p.login,
               p.nompers || ' ' || p.prenompers AS nom_complet
        FROM PROJET_SGBD.Personnel p
        JOIN PROJET_SGBD.Postes ps ON p.codeposte = ps.codeposte
        WHERE p.codeposte = 4
        ORDER BY p.nompers, p.prenompers
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl, login.toUpperCase(), password);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> livreur = new HashMap<>();
                livreur.put("id", rs.getLong("idpers"));
                livreur.put("nom", rs.getString("nompers"));
                livreur.put("prenom", rs.getString("prenompers"));
                livreur.put("nomComplet", rs.getString("nom_complet"));
                livreur.put("telephone", rs.getString("telpers"));
                livreur.put("login", rs.getString("login"));
                livreurs.add(livreur);
            }
            return ResponseEntity.ok(livreurs);

        } catch (SQLException e) {
            return ResponseEntity.status(500).body("SQL: " + e.getMessage());
        }
    }

    // 4. TOUS LES CHEFS
    @GetMapping("/tous-chefs")
    public ResponseEntity<?> getTousChefs(@RequestParam String login, @RequestParam String password) {
        List<Map<String, Object>> chefs = new ArrayList<>();

        String sql = """
        SELECT p.idpers, p.nompers, p.prenompers, p.telpers, p.login,
               p.nompers || ' ' || p.prenompers AS nom_complet
        FROM PROJET_SGBD.Personnel p
        JOIN PROJET_SGBD.Postes ps ON p.codeposte = ps.codeposte
        WHERE p.codeposte = 3
        ORDER BY p.nompers, p.prenompers
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl, login.toUpperCase(), password);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> chef = new HashMap<>();
                chef.put("id", rs.getLong("idpers"));
                chef.put("nom", rs.getString("nompers"));
                chef.put("prenom", rs.getString("prenompers"));
                chef.put("nomComplet", rs.getString("nom_complet"));
                chef.put("telephone", rs.getString("telpers"));
                chef.put("login", rs.getString("login"));
                chefs.add(chef);
            }
            return ResponseEntity.ok(chefs);

        } catch (SQLException e) {
            return ResponseEntity.status(500).body("SQL: " + e.getMessage());
        }
    }

    // 5. NOUVEAU: TROUVER ID PERSONNEL PAR LOGIN
    @GetMapping("/trouver-id")
    public ResponseEntity<?> trouverIdParLogin(@RequestParam String login, @RequestParam String password) {
        String sql = "SELECT idpers, nompers, prenompers, codeposte FROM PROJET_SGBD.Personnel WHERE UPPER(login) = UPPER(?)";

        try (Connection conn = DriverManager.getConnection(dbUrl, login.toUpperCase(), password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // On utilise le login passé en paramètre pour chercher dans la table Personnel
            ps.setString(1, login);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> personnel = new HashMap<>();
                    personnel.put("id", rs.getLong("idpers"));
                    personnel.put("nom", rs.getString("nompers"));
                    personnel.put("prenom", rs.getString("prenompers"));
                    personnel.put("codePoste", rs.getInt("codeposte"));
                    return ResponseEntity.ok(personnel);
                } else {
                    return ResponseEntity.status(404).body("Utilisateur non trouvé dans la table Personnel.");
                }
            }
        } catch (SQLException e) {
            return ResponseEntity.status(500).body("SQL: " + e.getMessage());
        }
    }

    // --- UTILS ---
// --- UTILS ---
    private Map<String, Object> mapResultSetToMessage(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("ID"));
        row.put("id_expediteur", rs.getLong("ID_EXPEDITEUR"));
        row.put("id_destinataire", rs.getLong("ID_DESTINATAIRE"));
        row.put("contenu", rs.getString("CONTENU"));
        row.put("is_urgent", rs.getInt("IS_URGENT"));

        // MODIFICATION ICI : On convertit le Timestamp en Millisecondes (Long)
        Timestamp ts = rs.getTimestamp("DATE_ENVOI");
        if (ts != null) {
            row.put("date_envoi", ts.getTime()); // Envoie un nombre que Android lira parfaitement
        } else {
            row.put("date_envoi", null);
        }

        return row;
    }
}