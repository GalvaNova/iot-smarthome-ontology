package com.smarthome;

import org.apache.jena.rdf.model.*;
import org.apache.jena.ontology.*;
import org.apache.jena.reasoner.*;
import org.apache.jena.util.FileManager;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;

import spark.Spark;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class SmartHomeReasoner {

    private static final String ONTOLOGY_FILE = "ontology/thesis-1.owl"; // sesuaikan path OWL
    private static OntModel baseModel;

    public static void main(String[] args) {
        try {
            // ================================
            // Load base ontology (tanpa data sensor)
            // ================================
            baseModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
            InputStream in = FileManager.get().open(ONTOLOGY_FILE);
            if (in == null) {
                throw new IllegalArgumentException("File ontology tidak ditemukan: " + ONTOLOGY_FILE);
            }
            baseModel.read(in, null);

            System.out.println("✅ Base ontology loaded (without sensor data).");

            // Reset actuator awal
            resetActuators(baseModel);

            // ================================
            // Jalankan Reasoner REST service
            // ================================
            Spark.port(4567);
            Spark.post("/reasoning/cook", (req, res) -> {
                res.type("application/json");
                String body = req.body();

                try {
                    return runReasoning(body);
                } catch (Exception e) {
                    e.printStackTrace();
                    res.status(500);
                    return "{ \"error\": \"" + e.getMessage() + "\" }";
                }
            });

            System.out.println("✅ Reasoner service running at http://localhost:4567");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ======================================
    // Jalankan reasoning berdasarkan RDF input
    // ======================================
    private static String runReasoning(String rdfInput) throws Exception {
        // Buat model sementara untuk sensor data
        Model sensorModel = ModelFactory.createDefaultModel();

        // Deteksi format RDF secara otomatis
        Lang lang = RDFLanguages.contentTypeToLang("text/turtle"); // default Turtle
        if (rdfInput.trim().startsWith("{")) {
            lang = Lang.JSONLD;
        } else if (rdfInput.contains("rdf:RDF")) {
            lang = Lang.RDFXML;
        } else if (rdfInput.contains("@prefix") || rdfInput.contains("^^")) {
            lang = Lang.TURTLE;
        } else if (rdfInput.contains("<") && rdfInput.contains(">")) {
            lang = Lang.NTRIPLES;
        }

        // Parse RDF ke dalam model
        try (InputStream is = new ByteArrayInputStream(rdfInput.getBytes())) {
            RDFDataMgr.read(sensorModel, is, lang);
        }

        // Gabungkan sensor data ke ontology model
        OntModel combined = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        combined.add(baseModel);
        combined.add(sensorModel);

        // Terapkan reasoning (gunakan Jena built-in rules)
        InfModel infModel = ModelFactory.createInfModel(ReasonerRegistry.getOWLReasoner(), combined);

        // ================================
        // Ambil hasil reasoning (contoh: status AC dan cooking activity)
        // ================================
        Map<String, String> result = new HashMap<>();

        String ns = "http://www.semanticweb.org/smarthome#";
        Resource acExhaust = infModel.getResource(ns + "act_AC_Exhaust");
        Property hasActionStatus = infModel.getProperty(ns + "M_hasActionStatus");
        Statement acStatusStmt = acExhaust.getProperty(hasActionStatus);
        if (acStatusStmt != null) {
            result.put("act_AC_Exhaust_action", acStatusStmt.getObject().toString());
        }

        Resource cookAct = infModel.getResource(ns + "fnc_cookAct");
        Property hasActivityStatus = infModel.getProperty(ns + "M_hasActivityStatus");
        Statement cookStmt = cookAct.getProperty(hasActivityStatus);
        if (cookStmt != null) {
            result.put("fnc_cookAct_activity", cookStmt.getObject().toString());
        }

        // ================================
        // Konversi hasil ke JSON string
        // ================================
        StringBuilder json = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> entry : result.entrySet()) {
            if (i > 0) json.append(",");
            json.append("\"").append(entry.getKey()).append("\": \"")
                .append(entry.getValue()).append("\"");
            i++;
        }
        json.append("}");

        return json.toString();
    }

    // ======================================
    // Reset semua actuator ke OFF saat startup
    // ======================================
    private static void resetActuators(OntModel model) {
        String ns = "http://www.semanticweb.org/smarthome#";
        Resource acExhaust = model.getResource(ns + "act_AC_Exhaust");
        Property hasActionStatus = model.getProperty(ns + "M_hasActionStatus");
        if (acExhaust != null && hasActionStatus != null) {
            acExhaust.removeAll(hasActionStatus);
            acExhaust.addProperty(hasActionStatus, ns + "st_actOFF");
        }
        System.out.println("✅ Initial actuator reset: all OFF");
    }
}
