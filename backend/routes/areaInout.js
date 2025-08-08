// routes/areaInout.js
const express = require("express");
const axios = require("axios");
const router = express.Router();

const FUSEKI_UPDATE = "http://192.168.43.238:3030/project-1/update";
const REASONER_URL = "http://localhost:4567/reasoning/inout";

let lastPersonCount = 0;
let lastUpdateTime = 0;

router.post("/dataInout", async (req, res) => {
  const { personCount } = req.body;
  if (personCount === undefined) {
    return res.status(400).json({ error: "personCount missing" });
  }

  const updateQuery = `
    PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
    PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
    DELETE { :read_AE_CountPers :AEdp_hasCOUNTvalue ?old . }
    INSERT { :read_AE_CountPers :AEdp_hasCOUNTvalue "${personCount}"^^xsd:integer . }
    WHERE { OPTIONAL { :read_AE_CountPers :AEdp_hasCOUNTvalue ?old . } }
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
    lastPersonCount = personCount;
    lastUpdateTime = Date.now();

    res.json({ message: "Person count updated & reasoning triggered" });
  } catch (err) {
    console.error("areaInout error:", err.message);
    res.status(500).json({ error: "Failed update or reasoning" });
  }
});

router.get("/statusInout", async (req, res) => {
  const query = `
    PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
    SELECT ?status WHERE { :act_AE_Lamp :M_hasActionStatus ?status }
  `;
  try {
    const r = await axios.get(`${query}`, {
      baseURL: FUSEKI_UPDATE.replace("/update", ""),
      params: { query },
      headers: { Accept: "application/sparql-results+json" },
    });
    const val =
      r.data.results.bindings[0]?.status.value.split("#")[1] ?? "UNKNOWN";
    res.json({
      lampState: val,
      personCount: lastPersonCount,
      espConnected: Date.now() - lastUpdateTime < 10000,
    });
  } catch (err) {
    console.error("statusInout error:", err.message);
    res.status(500).json({ error: true });
  }
});

module.exports = router;
