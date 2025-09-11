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

    private static final Dotenv dotenv = Dotenv.load();

    private static final String ONTO_NS = "http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#";
    private static final String OWL_FILE_PATH = dotenv.get("OWL_FILE", "ontology/thesis-1.owl");

    // Fuseki config dari .env
    private static final String FUSEKI_BASE = dotenv.get("FUSEKI_BASE_URL", "http://localhost:3030");
    private static final String FUSEKI_DATASET = dotenv.get("FUSEKI_DATASET", "jarvis");
    private static final String FUSEKI_UPDATE_URL = FUSEKI_BASE + "/" + FUSEKI_DATASET + "/update";

    public static void main(String[] args) {
        port(Integer.parseInt(dotenv.get("REASONER_PORT", "4567")));

        // Endpoint reasoning
        get("/reasoning/cook", (req, res) -> {
            res.type("application/json");
            resetActuatorState(); // ✅ reset sebelum reasoning
            return runReasoning("act_AC_Buzzer", "act_AC_Exhaust", "fnc_cookAct", "fnc_timing");
        });

        get("/reasoning/wash", (req, res) -> {
            res.type("application/json");
            resetActuatorState(); // opsional, bisa dipisah per-area
            return runReasoning("act_AS_Valve");
        });

        get("/reasoning/inout", (req, res) -> {
            res.type("application/json");
            resetActuatorState();
            return runReasoning("act_AE_Lamp");
        });

        System.out.println("✅ Reasoner service running at http://localhost:" + dotenv.get("REASONER_PORT", "4567"));
    }

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

            getFirstValue(reasoner, ind, actionProp).ifPresent(val -> {
                result.addProperty(indName + "_action", val);
                updateFuseki(indName, "M_hasActionStatus", val);
            });

            getFirstValue(reasoner, ind, activityProp).ifPresent(val -> {
                result.addProperty(indName + "_activity", val);
                updateFuseki(indName, "M_ActivityStatus", val);
            });

            getFirstValue(reasoner, ind, timerProp).ifPresent(val -> {
                result.addProperty(indName + "_timer", val);
                updateFuseki(indName, "ACop_hasTimerStatus", val);
            });
        }

        return result.toString();
    }

    private static Optional<String> getFirstValue(OpenlletReasoner reasoner, OWLNamedIndividual ind, OWLObjectProperty prop) {
        return reasoner.getObjectPropertyValues(ind, prop)
                .entities()
                .findFirst()
                .map(val -> val.getIRI().getShortForm());
    }

    private static void updateFuseki(String subject, String predicate, String inferredValue) {
        try {
            String update = String.format("""
                PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
                DELETE { :%s :%s ?s }
                INSERT { :%s :%s :%s }
                WHERE { OPTIONAL { :%s :%s ?s } }
            """, subject, predicate, subject, predicate, inferredValue, subject, predicate);

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

    // === Tambahan: reset actuator state sebelum reasoning ===
    private static void resetActuatorState() {
        try {
            String update = """
                PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
                DELETE {
                  :act_AC_Exhaust :M_hasActionStatus ?s .
                  :act_AC_Buzzer :M_hasActionStatus ?s .
                  :act_AS_Valve :M_hasActionStatus ?s .
                  :act_AE_Lamp :M_hasActionStatus ?s .
                }
                WHERE {
                  { :act_AC_Exhaust :M_hasActionStatus ?s } UNION
                  { :act_AC_Buzzer :M_hasActionStatus ?s } UNION
                  { :act_AS_Valve :M_hasActionStatus ?s } UNION
                  { :act_AE_Lamp :M_hasActionStatus ?s }
                }
            """;

            HttpURLConnection conn = (HttpURLConnection) new URL(FUSEKI_UPDATE_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(("update=" + URLEncoder.encode(update, "UTF-8")).getBytes());
            }

            if (conn.getResponseCode() == 200) {
                System.out.println("✅ Reset actuator states (all areas)");
            } else {
                System.err.println("❌ Reset actuator failed: HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Error resetting actuators: " + e.getMessage());
        }
    }
}
