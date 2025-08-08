const express = require("express");
const pool = require("../db");
const router = express.Router();

// GET semua email
router.get("/emails", async (req, res) => {
  try {
    const [rows] = await pool.query(
      "SELECT id, email, is_active FROM email_receivers ORDER BY created_at DESC"
    );
    res.json({ emails: rows });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST tambah email baru
router.post("/emails", async (req, res) => {
  const { email } = req.body;
  if (!email || !/^[\w-.]+@([\w-]+\.)+[\w-]{2,4}$/.test(email)) {
    return res.status(400).json({ error: "Format email tidak valid" });
  }
  try {
    await pool.query("INSERT INTO email_receivers (email) VALUES (?)", [email]);
    res.json({ message: "Email berhasil ditambahkan" });
  } catch (e) {
    if (e.code === "ER_DUP_ENTRY") {
      res.status(400).json({ error: "Email sudah terdaftar" });
    } else {
      res.status(500).json({ error: e.message });
    }
  }
});

// PUT toggle aktif/nonaktif
router.put("/emails/:id", async (req, res) => {
  const { id } = req.params;
  const { is_active } = req.body;
  try {
    await pool.query("UPDATE email_receivers SET is_active = ? WHERE id = ?", [
      is_active ? 1 : 0,
      id,
    ]);
    res.json({ message: "Status email diperbarui" });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// DELETE hapus email
router.delete("/emails/:id", async (req, res) => {
  const { id } = req.params;
  try {
    await pool.query("DELETE FROM email_receivers WHERE id = ?", [id]);
    res.json({ message: "Email dihapus" });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

module.exports = router;
