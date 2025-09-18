const express = require("express");
const axios = require("axios");
const router = express.Router();

const FUSEKI_UPDATE = "http://localhost:3030/jarvis8/update";
const REASONER_URL = "http://localhost:4567/reasoning/cook";

const PREFIXES = `
  PREFIX ex: <http://www.semanticweb.org/smarthome#>
`;

router.post("/sensorCook", async (req, res) => {
  try {
    const { suhu, api, jarak, ppm } = req.body;

    console.log("📥 Data sensor diterima:", req.body);

    // Simpan ke Fuseki
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

    // Translate JSON → Turtle
    const turtleData = `
      @prefix ex: <http://www.semanticweb.org/smarthome#> .

      ex:sensor1 a ex:Sensor ;
        ex:hasTemperature "${suhu}" ;
        ex:hasSmoke "${api}" ;
        ex:hasDistance "${jarak}" ;
        ex:hasPPM "${ppm}" .
    `;

    // Kirim ke reasoner
    const reasonerResponse = await axios.post(REASONER_URL, turtleData, {
      headers: { "Content-Type": "text/turtle" },
    });

    console.log("🤖 Reasoner result:", reasonerResponse.data);
    res.json(reasonerResponse.data);
  } catch (err) {
    console.error("❌ Error in /sensorCook:", err.message);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
