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
    private static final String FUSEKI_DATASET = dotenv.get("FUSEKI_DATASET", "jarvis3");
    private static final String FUSEKI_UPDATE_URL = FUSEKI_BASE + "/" + FUSEKI_DATASET + "/update";

    // Ontology & Reasoner cache (dibuat sekali saat startup)
    private static OWLOntologyManager manager;
    private static OWLOntology ontology;
    private static OWLDataFactory df;
    private static OpenlletReasoner reasoner;

    static {
        try {
            manager = OWLManager.createOWLOntologyManager();
            ontology = manager.loadOntologyFromOntologyDocument(new File(OWL_FILE_PATH));
            df = manager.getOWLDataFactory();

            reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
            reasoner.precomputeInferences();

            System.out.println("✅ Ontology loaded & reasoner initialized.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Failed to init ontology/reasoner");
        }
    }

    public static void main(String[] args) {
        port(Integer.parseInt(dotenv.get("REASONER_PORT", "4567")));

        // Endpoint reasoning
        get("/reasoning/cook", (req, res) -> {
            res.type("application/json");
            resetActuatorState(); // reset sebelum reasoning
            return runReasoning("act_AC_Buzzer", "act_AC_Exhaust", "fnc_cookAct", "fnc_timing");
        });

        get("/reasoning/wash", (req, res) -> {
            res.type("application/json");
            resetActuatorState();
            return runReasoning("act_AS_Valve");
        });

        get("/reasoning/inout", (req, res) -> {
            res.type("application/json");
            resetActuatorState();
            return runReasoning("act_AE_Lamp");
        });

        System.out.println("✅ Reasoner service running at http://localhost:" +
                dotenv.get("REASONER_PORT", "4567"));
    }

    private static String runReasoning(String... individuals) throws Exception {
        JsonObject result = new JsonObject();

        // Properti yang dipakai untuk reasoning
        OWLObjectProperty actionProp =
                df.getOWLObjectProperty(IRI.create(ONTO_NS + "M_hasActionStatus"));
        OWLObjectProperty activityProp =
                df.getOWLObjectProperty(IRI.create(ONTO_NS + "M_hasActivityStatus")); // ✅ perbaikan typo
        OWLObjectProperty timerProp =
                df.getOWLObjectProperty(IRI.create(ONTO_NS + "ACop_hasTimerStatus"));

        for (String indName : individuals) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(ONTO_NS + indName));

            // Action status (ON/OFF)
            getFirstValue(reasoner, ind, actionProp).ifPresent(val -> {
                result.addProperty(indName + "_action", val);
                updateFuseki(indName, "M_hasActionStatus", val, true);
            });

            // Activity status (fungsi sedang berjalan/tidak)
            getFirstValue(reasoner, ind, activityProp).ifPresent(val -> {
                result.addProperty(indName + "_activity", val);
                updateFuseki(indName, "M_hasActivityStatus", val, true);
            });

            // Timer status (literal/individual tergantung ontology)
            getFirstValue(reasoner, ind, timerProp).ifPresent(val -> {
                result.addProperty(indName + "_timer", val);
                updateFuseki(indName, "ACop_hasTimerStatus", val, true);
            });
        }

        return result.toString();
    }

    private static Optional<String> getFirstValue(OpenlletReasoner reasoner,
                                                 OWLNamedIndividual ind,
                                                 OWLObjectProperty prop) {
        return reasoner.getObjectPropertyValues(ind, prop)
                .entities()
                .findFirst()
                .map(val -> val.getIRI().getShortForm());
    }

    /**
     * Update Fuseki dengan hasil reasoning.
     *
     * @param subject       nama individual subjek
     * @param predicate     nama property
     * @param inferredValue hasil reasoning (biasanya nama individual, kadang literal)
     * @param treatAsIndividual true jika value adalah individual (default untuk status ON/OFF)
     */
    private static void updateFuseki(String subject,
                                     String predicate,
                                     String inferredValue,
                                     boolean treatAsIndividual) {
        try {
            String objectPart;

            if (treatAsIndividual) {
                objectPart = ":" + inferredValue;
            } else {
                objectPart = "\"" + inferredValue + "\"";
            }

            String update = String.format("""
                PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
                PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                DELETE { :%s :%s ?s }
                INSERT { :%s :%s %s }
                WHERE { OPTIONAL { :%s :%s ?s } }
            """, subject, predicate, subject, predicate, objectPart, subject, predicate);

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

    // === Reset actuator state sebelum reasoning (default OFF) ===
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
                INSERT {
                  :act_AC_Exhaust :M_hasActionStatus :st_actOFF .
                  :act_AC_Buzzer  :M_hasActionStatus :st_actOFF .
                  :act_AS_Valve   :M_hasActionStatus :st_actOFF .
                  :act_AE_Lamp    :M_hasActionStatus :st_actOFF .
                }
                WHERE {
                  OPTIONAL { :act_AC_Exhaust :M_hasActionStatus ?s }
                  OPTIONAL { :act_AC_Buzzer  :M_hasActionStatus ?s }
                  OPTIONAL { :act_AS_Valve   :M_hasActionStatus ?s }
                  OPTIONAL { :act_AE_Lamp    :M_hasActionStatus ?s }
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
                System.out.println("✅ Reset actuator states: all OFF by default");
            } else {
                System.err.println("❌ Reset actuator failed: HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Error resetting actuators: " + e.getMessage());
        }
    }
}
