const nodemailer = require("nodemailer");
const fs = require("fs");
const path = require("path");
const mysql = require("mysql2/promise");

require("dotenv").config();

async function sendLogToEmails(area, filePath) {
  const conn = await mysql.createConnection({
    host: process.env.DB_HOST,
    user: process.env.DB_USER,
    password: process.env.DB_PASS,
    database: process.env.DB_NAME,
  });

  const [rows] = await conn.execute("SELECT email FROM email_subscribers");
  const recipientEmails = rows.map((row) => row.email);
  if (recipientEmails.length === 0) return;

  const template = fs.readFileSync(
    path.join(__dirname, "../emails/logTemplate.html"),
    "utf8"
  );
  const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: {
      user: process.env.EMAIL_SENDER,
      pass: process.env.EMAIL_PASS,
    },
  });

  await transporter.sendMail({
    from: `"Smart Home" <${process.env.EMAIL_SENDER}>`,
    to: recipientEmails,
    subject: `📋 Log Sensor Area ${area}`,
    html: template
      .replace("{{area}}", area)
      .replace("{{timestamp}}", new Date().toLocaleString()),
    attachments: [
      {
        filename: path.basename(filePath),
        path: filePath,
      },
    ],
  });

  console.log(
    `📤 Email log area ${area} dikirim ke: ${recipientEmails.join(", ")}`
  );
}

module.exports = { sendLogToEmails };
