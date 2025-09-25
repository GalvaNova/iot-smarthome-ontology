const express = require("express");
const axios = require("axios");
const router = express.Router();

// 🔹 Endpoint Fuseki
const FUSEKI_UPDATE_WASH = "http://192.168.43.238:3030/areaWash-2/update";
const FUSEKI_QUERY_WASH = "http://192.168.43.238:3030/areaWash-2/query";

// 🔹 Endpoint Java Reasoner
const JAVA_REASONER = "http://localhost:4567/reasoning/areaWash";

// =============================
// 🚰 POST sensor (ultrasonic wash)
// =============================
router.post("/sensorWash", async (req, res) => {
  const { jarak1, jarak2 } = req.body;

  if (jarak1 === undefined || jarak2 === undefined) {
    return res.status(400).json({ error: "Missing jarak values" });
  }

  try {
    // 🔹 1. Kirim ke Java Reasoner untuk reasoning
    const reasoningRes = await axios.post(JAVA_REASONER, { jarak1, jarak2 });
    const { status } = reasoningRes.data;

    console.log(
      `📨 Kirim ke Java Reasoner → jarak1=${jarak1}, jarak2=${jarak2}`
    );
    console.log(`📥 Balasan Reasoner → status=${status}`);

    // 🔹 2. Bangun query SPARQL untuk update ke Fuseki
    const updateQuery = `
      PREFIX tb: <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
      PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

      DELETE {
        tb:act_AS_Valve tb:M_hasActionStatus ?oldStatus .
        tb:param_objek tb:ASdp_hasDISTOBJvalue ?oldObj .
        tb:param_orang tb:ASdp_hasDISTPERvalue ?oldPer .
      }
      INSERT {
        tb:param_objek a tb:parameter ;
          tb:ASdp_hasDISTOBJvalue "${jarak1}"^^xsd:float ;
          tb:M_hasAction tb:act_AS_Valve .

        tb:param_orang a tb:parameter ;
          tb:ASdp_hasDISTPERvalue "${jarak2}"^^xsd:float ;
          tb:M_hasAction tb:act_AS_Valve .

        tb:act_AS_Valve tb:M_hasActionStatus tb:${status} .
      }
      WHERE {
        OPTIONAL { tb:act_AS_Valve tb:M_hasActionStatus ?oldStatus . }
        OPTIONAL { tb:param_objek tb:ASdp_hasDISTOBJvalue ?oldObj . }
        OPTIONAL { tb:param_orang tb:ASdp_hasDISTPERvalue ?oldPer . }
      }
    `;

    console.log("🔥 SPARQL Update Final →\n", updateQuery);

    // 🔹 3. Update ke Fuseki
    await axios.post(
      FUSEKI_UPDATE_WASH,
      `update=${encodeURIComponent(updateQuery)}`,
      { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
    );

    // 🔹 4. Balas ke Arduino
    res.json({ message: "Wash data stored", status });
  } catch (err) {
    console.error("❌ sensorWash error:", err.message);
    res.status(500).json({ error: "Reasoner or Fuseki failed" });
  }
});

// =============================
// 🚰 GET valve status
// =============================
// GET /api/statusWash
router.get("/statusWash", async (req, res) => {
  const selectQuery = `
    PREFIX tb: <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
    SELECT ?dist1 ?dist2 ?status WHERE {
      OPTIONAL { tb:param_objek tb:ASdp_hasDISTOBJvalue ?dist1 . }
      OPTIONAL { tb:param_orang tb:ASdp_hasDISTPERvalue ?dist2 . }
      OPTIONAL { tb:act_AS_Valve tb:M_hasActionStatus ?status . }
    }
    LIMIT 1
  `;

  try {
    const result = await axios.get(
      `${FUSEKI_QUERY_WASH}?query=${encodeURIComponent(selectQuery)}`,
      { headers: { Accept: "application/sparql-results+json" } }
    );

    const b = result.data.results.bindings[0] || {};
    const status = b.status ? b.status.value.split("#").pop() : "status_OFF";

    res.json({
      jarak1: parseFloat(b.dist1?.value || 0),
      jarak2: parseFloat(b.dist2?.value || 0),
      status,
    });
  } catch (err) {
    console.error("❌ /statusWash error:", err.message);
    res.status(500).json({ error: "Failed to fetch status" });
  }
});

module.exports = router;
