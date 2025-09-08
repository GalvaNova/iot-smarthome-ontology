// serverThesis.js
const express = require("express");
const cors = require("cors");
const bodyParser = require("body-parser");
const { exec } = require("child_process");
const path = require("path");
require("dotenv").config();

// === Konfigurasi dari .env ===
const PORT = process.env.BACKEND_PORT || 5000;
const COOK_NOTIFIER = process.env.COOK_NOTIFIER || "cook-notifier.js";

const app = express();

// Middleware
app.use(cors());
app.use(express.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Modular API routes
const areaCook = require("./routes/areaCook");
const areaInout = require("./routes/areaInout");
const areaWash = require("./routes/areaWash");
const emailManager = require("./routes/emailManager");

app.use("/api", areaCook);
app.use("/api", areaInout);
app.use("/api", areaWash);
app.use("/api", emailManager);

// Serve static frontend
app.use("/", express.static(path.join(__dirname, "../frontend")));

// Jalankan cook-notifier.js secara otomatis (opsional)
if (COOK_NOTIFIER) {
  exec(`node ${COOK_NOTIFIER}`, (err, stdout, stderr) => {
    if (err) {
      console.error("❌ Gagal memulai cook-notifier:", stderr);
    } else {
      console.log(`🚀 ${COOK_NOTIFIER} dijalankan.`);
    }
  });
}

app.listen(PORT, "0.0.0.0", () => {
  console.log(`✅ Backend aktif di http://0.0.0.0:${PORT}`);
});
