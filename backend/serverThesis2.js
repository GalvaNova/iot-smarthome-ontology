const express = require("express");
const bodyParser = require("body-parser");
const cors = require("cors");

// Import router untuk areaWash
const areaWashRouter = require("./routes/areaWash.js");

const app = express();
const PORT = 5000;

// Middleware
app.use(cors());
app.use(bodyParser.json());

// Routes
app.use("/api", areaWashRouter);

// Root endpoint (opsional)
app.get("/", (req, res) => {
  res.send(
    "🚀 SmartHome Backend berjalan. Gunakan endpoint /api/sensorWash atau /api/statusWash"
  );
});

// Start server
app.listen(PORT, "0.0.0.0", () => {
  console.log(`✅ Backend berjalan di http://localhost:${PORT}`);
});
