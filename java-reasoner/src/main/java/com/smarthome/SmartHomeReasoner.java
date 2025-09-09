package com.smarthome;

import static spark.Spark.*;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import io.github.cdimascio.dotenv.Dotenv;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Optional;

public class SmartHomeReasoner {

    public static void main(String[] args) {
        // Load konfigurasi dari .env
        Dotenv dotenv = Dotenv.load();
        int portNumber = Integer.parseInt(dotenv.get("PORT", "4567"));
        String owlFilePath = dotenv.get("ONTOLOGY_PATH", "ontology/thesis-1.owl");
        String ontologyNS = dotenv.get("ONTOLOGY_NS", "http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#");
        String fusekiUpdateUrl = dotenv.get("FUSEKI_UPDATE_URL", "http://localhost:3030/project-1/update");

        port(portNumber);

        // Reasoning untuk areaCook
        get("/reasoning/cook", (req, res) -> {
            res.type("application/json");
            return runReasoning(ontologyNS, owlFilePath, fusekiUpdateUrl,
                    "act_AC_Buzzer", "act_AC_Exhaust", "fnc_cookAct", "fnc_timing");
        });

        // Reasoning untuk areaWash
        get("/reasoning/wash", (req, res) -> {
            res.type("application/json");
            return runReasoning(ontologyNS, owlFilePath, fusekiUpdateUrl, "act_AS_Valve");
        });

        // Reasoning untuk areaInout
        get("/reasoning/inout", (req, res) -> {
            res.type("application/json");
            return runReasoning(ontologyNS, owlFilePath, fusekiUpdateUrl, "act_AE_Lamp");
        });

        System.out.println("✅ Reasoner service running at http://localhost:" + portNumber);
    }

    /**
     * Jalankan reasoning untuk beberapa individual
     */
    private static String runReasoning(String ontologyNS, String owlFilePath, String fusekiUpdateUrl,
                                       String... individuals) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(owlFilePath));
        OWLDataFactory df = manager.getOWLDataFactory();

        OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        reasoner.precomputeInferences();

        OWLObjectProperty actionProp = df.getOWLObjectProperty(IRI.create(ontologyNS + "M_hasActionStatus"));
        OWLObjectProperty activityProp = df.getOWLObjectProperty(IRI.create(ontologyNS + "M_ActivityStatus"));
        OWLObjectProperty timerProp = df.getOWLObjectProperty(IRI.create(ontologyNS + "ACop_hasTimerStatus"));

        JsonObject result = new JsonObject();

        for (String indName : individuals) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(ontologyNS + indName));

            // Cek action
            getFirstValue(reasoner, ind, actionProp).ifPresent(val -> {
                result.addProperty(indName + "_action", val);
                updateFuseki(fusekiUpdateUrl, ontologyNS, indName, "M_hasActionStatus", val);
            });

            // Cek activity
            getFirstValue(reasoner, ind, activityProp).ifPresent(val -> {
                result.addProperty(indName + "_activity", val);
                updateFuseki(fusekiUpdateUrl, ontologyNS, indName, "M_ActivityStatus", val);
            });

            // Cek timer
            getFirstValue(reasoner, ind, timerProp).ifPresent(val -> {
                result.addProperty(indName + "_timer", val);
                updateFuseki(fusekiUpdateUrl, ontologyNS, indName, "ACop_hasTimerStatus", val);
            });
        }

        return result.toString();
    }

    /**
     * Ambil nilai pertama dari object property
     */
    private static Optional<String> getFirstValue(OpenlletReasoner reasoner, OWLNamedIndividual ind,
                                                  OWLObjectProperty prop) {
        return reasoner.getObjectPropertyValues(ind, prop)
                .entities()
                .findFirst()
                .map(val -> val.getIRI().getShortForm());
    }

    /**
     * Update hasil reasoning ke Fuseki
     */
    private static void updateFuseki(String fusekiUpdateUrl, String ontologyNS, String subject,
                                     String predicate, String inferredValue) {
        try {
            String update = String.format("""
                PREFIX : <%s>
                DELETE { :%s :%s ?s }
                INSERT { :%s :%s :%s }
                WHERE { OPTIONAL { :%s :%s ?s } }
            """, ontologyNS, subject, predicate, subject, predicate, inferredValue, subject, predicate);

            HttpURLConnection conn = (HttpURLConnection) new URL(fusekiUpdateUrl).openConnection();
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
