const express = require("express");
const cors = require("cors");
const bodyParser = require("body-parser");
const { exec } = require("child_process");
const path = require("path");

require("dotenv").config();

const app = express();
const PORT = 5000;

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

// Jalankan cook-notifier.js secara otomatis
exec("node cook-notifier.js", (err, stdout, stderr) => {
  if (err) {
    console.error("❌ Gagal memulai cook-notifier.js:", stderr);
  } else {
    console.log("🚀 cook-notifier.js dijalankan.");
  }
});

// app.listen(PORT, () => {
//   console.log(`✅ Backend aktif di http://0.0.0.0:${PORT}`);
// });

app.listen(PORT, () => {
  console.log(`✅ Backend aktif di http://192.168.43.238:${PORT}`);
});
