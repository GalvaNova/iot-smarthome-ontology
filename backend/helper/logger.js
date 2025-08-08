const fs = require("fs");
const path = require("path");

function logSensorData(area, data, format = "csv") {
  const timestamp = new Date().toISOString();
  const logDir = path.join(__dirname, "../logs");
  if (!fs.existsSync(logDir)) fs.mkdirSync(logDir);

  let line = "";

  if (format === "csv") {
    const filePath = path.join(logDir, `${area}-log.csv`);
    const headers = Object.keys(data).join(",") + ",timestamp\n";
    if (!fs.existsSync(filePath)) fs.writeFileSync(filePath, headers);
    line = Object.values(data).join(",") + `,${timestamp}\n`;
    fs.appendFileSync(filePath, line);
  } else if (format === "txt") {
    const filePath = path.join(logDir, `${area}-log.txt`);
    line = `[${timestamp}] ${JSON.stringify(data)}\n`;
    fs.appendFileSync(filePath, line);
  }
}

module.exports = { logSensorData };
