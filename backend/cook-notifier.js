// cook-notifier.js
const axios = require("axios");
const TelegramBot = require("node-telegram-bot-api");
require("dotenv").config();

const TELEGRAM_TOKEN =
  process.env.TELEGRAM_TOKEN ||
  "8190460607:AAGa3Bgyl5XTRDwkm7C9HiJ-SvA3xdzYMcs";
const FUSEKI_QUERY_URL = "http://localhost:3030/thesis-1/query";
const POLL_INTERVAL = 5000; // 5 detik

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
    "👋 Selamat datang di notifikasi dapur! Anda akan menerima peringatan otomatis saat memasak atau kebocoran gas terdeteksi."
  );
});

// === Fungsi Polling Fuseki ===
async function checkStatus() {
  try {
    const res = await axios.get(
      `${FUSEKI_QUERY_URL}?query=` +
        encodeURIComponent(`
        PREFIX : <http://www.semanticweb.org/msi/ontologies/2025/5/thesis-1#>
        SELECT ?cook ?buzz ?ppm WHERE {
          OPTIONAL { :fnc_cookAct :M_ActivityStatus ?cook }
          OPTIONAL { :act_AC_Buzzer :M_ActionStatus ?buzz }
          OPTIONAL { :read_AC_Ppm :ACdp_hasPPMvalue ?ppm }
        } LIMIT 1
      `),
      { headers: { Accept: "application/sparql-results+json" } }
    );

    const b = res.data.results.bindings[0] || {};
    const cookStatus = b.cook?.value.split("#")[1] ?? "UNKNOWN";
    const buzzerStatus = b.buzz?.value.split("#")[1] ?? "OFF";
    const ppm = parseFloat(b.ppm?.value ?? 0);

    // Notifikasi memasak
    if (cookStatus === "st_cookYES" && lastState.cooking !== "YES") {
      notifyAll(
        "🍳 *Memasak DIMULAI!*",
        "Aktivitas memasak sedang berlangsung."
      );
      lastState.cooking = "YES";
    } else if (cookStatus === "st_cookNO" && lastState.cooking !== "NO") {
      notifyAll("✅ *Memasak SELESAI*", "Tidak ada aktivitas memasak.");
      lastState.cooking = "NO";
    }

    // Notifikasi gas
    if (buzzerStatus === "st_actON" && ppm >= 700 && !lastState.gasLeak) {
      notifyAll(
        "🚨 *KEBOCORAN GAS!*",
        `Terdeteksi kebocoran gas!\nPPM: *${ppm.toFixed(2)}*`
      );
      lastState.gasLeak = true;
    } else if (
      (buzzerStatus !== "st_actON" || ppm < 700) &&
      lastState.gasLeak
    ) {
      notifyAll("🟢 *Gas Normal*", "Tidak ada kebocoran gas saat ini.");
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
