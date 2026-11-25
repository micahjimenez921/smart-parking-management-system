// Starts a private SmartPark server for one test file, with its own empty data folder.
// Your real data/ folder is never touched, and the server uses a free port, so it can run
// while your normal server is open on port 8080.

const { spawn, spawnSync } = require("node:child_process");
const fs = require("node:fs");
const net = require("node:net");
const os = require("node:os");
const path = require("node:path");

const ROOT = path.resolve(__dirname, "..", "..");                // the repository folder
const APP = path.join(ROOT, "source_code", "SmartParking");
const BUILD = path.resolve(__dirname, "..", ".build", "classes");

function listJava(dir) {
  const found = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) found.push(...listJava(full));
    else if (entry.name.endsWith(".java")) found.push(full);
  }
  return found;
}

// Compiles the Java code when it changed since the last run.
function compile() {
  const sources = listJava(path.join(APP, "src"));
  const newest = Math.max(...sources.map(f => fs.statSync(f).mtimeMs));
  const stamp = path.join(BUILD, "..", "built-at.txt");
  if (fs.existsSync(stamp) && Number(fs.readFileSync(stamp, "utf8")) >= newest) return;

  fs.rmSync(BUILD, { recursive: true, force: true });
  fs.mkdirSync(BUILD, { recursive: true });
  const argFile = path.join(BUILD, "..", "sources.txt");
  fs.writeFileSync(argFile, sources.map(f => '"' + f.replace(/\\/g, "/") + '"').join("\n"));
  const result = spawnSync("javac", ["-d", BUILD, "@" + argFile], { encoding: "utf8" });
  if (result.status !== 0) {
    throw new Error("javac failed. Is the JDK installed?\n" + (result.stderr || result.error));
  }
  fs.writeFileSync(stamp, String(Date.now()));
}

function freePort() {
  return new Promise((resolve, reject) => {
    const probe = net.createServer();
    probe.listen(0, "127.0.0.1", () => {
      const { port } = probe.address();
      probe.close(() => resolve(port));
    });
    probe.on("error", reject);
  });
}

/** Starts the server and resolves to { url, passwords, stop }. */
async function startServer() {
  compile();

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "smartpark-test-"));
  fs.cpSync(path.join(APP, "web"), path.join(dir, "source_code", "SmartParking", "web"), { recursive: true });

  const port = await freePort();
  const child = spawn("java", ["-Dsmartpark.port=" + port, "-cp", BUILD, "app.WebMain"], { cwd: dir });

  const passwords = {};
  let log = "";
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("The server did not start in time.\n" + log)), 20000);
    const onData = chunk => {
      log += chunk.toString();
      for (const m of log.matchAll(/\(username \/ password\): (\S+) \/ (\S+)/g)) passwords[m[1]] = m[2];
      if (log.includes("SmartPark is running")) {
        clearTimeout(timer);
        resolve();
      }
    };
    child.stdout.on("data", onData);
    child.stderr.on("data", onData);
    child.on("exit", code => reject(new Error("The server stopped (exit " + code + ").\n" + log)));
  });

  return {
    url: "http://localhost:" + port,
    passwords,
    stop() {
      child.removeAllListeners("exit");
      child.kill();
      try { fs.rmSync(dir, { recursive: true, force: true }); } catch (e) { /* Windows may still hold the folder */ }
    }
  };
}

module.exports = { startServer };
