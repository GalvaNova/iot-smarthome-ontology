const mysql = require("mysql2/promise");
const pool = mysql.createPool({
  host: "localhost",
  user: "root",
  password: "",
  database: "email", // isi sesuai DB MySQL
});
module.exports = pool;
