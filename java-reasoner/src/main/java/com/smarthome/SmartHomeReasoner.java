package com.smarthome;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.FileManager;
import org.json.JSONObject;
import openllet.jena.PelletReasonerFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.rdf.model.InfModel;

import static spark.Spark.*;

public class SmartHomeReasoner {

    private static final String ONTOLOGY_PATH = "ontology/thesis-1.owl";
    private static final String NS = "http://www.semanticweb.org/ontologies/2024/smarthome#";

    // Simpan model global biar bisa dipakai juga di /debug/triples
    private static OntModel baseModel;
    private static InfModel infModel;

    public static void main(String[] args) {
        System.out.println("🚀 Java Reasoner service starting...");

        // Load ontologi
        baseModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        FileManager.get().readModel(baseModel, ONTOLOGY_PATH);

        // Setup reasoner Pellet
        Reasoner reasoner = PelletReasonerFactory.theInstance().create();
        infModel = ModelFactory.createInfModel(reasoner, baseModel);

        // ========== Route utama untuk reasoning ==========
        post("/reasoning/areaWash", (req, res) -> {
            res.type("application/json");
            try {
                JSONObject json = new JSONObject(req.body());
                double jarak1 = json.optDouble("jarak1", -1);
                double jarak2 = json.optDouble("jarak2", -1);

                // lakukan reasoning sederhana
                String status = getActivityStatus(jarak1, jarak2);

                JSONObject response = new JSONObject();
                response.put("message", "Wash data stored");
                response.put("status", status);
                return response.toString();

            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return new JSONObject().put("error", "Reasoner failed").toString();
            }
        });

        // ========== Route debug untuk lihat triple ==========
        get("/debug/triples", (req, res) -> {
            res.type("text/plain");
            StringBuilder sb = new StringBuilder();
            StmtIterator iter = infModel.listStatements();
            while (iter.hasNext()) {
                Statement stmt = iter.nextStatement();
                sb.append(stmt.toString()).append("\n");
            }
            return sb.toString();
        });

        System.out.println("✅ Reasoner service running at http://localhost:4567/reasoning/areaWash");
    }

    /**
     * Fungsi sederhana untuk cek status activity berdasarkan input sensor.
     * Misalnya kalau ada objek < 10cm dan ada orang < 50cm → aktif
     */
    private static String getActivityStatus(double jarak1, double jarak2) {
        if (jarak1 >= 0 && jarak1 < 10 && jarak2 >= 0 && jarak2 < 50) {
            return "st_actON";  // contoh rule aktif
        } else {
            return "unknown";  // default kalau tidak cocok
        }
    }
}
