package com.smarthome;

import static spark.Spark.*;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;

import com.google.gson.JsonObject;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Optional;

public class SmartHomeReasoner {

    // private static final Dotenv dotenv = Dotenv.load();
    private static final Dotenv dotenv = Dotenv.configure()
    .directory("../")   // cari .env di parent folder
    .ignoreIfMissing()
    .load();

    // Konfigurasi dari .env
    private static final String ONTO_NS = dotenv.get("ONTO_NS", "http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#");

    private static final String OWL_FILE_PATH = dotenv.get("OWL_FILE_PATH", "ontology/thesis-1.owl");

    
    private static final String FUSEKI_UPDATE_URL = dotenv.get("FUSEKI_UPDATE_URL", "http://localhost:3030/project-1/update");
    private static final int SERVER_PORT = Integer.parseInt(dotenv.get("REASONER_PORT", "4567"));

    public static void main(String[] args) {
        port(SERVER_PORT);

        // Endpoint reasoning areaCook
        get("/reasoning/cook", (req, res) -> {
            res.type("application/json");
            try {
                return runReasoning("act_AC_Buzzer", "act_AC_Exhaust", "fnc_cookAct", "fnc_timing");
            } catch (Exception e) {
                res.status(500);
                return "{\"error\": \"Reasoning cook gagal: " + e.getMessage() + "\"}";
            }
        });

        // Endpoint reasoning areaWash
        get("/reasoning/wash", (req, res) -> {
            res.type("application/json");
            try {
                return runReasoning("act_AS_Valve");
            } catch (Exception e) {
                res.status(500);
                return "{\"error\": \"Reasoning wash gagal: " + e.getMessage() + "\"}";
            }
        });

        // Endpoint reasoning areaInout
        get("/reasoning/inout", (req, res) -> {
            res.type("application/json");
            try {
                return runReasoning("act_AE_Lamp");
            } catch (Exception e) {
                res.status(500);
                return "{\"error\": \"Reasoning inout gagal: " + e.getMessage() + "\"}";
            }
        });

        System.out.println("✅ Reasoner service running at http://localhost:" + SERVER_PORT);
    }

    /**
     * Jalankan reasoning untuk beberapa individual
     */
    private static String runReasoning(String... individuals) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(OWL_FILE_PATH));
        OWLDataFactory df = manager.getOWLDataFactory();

        OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        reasoner.precomputeInferences();

        OWLObjectProperty actionProp = df.getOWLObjectProperty(IRI.create(ONTO_NS + "M_hasActionStatus"));
        OWLObjectProperty activityProp = df.getOWLObjectProperty(IRI.create(ONTO_NS + "M_ActivityStatus"));
        OWLObjectProperty timerProp = df.getOWLObjectProperty(IRI.create(ONTO_NS + "ACop_hasTimerStatus"));

        JsonObject result = new JsonObject();

        for (String indName : individuals) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(ONTO_NS + indName));

            // Cek action
            getFirstValue(reasoner, ind, actionProp).ifPresent(val -> {
                result.addProperty(indName + "_action", val);
                updateFuseki(indName, "M_hasActionStatus", val);
            });

            // Cek activity
            getFirstValue(reasoner, ind, activityProp).ifPresent(val -> {
                result.addProperty(indName + "_activity", val);
                updateFuseki(indName, "M_ActivityStatus", val);
            });

            // Cek timer
            getFirstValue(reasoner, ind, timerProp).ifPresent(val -> {
                result.addProperty(indName + "_timer", val);
                updateFuseki(indName, "ACop_hasTimerStatus", val);
            });
        }

        return result.toString();
    }

    /**
     * Ambil nilai pertama dari object property
     */
    private static Optional<String> getFirstValue(OpenlletReasoner reasoner, OWLNamedIndividual ind, OWLObjectProperty prop) {
        return reasoner.getObjectPropertyValues(ind, prop)
                .entities()
                .findFirst()
                .map(val -> val.getIRI().getShortForm());
    }

    /**
     * Update hasil reasoning ke Fuseki
     */
    private static void updateFuseki(String subject, String predicate, String inferredValue) {
        try {
            String update = String.format("""
                PREFIX : <%s>
                DELETE { :%s :%s ?s }
                INSERT { :%s :%s :%s }
                WHERE { OPTIONAL { :%s :%s ?s } }
            """, ONTO_NS, subject, predicate, subject, predicate, inferredValue, subject, predicate);

            HttpURLConnection conn = (HttpURLConnection) new URL(FUSEKI_UPDATE_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(("update=" + URLEncoder.encode(update, "UTF-8")).getBytes());
            }

            if (conn.getResponseCode() == 200) {
                System.out.println("✅ Updated: " + subject + " " + predicate + " → " + inferredValue);
            } else {
                System.err.println("❌ Fuseki update failed: HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Error updating Fuseki: " + e.getMessage());
        }
    }
}
