// routes/areaWash.js
const express = require("express");
const axios = require("axios");
const router = express.Router();

const FUSEKI_UPDATE = "http://192.168.43.238:3030/project-1/update";
const FUSEKI_QUERY = "http://192.168.43.238:3030/project-1/query";
const REASONER_URL = "http://localhost:4567/reasoning/wash";

// POST /api/data-ultrasonic
router.post("/data-ultrasonic", async (req, res) => {
  const { distance1, distance2 } = req.body;

  if ([distance1, distance2].some((v) => v === undefined)) {
    return res.status(400).json({ error: "Missing distance values" });
  }

  const status = distance1 < 15 && distance2 < 25 ? "ON" : "OFF";

  const updateQuery = `
    PREFIX tb: <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
    PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
    DELETE {
      tb:act_AS_Valve tb:M_hasActionStatus ?old .
      tb:param_objek tb:ASdp_hasDISTOBJvalue ?oldObj .
      tb:param_orang tb:ASdp_hasDISTPERvalue ?oldPer .
    }
    INSERT {
      tb:param_objek a tb:parameter ;
        tb:ASdp_hasDISTOBJvalue "${distance1}"^^xsd:float ;
        tb:M_hasAction tb:act_AS_Valve .

      tb:param_orang a tb:parameter ;
        tb:ASdp_hasDISTPERvalue "${distance2}"^^xsd:float ;
        tb:M_hasAction tb:act_AS_Valve .

      tb:act_AS_Valve tb:M_hasActionStatus tb:${status} .
    }
    WHERE {
      OPTIONAL { tb:act_AS_Valve tb:M_hasActionStatus ?old . }
      OPTIONAL { tb:param_objek tb:ASdp_hasDISTOBJvalue ?oldObj . }
      OPTIONAL { tb:param_orang tb:ASdp_hasDISTPERvalue ?oldPer . }
    }
  `;

  try {
    await axios.post(
      FUSEKI_UPDATE,
      `update=${encodeURIComponent(updateQuery)}`,
      {
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
      }
    );

    await axios.get(REASONER_URL);

    res.json({
      message: "Ultrasonic data stored & reasoning triggered",
      status,
    });
  } catch (err) {
    console.error("❌ areaWash error:", err.message);
    res.status(500).json({ error: "Failed to update or trigger reasoner" });
  }
});

// GET /api/valve-status
router.get("/valve-status", async (req, res) => {
  const query = `
    PREFIX tb: <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
    SELECT ?dist1 ?dist2 ?status WHERE {
      tb:param_objek tb:ASdp_hasDISTOBJvalue ?dist1 .
      tb:param_orang tb:ASdp_hasDISTPERvalue ?dist2 .
      tb:act_AS_Valve tb:M_hasActionStatus ?status .
    }
    LIMIT 1
  `;

  try {
    const result = await axios.get(
      `${FUSEKI_QUERY}?query=${encodeURIComponent(query)}`,
      { headers: { Accept: "application/sparql-results+json" } }
    );

    const b = result.data.results.bindings[0] || {};
    const distance1 = parseFloat(b.dist1?.value || 0);
    const distance2 = parseFloat(b.dist2?.value || 0);
    const valStatus = b.status?.value?.split("#")[1] || "OFF";

    res.json({ distance1, distance2, valStatus });
  } catch (err) {
    console.error("❌ /valve-status error:", err.message);
    res.status(500).json({ error: "Failed to fetch status" });
  }
});

// GET /api/cuci/status → koneksi backend
router.get("/cuci/status", async (req, res) => {
  let fusekiConnected = false;
  const now = Date.now();
  const espConnected = now - lastEspUpdate < 10000;

  try {
    await axios.get(
      `${FUSEKI_QUERY}?query=${encodeURIComponent("ASK { ?s ?p ?o }")}`
    );
    fusekiConnected = true;
  } catch {
    fusekiConnected = false;
  }

  res.json({
    backend: true,
    fuseki: fusekiConnected,
    iot: espConnected,
  });
});

let lastEspUpdate = 0;
setInterval(() => {
  lastEspUpdate = Date.now();
}, 5000); // Dummy ping updater

module.exports = router;
