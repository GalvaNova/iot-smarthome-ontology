// cook-notifier.js
const axios = require("axios");
const TelegramBot = require("node-telegram-bot-api");
require("dotenv").config();

// === Konfigurasi dari .env ===
const TELEGRAM_TOKEN = process.env.TELEGRAM_TOKEN;
const FUSEKI_QUERY_URL =
  process.env.FUSEKI_QUERY_URL || "http://192.168.43.238:3030/project-1/query";
const POLL_INTERVAL = process.env.POLL_INTERVAL || 5000; // default: 5 detik
const GAS_THRESHOLD = process.env.GAS_THRESHOLD || 700; // default: 700 PPM

if (!TELEGRAM_TOKEN) {
  throw new Error("❌ TELEGRAM_TOKEN belum diatur di .env");
}

const bot = new TelegramBot(TELEGRAM_TOKEN, { polling: true });

let subscribers = new Set();
let lastState = {
  cooking: null,
  gasLeak: false,
};

// === Command Bot ===
bot.onText(/\/start/, (msg) => {
  const chatId = msg.chat.id;
  subscribers.add(chatId);
  bot.sendMessage(
    chatId,
    "👋 Selamat datang di notifikasi dapur!\nAnda akan menerima peringatan otomatis saat memasak atau kebocoran gas terdeteksi."
  );
});

// === Fungsi Polling Fuseki ===
async function checkStatus() {
  try {
    const query = `
      PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
      SELECT ?cook ?buzz ?ppm WHERE {
        OPTIONAL { :fnc_cookAct :M_ActivityStatus ?cook }
        OPTIONAL { :act_AC_Buzzer :M_ActionStatus ?buzz }
        OPTIONAL { :read_AC_Ppm :ACdp_hasPPMvalue ?ppm }
      } LIMIT 1
    `;

    const res = await axios.get(
      `${FUSEKI_QUERY_URL}?query=${encodeURIComponent(query)}`,
      { headers: { Accept: "application/sparql-results+json" } }
    );

    const b = res.data.results.bindings[0] || {};
    const cookStatus = b.cook?.value.split("#")[1] ?? "UNKNOWN";
    const buzzerStatus = b.buzz?.value.split("#")[1] ?? "OFF";
    const ppm = parseFloat(b.ppm?.value ?? 0);

    const now = new Date().toLocaleString();

    // Notifikasi memasak
    if (cookStatus === "st_cookYES" && lastState.cooking !== "YES") {
      notifyAll(
        "🍳 *Memasak DIMULAI!*",
        `Aktivitas memasak sedang berlangsung.\n🕒 ${now}`
      );
      lastState.cooking = "YES";
    } else if (cookStatus === "st_cookNO" && lastState.cooking !== "NO") {
      notifyAll(
        "✅ *Memasak SELESAI*",
        `Tidak ada aktivitas memasak.\n🕒 ${now}`
      );
      lastState.cooking = "NO";
    }

    // Notifikasi gas
    if (
      buzzerStatus === "st_actON" &&
      ppm >= GAS_THRESHOLD &&
      !lastState.gasLeak
    ) {
      notifyAll(
        "🚨 *KEBOCORAN GAS!*",
        `Terdeteksi kebocoran gas!\nPPM: *${ppm.toFixed(2)}*\n🕒 ${now}`
      );
      lastState.gasLeak = true;
    } else if (
      (buzzerStatus !== "st_actON" || ppm < GAS_THRESHOLD) &&
      lastState.gasLeak
    ) {
      notifyAll(
        "🟢 *Gas Normal*",
        `Tidak ada kebocoran gas saat ini.\n🕒 ${now}`
      );
      lastState.gasLeak = false;
    }
  } catch (err) {
    console.error("❌ Gagal polling Fuseki:", err.message);
  }
}

function notifyAll(title, body) {
  for (const id of subscribers) {
    bot.sendMessage(id, `${title}\n${body}`, { parse_mode: "Markdown" });
  }
}

// Mulai polling
setInterval(checkStatus, POLL_INTERVAL);
console.log("🔁 cook-notifier.js berjalan & memantau status dapur...");
