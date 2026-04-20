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
@RequestMapping("/api/livraisons")
@CrossOrigin(origins = "*")
public class LivraisonController {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    // Remplace par le nom exact de ton schéma admin (ex: PROJET_SGBD)
    private final String SCHEMA = "PROJET_SGBD.";

    /**
     * 1. LISTE DES LIVRAISONS (Correction ORA-00942)
     */
    @GetMapping("/ma-tournee")
    public ResponseEntity<?> getMaTournee(@RequestParam String login, @RequestParam String password) {
        List<Map<String, Object>> livraisons = new ArrayList<>();

        // Ajout du préfixe SCHEMA devant chaque table pour que Malek puisse les voir
        // Requête sécurisée :
// 1. Filtre par livreur (Login)
// 2. Filtre par date (Uniquement aujourd'hui)
        String sql = "SELECT lc.nocde, lc.etatliv, lc.dateliv, c.datecde, cl.nomclt, cl.prenomclt, cl.adrclt " +
                "FROM PROJET_SGBD.LivraisonCom lc " +
                "JOIN PROJET_SGBD.Commandes c ON lc.nocde = c.nocde " +
                "JOIN PROJET_SGBD.Clients cl ON c.noclt = cl.noclt " +
                "JOIN PROJET_SGBD.Personnel p ON lc.livreur = p.idpers " +
                "WHERE UPPER(p.Login) = UPPER(?) " +
                "AND TRUNC(lc.dateliv) = TRUNC(SYSDATE)"; // <--- C'est ici la sécurité temporelle// J'ai enlevé le filtre TRUNC(date) pour tes tests

        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("noCde", rs.getInt("nocde"));
                    item.put("etatLiv", rs.getString("etatliv"));
                    item.put("client", rs.getString("prenomclt") + " " + rs.getString("nomclt"));
                    livraisons.add(item);
                }
            }
            return ResponseEntity.ok(livraisons);
        } catch (SQLException e) {
            return ResponseEntity.status(500).body("Erreur SQL : " + e.getMessage());
        }
    }

    /**
     * 2. DÉTAILS COMMANDE (Correction ORA-00904 : prixV -> prix)
     */
    @GetMapping("/details-commande/{noCde}")
    public ResponseEntity<?> getDetails(
            @PathVariable int noCde,
            @RequestParam String login,
            @RequestParam String password) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> articles = new ArrayList<>();
        double montantTotalLivraison = 0.0;

        // Requête qui récupère les infos clients, les articles et calcule le sous-total
        String sql = "SELECT cl.nomclt, cl.prenomclt, cl.telclt, cl.adrclt, cl.villeclt, " +
                "       a.designation, l.qtecde, a.prix, (l.qtecde * a.prix) as sous_total " +
                "FROM PROJET_SGBD.ligcdes l " +
                "JOIN PROJET_SGBD.articles a ON l.refart = a.refart " +
                "JOIN PROJET_SGBD.commandes c ON l.nocde = c.nocde " +
                "JOIN PROJET_SGBD.clients cl ON c.noclt = cl.noclt " +
                "WHERE l.nocde = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, noCde);

            try (ResultSet rs = ps.executeQuery()) {
                boolean clientInfosSet = false;
                while (rs.next()) {
                    if (!clientInfosSet) {
                        response.put("nomClient", rs.getString("nomclt") + " " + rs.getString("prenomclt"));
                        response.put("telephone", rs.getString("telclt"));
                        response.put("adresseComplete", rs.getString("adrclt") + ", " + rs.getString("villeclt"));
                        clientInfosSet = true;
                    }

                    // On ne met QUE la quantité ici, on retire la désignation
                    Map<String, Object> art = new HashMap<>();
                    art.put("quantite", rs.getInt("qtecde"));
                    articles.add(art);

                    montantTotalLivraison += rs.getDouble("sous_total");
                }
            }

            if (articles.isEmpty()) {
                return ResponseEntity.status(404).body("Commande non trouvée.");
            }

            response.put("articles", articles);
            response.put("montantTotalARecuperer", montantTotalLivraison);

            return ResponseEntity.ok(response);

        } catch (SQLException e) {
            return ResponseEntity.status(500).body("Erreur SQL : " + e.getMessage());
        }
    }

    /**
     * 3. MODIFIER ÉTAT (Correction 404 Commande non trouvée)
     */
    @PutMapping("/modifier-etat")
    public ResponseEntity<?> updateEtat(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam int noCde,
            @RequestParam String nouvelEtat) {

        String sql = "UPDATE " + SCHEMA + "LivraisonCom SET etatliv = ? WHERE nocde = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl, login, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nouvelEtat);
            ps.setInt(2, noCde);

            int rows = ps.executeUpdate();
            // Si rows == 0, c'est que le noCde n'existe pas dans la table LivraisonCom
            return rows > 0 ? ResponseEntity.ok("Statut mis à jour !") : ResponseEntity.status(404).body("Commande " + noCde + " non trouvée dans LivraisonCom.");

        } catch (SQLException e) {
            return ResponseEntity.status(403).body("Erreur Oracle : " + e.getMessage());
        }
    }
}