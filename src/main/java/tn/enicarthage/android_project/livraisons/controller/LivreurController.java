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
@RequestMapping("/api/livreur")
@CrossOrigin(origins = "*")
public class LivreurController {

    @Value("${spring.datasource.url}")
    private String dbUrl;
    private final String SCHEMA = "PROJET_SGBD.";

    // 1. MA TOURNÉE
    @GetMapping("/ma-tournee")
    public ResponseEntity<?> getMaTournee(
            @RequestParam String login,
            @RequestParam String password) {

        if (login == null || login.isBlank() || password == null || password.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Login et password obligatoires.");

        String oracleUser = login.toUpperCase().trim();
        List<Map<String, Object>> tournee = new ArrayList<>();

        String sql = "SELECT lc.nocde, lc.etatliv, cl.nomclt, cl.prenomclt, cl.adrclt, cl.villeclt, cl.telclt " +
                "FROM " + SCHEMA + "LivraisonCom lc " +
                "JOIN " + SCHEMA + "Personnel p ON lc.livreur = p.idpers " +
                "JOIN " + SCHEMA + "Commandes c ON lc.nocde = c.nocde " +
                "JOIN " + SCHEMA + "Clients cl ON c.noclt = cl.noclt " +
                "WHERE UPPER(p.Login) = ? AND TRUNC(lc.dateliv) = TRUNC(SYSDATE)";

        try (Connection conn = DriverManager.getConnection(dbUrl, oracleUser, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, oracleUser);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> livraison = new HashMap<>();
                    livraison.put("nocde", rs.getInt("nocde"));
                    livraison.put("etatliv", rs.getString("etatliv"));
                    livraison.put("nomClient", rs.getString("nomclt") + " " + rs.getString("prenomclt"));
                    livraison.put("adresse", rs.getString("adrclt") + ", " + rs.getString("villeclt"));
                    livraison.put("telephone", rs.getString("telclt"));
                    tournee.add(livraison);
                }
            }
            return ResponseEntity.ok(tournee);

        } catch (SQLException e) {
            return switch (e.getErrorCode()) {
                case 1017 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
                case 28000 -> ResponseEntity.status(HttpStatus.LOCKED).body("Compte Oracle verrouillé.");
                case 28001 -> ResponseEntity.status(HttpStatus.FORBIDDEN).body("Mot de passe expiré.");
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur SQL : " + e.getMessage());
            };
        }
    }

    // 2. DÉTAILS D'UNE COMMANDE
    @GetMapping("/details-commande/{noCde}")
    public ResponseEntity<?> getDetails(
            @PathVariable int noCde,
            @RequestParam String login,
            @RequestParam String password) {

        if (login == null || login.isBlank() || password == null || password.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Login et password obligatoires.");

        String oracleUser = login.toUpperCase().trim();
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> articles = new ArrayList<>();
        double montantTotal = 0.0;

        String sql = "SELECT cl.nomclt, cl.prenomclt, cl.telclt, cl.adrclt, cl.villeclt, lc.modepay, " +
                "a.designation, l.qtecde, a.prix, (l.qtecde * a.prix) as sous_total " +
                "FROM " + SCHEMA + "ligcdes l " +
                "JOIN " + SCHEMA + "articles a ON l.refart = a.refart " +
                "JOIN " + SCHEMA + "commandes c ON l.nocde = c.nocde " +
                "JOIN " + SCHEMA + "clients cl ON c.noclt = cl.noclt " +
                "JOIN " + SCHEMA + "LivraisonCom lc ON c.nocde = lc.nocde " +
                "WHERE l.nocde = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl, oracleUser, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, noCde);
            try (ResultSet rs = ps.executeQuery()) {
                boolean infosSet = false;
                while (rs.next()) {
                    if (!infosSet) {
                        response.put("nomClient", rs.getString("nomclt") + " " + rs.getString("prenomclt"));
                        response.put("telephone", rs.getString("telclt"));
                        response.put("adresseComplete", rs.getString("adrclt") + ", " + rs.getString("villeclt"));
                        response.put("modePayement", rs.getString("modepay"));
                        infosSet = true;
                    }
                    Map<String, Object> art = new HashMap<>();
                    art.put("designation", rs.getString("designation"));
                    art.put("quantite", rs.getInt("qtecde"));
                    art.put("prixUnitaire", rs.getDouble("prix"));
                    art.put("sousTotal", rs.getDouble("sous_total"));
                    articles.add(art);
                    montantTotal += rs.getDouble("sous_total");
                }
            }

            if (articles.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Commande " + noCde + " non trouvée.");

            response.put("articles", articles);
            response.put("montantTotal", montantTotal);
            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            return switch (e.getErrorCode()) {
                case 1017 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
                case 28000 -> ResponseEntity.status(HttpStatus.LOCKED).body("Compte Oracle verrouillé.");
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur SQL : " + e.getMessage());
            };
        }
    }

    // 3. MODIFIER ÉTAT ET REMARQUE
    @PutMapping("/modifier-etat")
    public ResponseEntity<?> updateEtat(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam int noCde,
            @RequestParam String nouvelEtat,
            @RequestParam(required = false) String remarque) {

        if (login == null || login.isBlank() || password == null || password.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Login et password obligatoires.");

        String oracleUser = login.toUpperCase().trim();
        String sql = "UPDATE " + SCHEMA + "LivraisonCom SET etatliv = ?, remarque = ? WHERE nocde = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl, oracleUser, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nouvelEtat);
            ps.setString(2, remarque);
            ps.setInt(3, noCde);

            int rows = ps.executeUpdate();
            return rows > 0
                    ? ResponseEntity.ok("Statut mis à jour.")
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).body("Commande " + noCde + " non trouvée.");

        } catch (SQLException e) {
            return switch (e.getErrorCode()) {
                case 1017 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
                case 28000 -> ResponseEntity.status(HttpStatus.LOCKED).body("Compte Oracle verrouillé.");
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur SQL : " + e.getMessage());
            };
        }
    }

    // 4. MESSAGE D'URGENCE
    @PostMapping("/urgence")
    public ResponseEntity<?> sendUrgence(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam int noCde,
            @RequestParam String telephone,
            @RequestParam String contenu) {

        if (login == null || login.isBlank() || password == null || password.isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Login et password obligatoires.");

        String oracleUser = login.toUpperCase().trim();
        String sql = "INSERT INTO " + SCHEMA + "Messages (expediteur, type_msg, contenu, contact_client, nocde) " +
                "VALUES (?, 'URGENCE', ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(dbUrl, oracleUser, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, oracleUser);
            ps.setString(2, contenu);
            ps.setString(3, telephone);
            ps.setInt(4, noCde);
            ps.executeUpdate();
            return ResponseEntity.ok("Message d'urgence envoyé.");

        } catch (SQLException e) {
            return switch (e.getErrorCode()) {
                case 1017 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants invalides.");
                case 28000 -> ResponseEntity.status(HttpStatus.LOCKED).body("Compte Oracle verrouillé.");
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur SQL : " + e.getMessage());
            };
        }
    }
}