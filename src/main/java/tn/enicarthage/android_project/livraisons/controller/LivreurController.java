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
@RequestMapping("/api/livreur")
@CrossOrigin(origins = "*")
public class LivreurController {

    @Value("${spring.datasource.url}")
    private String dbUrl;
    private final String SCHEMA = "PROJET_SGBD.";

    // 1. MA TOURNÉE
    @GetMapping("/ma-tournee")
    public ResponseEntity<?> getMaTournee(@RequestParam String login, @RequestParam String password) {
        List<Map<String, Object>> tournee = new ArrayList<>();

        String sql = """
SELECT
    lc.nocde,
    lc.etatliv,
    lc.modepay,
    cl.nomclt,
    cl.prenomclt,
    cl.adrclt,
    cl.villeclt,
    cl.telclt,
    NVL((
        SELECT SUM(l.qtecde * a.prix)
        FROM PROJET_SGBD.LigCdes l
        JOIN PROJET_SGBD.Articles a ON a.refart = l.refart
        WHERE l.nocde = lc.nocde
    ), 0) AS montantTotal,
    NVL((
        SELECT SUM(l2.qtecde)   -- Quantité TOTALE d'articles
        FROM PROJET_SGBD.LigCdes l2
        WHERE l2.nocde = lc.nocde
    ), 0) AS nbArticles
FROM PROJET_SGBD.LivraisonCom lc
JOIN PROJET_SGBD.Personnel p ON lc.livreur = p.idpers
JOIN PROJET_SGBD.Commandes c ON lc.nocde = c.nocde
JOIN PROJET_SGBD.Clients cl ON c.noclt = cl.noclt
WHERE UPPER(p.Login) = UPPER(?)
  AND TRUNC(lc.dateliv) = TRUNC(SYSDATE)
ORDER BY lc.nocde ASC
""";

        try (Connection conn = DriverManager.getConnection(dbUrl, login.toUpperCase(), password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, login);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> livraison = new HashMap<>();
                    livraison.put("id", rs.getInt("nocde"));
                    livraison.put("etat", rs.getString("etatliv"));
                    livraison.put("nomClient", rs.getString("nomclt") + " " + rs.getString("prenomclt"));
                    livraison.put("adresse", rs.getString("adrclt") + ", " + rs.getString("villeclt"));
                    livraison.put("telephone", rs.getString("telclt"));
                    livraison.put("modePayment", rs.getString("modepay"));
                    livraison.put("montantTotal", rs.getDouble("montantTotal"));
                    livraison.put("nbArticles", rs.getInt("nbArticles")); // 👈 désormais la quantité totale
                    tournee.add(livraison);
                }
            }
            return ResponseEntity.ok(tournee);
        } catch (SQLException e) {
            System.err.println("Erreur SQL: " + e.getMessage());
            return ResponseEntity.status(500).body("Erreur SQL : " + e.getMessage());
        }
    }

    @PutMapping("/modifier-etat")
    public ResponseEntity<?> updateEtat(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam int noCde,
            @RequestParam String nouvelEtat,
            @RequestParam(required = false) String remarque) {

        // ✅ Log pour voir ce qui arrive
        System.out.println(">>> modifier-etat appelé : login=" + login
                + " noCde=" + noCde + " etat=" + nouvelEtat);

        String sql = "UPDATE PROJET_SGBD.LivraisonCom SET etatliv = ? WHERE nocde = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nouvelEtat);
            ps.setInt(2, noCde);
            int rows = ps.executeUpdate();
            System.out.println(">>> Rows updated: " + rows);
            return rows > 0
                    ? ResponseEntity.ok("Statut mis à jour !")
                    : ResponseEntity.status(404).body("Commande non trouvée.");
        } catch (SQLException e) {
            System.out.println(">>> SQL ERROR: " + e.getMessage());
            return ResponseEntity.status(500).body("Erreur SQL : " + e.getMessage());
        }
    }
    // 4. MESSAGE D'URGENCE
    @PostMapping("/urgence")
    public ResponseEntity<?> sendUrgence(@RequestParam String login, @RequestParam String password,
                                         @RequestParam int noCde, @RequestParam String telephone, @RequestParam String contenu) {
        String sql = "INSERT INTO " + SCHEMA + "Messages (expediteur, type_msg, contenu, contact_client, nocde) VALUES (?, 'URGENCE', ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, contenu);
            ps.setString(3, telephone);
            ps.setInt(4, noCde);
            ps.executeUpdate();
            return ResponseEntity.ok("Message d'urgence envoyé au contrôleur.");
        } catch (SQLException e) {
            return ResponseEntity.status(500).body("Erreur SQL : " + e.getMessage());
        }
    }
}