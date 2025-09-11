// routes/areaCook.js
require("dotenv").config();
const express = require("express");
const axios = require("axios");
const router = express.Router();

// === Ambil konfigurasi dari .env ===
const FUSEKI_HOST = process.env.FUSEKI_HOST || "localhost";
const FUSEKI_PORT = process.env.FUSEKI_PORT || "3030";
const FUSEKI_DATASET = process.env.FUSEKI_DATASET || "jarvis";

const FUSEKI_UPDATE =
  process.env.FUSEKI_UPDATE_URL ||
  `http://${FUSEKI_HOST}:${FUSEKI_PORT}/${FUSEKI_DATASET}/update`;

const FUSEKI_QUERY =
  process.env.FUSEKI_QUERY_URL ||
  `http://${FUSEKI_HOST}:${FUSEKI_PORT}/${FUSEKI_DATASET}/query`;

const REASONER_HOST = process.env.REASONER_HOST || "localhost";
const REASONER_PORT = process.env.REASONER_PORT || "4567";
const REASONER_URL = `http://${REASONER_HOST}:${REASONER_PORT}/reasoning/cook`;

// Debug log
console.log("🔗 Config areaCook.js:");
console.log("   FUSEKI_UPDATE:", FUSEKI_UPDATE);
console.log("   FUSEKI_QUERY :", FUSEKI_QUERY);
console.log("   REASONER_URL :", REASONER_URL);

// ==================== POST Sensor Data ====================
let lastUpdateCook = 0;

router.post("/sensorCook", async (req, res) => {
  let { temp, flame, jarak, ppm } = req.body;
  if ([temp, flame, jarak, ppm].some((v) => v === undefined)) {
    return res.status(400).json({ error: "Missing sensor data" });
  }

  lastUpdateCook = Date.now();

  // SPARQL update ke Fuseki
  const updateQuery = `
    PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
    PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
    DELETE {
      :read_AC_Temp :ACdp_hasTEMPvalue ?t .
      :read_AC_Flame :ACdp_hasFIREvalue ?f .
      :read_AC_Dist :ACdp_hasDISTvalue ?j .
      :read_AC_Ppm :ACdp_hasPPMvalue ?p .
    }
    INSERT {
      :read_AC_Temp :ACdp_hasTEMPvalue "${temp}"^^xsd:decimal .
      :read_AC_Flame :ACdp_hasFIREvalue "${flame}"^^xsd:decimal .
      :read_AC_Dist :ACdp_hasDISTvalue "${jarak}"^^xsd:decimal .
      :read_AC_Ppm :ACdp_hasPPMvalue "${ppm}"^^xsd:decimal .
    }
    WHERE {
      OPTIONAL { :read_AC_Temp :ACdp_hasTEMPvalue ?t }
      OPTIONAL { :read_AC_Flame :ACdp_hasFIREvalue ?f }
      OPTIONAL { :read_AC_Dist :ACdp_hasDISTvalue ?j }
      OPTIONAL { :read_AC_Ppm :ACdp_hasPPMvalue ?p }
    }
  `;

  try {
    // Simpan sensor data ke Fuseki
    await axios.post(
      FUSEKI_UPDATE,
      `update=${encodeURIComponent(updateQuery)}`,
      { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
    );

    // Trigger reasoning Java Reasoner
    await axios.get(REASONER_URL);

    res.json({ message: "Sensor data stored & reasoning triggered" });
  } catch (err) {
    console.error("❌ Error in /sensorCook:", err.message);
    res.status(500).json({ error: "Failed to update or trigger reasoning" });
  }
});

// ==================== GET Status Actuator ====================
router.get("/statusCook", async (req, res) => {
  const query = `
    PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
    SELECT ?fan ?buzzer ?act ?timer WHERE {
      OPTIONAL { :act_AC_Exhaust :M_hasActionStatus ?fan }
      OPTIONAL { :act_AC_Buzzer  :M_hasActionStatus ?buzzer }
      OPTIONAL { :fnc_cookAct :M_hasActivityStatus ?act }
      OPTIONAL { :fnc_timing :ACop_hasTimerStatus ?timer }
    }
    LIMIT 1
  `;

  try {
    const r = await axios.get(FUSEKI_QUERY, {
      params: { query },
      headers: { Accept: "application/sparql-results+json" },
    });

    console.log("🔎 /statusCook Fuseki:", JSON.stringify(r.data, null, 2));

    const b = r.data.results.bindings[0] || {};

    const fan = b.fan?.value.endsWith("st_actON") ? "ON" : "OFF";
    const buzzer = b.buzzer?.value.endsWith("st_actON") ? "ON" : "OFF";
    const activity = b.act?.value?.split("#").pop() ?? "UNKNOWN";
    const timer = b.timer?.value?.split("#").pop() ?? "UNKNOWN";

    res.json({ FAN: fan, BUZZER: buzzer, ACTIVITY: activity, TIMER: timer });
  } catch (err) {
    console.error("❌ statusCook error:", err.message);
    res.status(500).json({ error: true });
  }
});

// ==================== Helper: getCookStatus ====================
async function getCookStatus() {
  const query = `
    PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
    SELECT ?fanStatus ?buzzStatus WHERE {
      OPTIONAL { :act_AC_Exhaust :M_hasActionStatus ?fanStatus. }
      OPTIONAL { :act_AC_Buzzer :M_hasActionStatus ?buzzStatus. }
    }
  `;

  try {
    const res = await axios.post(
      FUSEKI_QUERY,
      `query=${encodeURIComponent(query)}`,
      {
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
      }
    );

    console.log("🔎 Fuseki response:", JSON.stringify(res.data, null, 2));

    let fan = "OFF";
    let buzzer = "OFF";

    const bindings = res.data.results.bindings[0];
    if (bindings?.fanStatus?.value.endsWith("st_actON")) fan = "ON";
    if (bindings?.buzzStatus?.value.endsWith("st_actON")) buzzer = "ON";

    return { fan, buzzer };
  } catch (err) {
    console.error("❌ statusCook error:", err.message);
    return { fan: "OFF", buzzer: "OFF" };
  }
}

// ==================== GET Latest Sensor Update ====================
router.get("/latest", (req, res) => {
  res.json({ lastUpdate: lastUpdateCook });
});

module.exports = router;
