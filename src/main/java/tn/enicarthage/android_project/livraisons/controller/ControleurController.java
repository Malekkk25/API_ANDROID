package tn.enicarthage.android_project.livraisons.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    // 1. TOUTES LES LIVRAISONS
    @GetMapping("/liste-livraisons")
    public ResponseEntity<?> getToutesLivraisons(
            @RequestParam String login,
            @RequestParam String password) {

        if (login == null || login.isBlank() || password == null || password.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Login et password obligatoires.");

        String oracleUser = login.toUpperCase().trim();
        List<Map<String, Object>> resultats = new ArrayList<>();

        String sql = "SELECT lc.nocde, lc.dateliv, lc.etatliv, p.nompers, p.prenompers, cl.nomclt " +
                "FROM " + SCHEMA + "LivraisonCom lc " +
                "JOIN " + SCHEMA + "Personnel p ON lc.livreur = p.idpers " +
                "JOIN " + SCHEMA + "Commandes c ON lc.nocde = c.nocde " +
                "JOIN " + SCHEMA + "Clients cl ON c.noclt = cl.noclt " +
                "ORDER BY lc.dateliv DESC";

        try (Connection conn = DriverManager.getConnection(dbUrl, oracleUser, password);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("nocde", rs.getInt("nocde"));
                row.put("date", rs.getDate("dateliv"));
                row.put("etat", rs.getString("etatliv"));
                row.put("livreur", rs.getString("prenompers") + " " + rs.getString("nompers"));
                row.put("client", rs.getString("nomclt"));
                resultats.add(row);
            }
            return ResponseEntity.ok(resultats);

        } catch (SQLException e) {
            return switch (e.getErrorCode()) {
                case 1017 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
                case 28000 -> ResponseEntity.status(HttpStatus.LOCKED).body("Compte Oracle verrouillé.");
                case 28001 -> ResponseEntity.status(HttpStatus.FORBIDDEN).body("Mot de passe expiré.");
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur SQL : " + e.getMessage());
            };
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Le contenu du message est obligatoire.");

        String oracleUser = login.toUpperCase().trim();
        String sql = "INSERT INTO " + SCHEMA + "Messages (expediteur, type_msg, contenu) VALUES (?, 'INFO', ?)";

        try (Connection conn = DriverManager.getConnection(dbUrl, oracleUser, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, oracleUser);
            ps.setString(2, contenu);
            ps.executeUpdate();
            return ResponseEntity.ok("Message d'information diffusé.");

        } catch (SQLException e) {
            return switch (e.getErrorCode()) {
                case 1017 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
                case 28000 -> ResponseEntity.status(HttpStatus.LOCKED).body("Compte Oracle verrouillé.");
                case 28001 -> ResponseEntity.status(HttpStatus.FORBIDDEN).body("Mot de passe expiré.");
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur SQL : " + e.getMessage());
            };
        }
    }
}