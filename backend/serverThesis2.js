const express = require("express");
const axios = require("axios");
const bodyParser = require("body-parser");

const app = express();
app.use(bodyParser.json());

// Konfigurasi koneksi
const FUSEKI_UPDATE = "http://localhost:3030/jarvis8/update";
const FUSEKI_QUERY = "http://localhost:3030/jarvis8/query";
const REASONER_URL = "http://localhost:4567/reasoning/cook";

// Prefix ontology
const PREFIXES = `
  PREFIX ex: <http://www.semanticweb.org/smarthome#>
`;

// Endpoint menerima data sensor dari NodeMCU
app.post("/sensorCook", async (req, res) => {
  try {
    const { suhu, api, jarak, ppm } = req.body;

    console.log("📥 Data sensor diterima:", req.body);

    // ================================
    // 1. Simpan data ke Fuseki
    // ================================
    const sparqlUpdate = `
      ${PREFIXES}
      DELETE WHERE { ex:sensor1 ?p ?o } ;
      INSERT DATA {
        ex:sensor1 a ex:Sensor ;
          ex:hasTemperature "${suhu}" ;
          ex:hasSmoke "${api}" ;
          ex:hasDistance "${jarak}" ;
          ex:hasPPM "${ppm}" .
      }
    `;

    await axios.post(FUSEKI_UPDATE, sparqlUpdate, {
      headers: { "Content-Type": "application/sparql-update" },
    });

    console.log("✅ Sensor data updated to Fuseki");

    // ================================
    // 2. Translate JSON → RDF/Turtle
    // ================================
    const turtleData = `
      @prefix ex: <http://www.semanticweb.org/smarthome#> .

      ex:sensor1 a ex:Sensor ;
        ex:hasTemperature "${suhu}" ;
        ex:hasSmoke "${api}" ;
        ex:hasDistance "${jarak}" ;
        ex:hasPPM "${ppm}" .
    `;

    // ================================
    // 3. Kirim RDF/Turtle ke Reasoner
    // ================================
    const reasonerResponse = await axios.post(REASONER_URL, turtleData, {
      headers: { "Content-Type": "text/turtle" },
    });

    console.log("🤖 Reasoner result:", reasonerResponse.data);

    // ================================
    // 4. Kirim balik hasil ke NodeMCU
    // ================================
    res.json(reasonerResponse.data);
  } catch (err) {
    console.error("❌ Error in /sensorCook:", err.message);
    res.status(500).json({ error: err.message });
  }
});

// Jalankan server
app.listen(5000, () => {
  console.log("✅ Backend aktif di http://0.0.0.0:5000");
});
