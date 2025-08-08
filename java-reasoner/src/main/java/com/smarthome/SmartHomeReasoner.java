package com.smarthome;

import static spark.Spark.*;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Optional;

public class SmartHomeReasoner {

    private static final String ONTO_NS = "http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#";
    private static final String OWL_FILE_PATH = "ontology/thesis-1.owl";
    private static final String FUSEKI_UPDATE_URL = "http://localhost:3030/project-1/update";

    public static void main(String[] args) {
        port(4567);

        // GET endpoint
        get("/reasoning/cook", (req, res) -> {
            res.type("application/json");
            return runReasoning("act_AC_Buzzer", "act_AC_Exhaust", "fnc_cookAct", "fnc_timing");
        });

        // POST endpoint untuk data sensor
        post("/reasoning/cook", (req, res) -> {
            JsonObject body = JsonParser.parseString(req.body()).getAsJsonObject();
            float temp = body.get("temp").getAsFloat();
            float flame = body.get("flame").getAsFloat();
            float jarak = body.get("jarak").getAsFloat();
            float ppm = body.get("ppm").getAsFloat();

            res.type("application/json");
            return runReasoningWithSensor(temp, flame, jarak, ppm,
                    "act_AC_Buzzer", "act_AC_Exhaust", "fnc_cookAct", "fnc_timing");
        });

        System.out.println("✅ Reasoner service running at http://localhost:4567");
    }

    private static String runReasoningWithSensor(float temp, float flame, float jarak, float ppm, String... individuals) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(OWL_FILE_PATH));
        OWLDataFactory df = manager.getOWLDataFactory();

        // Tambahkan data sensor sebagai assertion
        addSensorData(manager, ontology, df, "read_AC_Temp", "ACdp_hasTEMPvalue", temp);
        addSensorData(manager, ontology, df, "read_AC_Flame", "ACdp_hasFIREvalue", flame);
        addSensorData(manager, ontology, df, "read_AC_Dist", "ACdp_hasDISTvalue", jarak);
        addSensorData(manager, ontology, df, "read_AC_Ppm", "ACdp_hasPPMvalue", ppm);

        return runReasoning(manager, ontology, df, individuals);
    }

    private static void addSensorData(OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory df,
                                       String individual, String property, float value) {
        OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(ONTO_NS + individual));
        OWLDataProperty prop = df.getOWLDataProperty(IRI.create(ONTO_NS + property));
        OWLLiteral val = df.getOWLLiteral(value);
        OWLAxiom axiom = df.getOWLDataPropertyAssertionAxiom(prop, ind, val);
        manager.addAxiom(ontology, axiom);
    }

    private static String runReasoning(String... individuals) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(OWL_FILE_PATH));
        OWLDataFactory df = manager.getOWLDataFactory();
        return runReasoning(manager, ontology, df, individuals);
    }

    private static String runReasoning(OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory df,
                                       String... individuals) throws Exception {
        OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        reasoner.precomputeInferences();

        OWLObjectProperty actionProp = df.getOWLObjectProperty(IRI.create(ONTO_NS + "M_ActionStatus"));
        OWLObjectProperty activityProp = df.getOWLObjectProperty(IRI.create(ONTO_NS + "M_ActivityStatus"));
        OWLObjectProperty timerProp = df.getOWLObjectProperty(IRI.create(ONTO_NS + "ACop_hasTimerStatus"));

        JsonObject result = new JsonObject();

        for (String indName : individuals) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(ONTO_NS + indName));

            getFirstValue(reasoner, ind, actionProp).ifPresent(val -> {
                result.addProperty(indName + "_action", val);
                updateFuseki(indName, "M_ActionStatus", val);
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
}
