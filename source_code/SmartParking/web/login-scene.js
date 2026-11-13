/* The 3D parking lot on the sign-in page.
 *
 * Cars drive in, stop at the ticket machine, wait for the barrier, drive past
 * their bay and reverse into it. Later they pull out forward and leave through
 * the exit barrier. Only one car uses the aisle at a time, so they never touch.
 *
 * World units are metres. The ground is the XZ plane, +Z comes toward the camera.
 * A car's nose points along +X in its own model; its heading is rotation.y.
 */
import * as THREE from "three";
import { RoundedBoxGeometry } from "three/addons/geometries/RoundedBoxGeometry.js";

const canvas = document.getElementById("scene-canvas");
const sceneBox = document.getElementById("scene");
const tagEl = document.getElementById("plate-tag");
const tagPlate = document.getElementById("plate-text");
const tagTime = document.getElementById("plate-time");
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

let renderer;
try {
  renderer = new THREE.WebGLRenderer({ canvas: canvas, antialias: true, alpha: true });
} catch (error) {
  document.documentElement.classList.add("no-webgl");
  throw error;
}
renderer.setClearColor(0x000000, 0);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;

/* ---------- layout of the lot ---------- */

const LANE_Z = 3.2;            // the aisle the cars drive along (heading -X)
const BAY_Z = -2.2;            // centre line of the bays
const R = 3.4;                 // turning radius of the reversing arc
const S1 = 2.0;                // straight part inside the bay
const ARC = Math.PI * R / 2;   // length of the quarter turn
const OVERSHOOT = 1.4;         // how far past the arc start a car drives before it reverses
const BAYS = [-3.4, 0, 3.4, 6.8];
const STOP_X = 10.6;           // where a car waits at the ticket machine
const START_X = 18;
const END_X = -16.5;
const IN_BARRIER_X = 7.6;
const OUT_BARRIER_X = -12.3;
const CAR_L = 3.5, CAR_W = 1.7, WHEELBASE = 2.2;

/* ---------- colors ---------- */

const ACCENT = 0x2f6df6;   // the sign-in page uses blue only
function accentColor() { return ACCENT; }

const M = {
  white: new THREE.MeshStandardMaterial({ color: 0xf3f4f3, roughness: 0.55 }),
  accent: new THREE.MeshStandardMaterial({ color: accentColor(), roughness: 0.5 }),
  glass: new THREE.MeshStandardMaterial({ color: 0x2c3442, roughness: 0.25, metalness: 0.15 }),
  tire: new THREE.MeshStandardMaterial({ color: 0x2b2e31, roughness: 0.9 }),
  cream: new THREE.MeshStandardMaterial({ color: 0xfff1cc, roughness: 0.4, emissive: 0x3a2f12 }),
  red: new THREE.MeshStandardMaterial({ color: 0xe5373b, roughness: 0.45 }),
  dark: new THREE.MeshStandardMaterial({ color: 0x33373b, roughness: 0.6 }),
  line: new THREE.MeshStandardMaterial({ color: 0xf6f7f7, roughness: 0.9 }),
  eye: new THREE.MeshStandardMaterial({ color: 0x3b6cff, emissive: 0x2a56ff, emissiveIntensity: 1.4, roughness: 0.3 }),
  face: new THREE.MeshStandardMaterial({ color: 0x1b2030, roughness: 0.2, metalness: 0.2 })
};

/* ---------- scene, light, camera ---------- */

const scene = new THREE.Scene();

const sky = new THREE.HemisphereLight(0xffffff, 0xb4b9bd, 2.3);
scene.add(sky);

const sun = new THREE.DirectionalLight(0xffffff, 2.6);
sun.position.set(-6, 16, 10);
sun.target.position.set(2, 0, 0);
sun.castShadow = true;
sun.shadow.mapSize.set(2048, 2048);
sun.shadow.camera.left = -18;
sun.shadow.camera.right = 18;
sun.shadow.camera.top = 15;
sun.shadow.camera.bottom = -15;
sun.shadow.camera.near = 2;
sun.shadow.camera.far = 45;
sun.shadow.radius = 5;
sun.shadow.bias = -0.0004;
sun.shadow.normalBias = 0.03;
scene.add(sun, sun.target);

const ground = new THREE.Mesh(
  new THREE.PlaneGeometry(90, 70),
  new THREE.ShadowMaterial({ opacity: 0.24 })
);
ground.rotation.x = -Math.PI / 2;
ground.receiveShadow = true;
scene.add(ground);

const camera = new THREE.PerspectiveCamera(24, 1, 1, 200);
const LOOK_AT = new THREE.Vector3(3.2, 0.2, 1.6);
const VIEW_DIR = new THREE.Vector3(-0.5, 0.62, 0.9).normalize();
const HALF_WIDTH = 10.8;

function fit() {
  const w = Math.max(1, sceneBox.clientWidth);
  const h = Math.max(1, sceneBox.clientHeight);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  const halfFov = Math.atan(Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * camera.aspect);
  const distance = HALF_WIDTH / Math.tan(halfFov);
  camera.position.copy(LOOK_AT).addScaledVector(VIEW_DIR, distance);
  camera.lookAt(LOOK_AT);
  camera.updateProjectionMatrix();
  tagWidth = tagEl.offsetWidth;
  tagHeight = tagEl.offsetHeight;
}
let tagWidth = 90, tagHeight = 40;

/* ---------- small helpers ---------- */

const rounded = (w, h, d, r, seg) => new RoundedBoxGeometry(w, h, d, seg || 3, r);

function mesh(geometry, material, x, y, z, parent) {
  const m = new THREE.Mesh(geometry, material);
  m.position.set(x, y, z);
  m.castShadow = true;
  m.receiveShadow = true;
  if (parent) parent.add(m);
  return m;
}

const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const easeInOut = p => 0.5 - 0.5 * Math.cos(Math.PI * p);
const easeOut = p => 1 - (1 - p) * (1 - p);
const easeIn = p => p * p;
const rand = (a, b) => a + Math.random() * (b - a);

/* ---------- cars ---------- */

const G = {
  body: rounded(CAR_L, 0.7, CAR_W, 0.28, 4),
  glass: rounded(1.95, 0.46, 1.5, 0.12, 3),
  roof: rounded(2.15, 0.16, 1.62, 0.07, 3),
  pillar: rounded(0.14, 0.5, 1.62, 0.05, 2),
  bumper: rounded(0.16, 0.16, 1.5, 0.07, 2),
  grille: rounded(0.06, 0.2, 0.7, 0.03, 2),
  tail: rounded(0.08, 0.16, 0.34, 0.03, 2),
  light: new THREE.SphereGeometry(0.17, 14, 10),
  tire: new THREE.CylinderGeometry(0.36, 0.36, 0.3, 22).rotateX(Math.PI / 2),
  rim: new THREE.CylinderGeometry(0.2, 0.2, 0.32, 16).rotateX(Math.PI / 2)
};

const LETTERS = "ABCDEFGHJKLMNPRSTUVWXYZ";
function makePlate() {
  let text = "";
  for (let i = 0; i < 3; i++) text += LETTERS[Math.floor(Math.random() * LETTERS.length)];
  return text + " " + String(Math.floor(rand(1000, 9999)));
}

function buildCar(bodyMat) {
  const group = new THREE.Group();
  mesh(G.body, bodyMat, 0, 0.62, 0, group);
  mesh(G.glass, M.glass, -0.2, 1.2, 0, group);
  mesh(G.roof, M.white, -0.2, 1.51, 0, group);
  mesh(G.pillar, M.white, 0.78, 1.2, 0, group).rotation.z = -0.22;
  mesh(G.pillar, M.white, -1.18, 1.2, 0, group).rotation.z = 0.12;
  mesh(G.bumper, M.white, 1.78, 0.42, 0, group);
  mesh(G.bumper, M.white, -1.78, 0.42, 0, group);
  mesh(G.grille, M.dark, 1.76, 0.7, 0, group);
  for (const side of [-1, 1]) {
    const lamp = mesh(G.light, M.cream, 1.72, 0.76, side * 0.58, group);
    lamp.scale.set(0.55, 1, 1);
    mesh(G.tail, M.red, -1.76, 0.74, side * 0.58, group);
  }

  const wheels = [];
  for (const wx of [1.1, -1.1]) {
    for (const wz of [-0.82, 0.82]) {
      const steer = new THREE.Group();
      steer.position.set(wx, 0.36, wz);
      const spin = new THREE.Group();
      steer.add(spin);
      mesh(G.tire, M.tire, 0, 0, 0, spin);
      mesh(G.rim, M.white, 0, 0, 0, spin);
      group.add(steer);
      wheels.push({ steer: steer, spin: spin, front: wx > 0 });
    }
  }
  return { group: group, wheels: wheels };
}

/* ---------- the path of a car that parks in bay bx ---------- */

/* s = 0 is the parked spot. The car leaves along +s: straight out of the bay,
 * a quarter turn to the right onto the aisle, then along the aisle toward -X.
 * Parking is the same path walked backward. th is the heading (rotation.y). */
function pathAt(bx, s) {
  if (s <= S1) {
    return { x: bx, z: BAY_Z + Math.max(s, 0), th: -Math.PI / 2 };
  }
  if (s <= S1 + ARC) {
    const phi = (s - S1) / R;
    return {
      x: bx - R + R * Math.cos(phi),
      z: BAY_Z + S1 + R * Math.sin(phi),
      th: -Math.PI / 2 - phi
    };
  }
  return { x: bx - R - (s - S1 - ARC), z: LANE_Z, th: -Math.PI };
}

/* ---------- fixtures ---------- */

const fixtures = {};

function buildBays() {
  const g = new THREE.Group();
  const side = new THREE.BoxGeometry(0.09, 0.02, 3.6);
  const back = new THREE.BoxGeometry(3.4, 0.02, 0.09);
  for (let i = 0; i <= BAYS.length; i++) {
    const x = BAYS[0] - 1.7 + i * 3.4;
    mesh(side, M.line, x, 0.01, BAY_Z - 0.2, g).castShadow = false;
  }
  for (const bx of BAYS) mesh(back, M.line, bx, 0.01, BAY_Z - 2.0, g).castShadow = false;
  scene.add(g);
}

function buildBollards() {
  const spots = [];
  for (let x = -8.5; x <= 12.5; x += 1.7) spots.push([x, -6.0]);
  for (let x = -7.5; x <= 8; x += 1.9) spots.push([x, 7.9]);
  for (let z = -4.4; z <= 6.4; z += 1.7) {
    if (z > 0.6 && z < 5.8) continue;
    spots.push([-9.4, z]);
  }
  const post = new THREE.CapsuleGeometry(0.16, 0.5, 4, 12);
  const band = new THREE.CylinderGeometry(0.17, 0.17, 0.16, 14);
  const posts = new THREE.InstancedMesh(post, M.white, spots.length);
  const bands = new THREE.InstancedMesh(band, M.accent, spots.length);
  const m4 = new THREE.Matrix4();
  spots.forEach((p, i) => {
    m4.makeTranslation(p[0], 0.42, p[1]);
    posts.setMatrixAt(i, m4);
    m4.makeTranslation(p[0], 0.5, p[1]);
    bands.setMatrixAt(i, m4);
  });
  for (const m of [posts, bands]) { m.castShadow = true; m.receiveShadow = true; scene.add(m); }
}

function makeBarrier(x) {
  const group = new THREE.Group();
  group.position.set(x, 0, 5.3);
  const base = mesh(rounded(0.5, 1.0, 0.5, 0.12, 3), M.accent, 0, 0.5, 0, group);
  const pivot = new THREE.Group();
  pivot.position.set(0, 1.0, 0);
  group.add(pivot);
  const stripe = new THREE.BoxGeometry(0.16, 0.16, 0.5);
  for (let i = 0; i < 6; i++) {
    mesh(stripe, i % 2 ? M.white : M.red, 0, 0, -0.45 - i * 0.5, pivot);
  }
  scene.add(group);
  return { pivot: pivot, angle: 0, target: 0, base: base };
}

function makeDisplay(width, height) {
  const cv = document.createElement("canvas");
  cv.width = 320;
  cv.height = Math.round(320 * height / width);
  const tex = new THREE.CanvasTexture(cv);
  tex.colorSpace = THREE.SRGBColorSpace;
  return { cv: cv, tex: tex };
}

function buildEntrance() {
  /* gantry with a display over the entrance aisle */
  const g = new THREE.Group();
  g.position.set(12.8, 0, 0);
  const post = rounded(0.4, 3.6, 0.4, 0.1, 3);
  mesh(post, M.accent, 0, 1.8, 0.9, g);
  mesh(post, M.accent, 0, 1.8, 5.9, g);
  mesh(rounded(0.45, 0.45, 5.4, 0.12, 3), M.accent, 0, 3.7, 3.4, g);
  const display = makeDisplay(2.4, 0.9);
  const screen = mesh(rounded(0.12, 0.9, 2.4, 0.06, 2), M.dark, -0.3, 3.0, 3.4, g);
  const glow = new THREE.Mesh(new THREE.PlaneGeometry(2.2, 0.72), new THREE.MeshBasicMaterial({ map: display.tex }));
  glow.rotation.y = -Math.PI / 2;
  glow.position.set(-0.365, 3.0, 3.4);
  g.add(glow);
  scene.add(g);
  fixtures.display = display;

  /* ticket machine beside the aisle */
  const machine = new THREE.Group();
  machine.position.set(STOP_X, 0, 4.95);
  mesh(rounded(0.9, 1.15, 0.9, 0.2, 4), M.accent, 0, 0.575, 0, machine);
  mesh(rounded(0.7, 0.12, 0.12, 0.04, 2), M.dark, 0, 1.0, 0, machine).position.set(0, 1.18, 0);
  const button = new THREE.Group();
  button.position.set(-0.46, 0.7, 0);
  machine.add(button);
  const cap = new THREE.CylinderGeometry(0.17, 0.2, 0.14, 20).rotateZ(Math.PI / 2);
  mesh(cap, M.red, 0, 0, 0, button);
  const tcv = document.createElement("canvas");
  tcv.width = 64; tcv.height = 96;
  const tctx = tcv.getContext("2d");
  tctx.fillStyle = "#ffffff"; tctx.fillRect(0, 0, 64, 96);
  tctx.fillStyle = "#c5c9cc";
  for (let y = 18; y < 82; y += 9) tctx.fillRect(8, y, 36 + (y % 18), 3);
  tctx.fillStyle = "#2c3036"; tctx.fillRect(8, 6, 22, 6);
  const ticketTex = new THREE.CanvasTexture(tcv);
  ticketTex.colorSpace = THREE.SRGBColorSpace;
  const ticket = new THREE.Mesh(
    new THREE.BoxGeometry(0.03, 0.62, 0.42),
    new THREE.MeshStandardMaterial({ map: ticketTex, roughness: 0.9 })
  );
  ticket.castShadow = true;
  ticket.position.set(-0.1, 1.0, 0);
  ticket.rotation.z = 0.12;
  machine.add(ticket);
  scene.add(machine);
  fixtures.button = button;
  fixtures.ticket = ticket;
  fixtures.ticketAmount = 0;   // 0 hidden in the slot, 1 pushed out
  fixtures.ticketTarget = 0;
  fixtures.buttonTarget = 0;

  /* P sign */
  const sign = new THREE.Group();
  sign.position.set(13.3, 0, -2.6);
  sign.rotation.y = -0.55;
  mesh(new THREE.CylinderGeometry(0.1, 0.1, 1.8, 10), M.accent, 0, 0.9, 0, sign);
  const board = mesh(rounded(1.5, 1.8, 0.24, 0.2, 4), M.accent, 0, 2.3, 0, sign);
  const pcv = document.createElement("canvas");
  pcv.width = 128; pcv.height = 128;
  const ptex = new THREE.CanvasTexture(pcv);
  ptex.colorSpace = THREE.SRGBColorSpace;
  const face = new THREE.Mesh(new THREE.PlaneGeometry(1.1, 1.1), new THREE.MeshBasicMaterial({ map: ptex, transparent: true }));
  face.position.set(0, 2.3, 0.13);
  sign.add(face);
  scene.add(sign);
  fixtures.pSign = { cv: pcv, tex: ptex };

  fixtures.inBarrier = makeBarrier(IN_BARRIER_X);
}

function buildExit() {
  fixtures.outBarrier = makeBarrier(OUT_BARRIER_X);
}

function paintDisplays() {
  const hex = "#" + new THREE.Color(accentColor()).getHexString();
  const d = fixtures.display;
  const ctx = d.cv.getContext("2d");
  ctx.fillStyle = "#1d2023";
  ctx.fillRect(0, 0, d.cv.width, d.cv.height);
  ctx.fillStyle = hex === "#2c2f32" ? "#e8eaeb" : hex;
  ctx.font = "900 62px 'SP Display', Arial Black, Arial, sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(freeBays() + " FREE", d.cv.width / 2, d.cv.height * 0.4);
  ctx.fillStyle = "#9aa0a4";
  ctx.font = "600 26px 'SP Text', Arial, sans-serif";
  ctx.fillText("of " + BAYS.length + " bays", d.cv.width / 2, d.cv.height * 0.78);
  d.tex.needsUpdate = true;

  const p = fixtures.pSign;
  const pc = p.cv.getContext("2d");
  pc.clearRect(0, 0, 128, 128);
  pc.fillStyle = "#ffffff";
  pc.font = "900 120px 'SP Display', Arial Black, Arial, sans-serif";
  pc.textAlign = "center";
  pc.textBaseline = "middle";
  pc.fillText("P", 64, 70);
  p.tex.needsUpdate = true;
}

/* ---------- the scanner robot ---------- */

const robot = { group: new THREE.Group(), body: null, yaw: 0 };
function buildRobot() {
  const g = robot.group;
  const shell = new THREE.SphereGeometry(0.55, 28, 20);
  const head = mesh(shell, M.white, 0, 0, 0, g);
  head.scale.set(1.15, 0.92, 1.0);
  const face = mesh(new THREE.SphereGeometry(0.5, 24, 16), M.face, 0.18, 0.02, 0, g);
  face.scale.set(0.95, 0.66, 0.9);
  for (const side of [-1, 1]) {
    mesh(new THREE.SphereGeometry(0.09, 12, 10), M.eye, 0.58, 0.04, side * 0.2, g).scale.set(0.5, 1, 0.8);
  }
  mesh(new THREE.CylinderGeometry(0.02, 0.02, 0.5, 6), M.white, -0.15, 0.7, 0.2, g).rotation.z = 0.2;
  mesh(new THREE.SphereGeometry(0.06, 10, 8), M.white, -0.2, 0.96, 0.2, g);
  mesh(new THREE.TorusGeometry(0.26, 0.05, 8, 20), M.accent, 0, -0.52, 0, g).rotation.x = Math.PI / 2;
  g.position.set(2, 3.6, 1);
  g.scale.setScalar(1.35);
  scene.add(g);
}

/* ---------- the simulation ---------- */

const cars = [];
const sim = {
  time: 0,
  token: null,          // the one car that may use the aisle
  lastRelease: 0,
  nextSpawn: 1.2,
  collisions: 0,
  robotTarget: null,
  robotSwitch: 0,
  freeShown: -1
};

function freeBays() {
  return BAYS.length - cars.filter(c => c.bay !== null && c.state !== "gone").length;
}

function pickBay() {
  const taken = new Set(cars.map(c => c.bay));
  const free = BAYS.map((_, i) => i).filter(i => !taken.has(i));
  return free.length ? free[Math.floor(Math.random() * free.length)] : -1;
}

function spawnCar(kind, bayIndex, parked) {
  const mat = kind === "accent" ? M.accent : M.white;
  const built = buildCar(mat);
  scene.add(built.group);
  built.group.traverse(o => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; } });
  const car = {
    group: built.group, wheels: built.wheels,
    bay: bayIndex, bx: BAYS[bayIndex], plate: makePlate(),
    state: "approach", t: 0, leg: null, mode: "lane",
    x: START_X, z: LANE_Z, th: -Math.PI, steer: 0, ticketAt: null, dwell: rand(7, 11)
  };
  cars.push(car);
  if (parked) {
    car.state = "parked";
    car.mode = "path";
    car.s = 0;
    car.ticketAt = sim.time - rand(6, 20);
    car.dwell = rand(2, 8);
    pose(car, 0);
  } else {
    sim.token = car;
    startLeg(car, "approach", START_X, STOP_X, 2.6, easeOut, "lane");
    pose(car, START_X);
  }
  return car;
}

function startLeg(car, name, from, to, duration, ease, mode) {
  car.state = name;
  car.mode = mode;
  car.leg = { from: from, to: to, T: duration, t: 0, ease: ease };
}

function pose(car, v) {
  car.v = v;
  if (car.mode === "lane") {
    car.x = v; car.z = LANE_Z; car.th = -Math.PI;
  } else {
    const p = pathAt(car.bx, v);
    car.x = p.x; car.z = p.z; car.th = p.th;
  }
}

function releaseToken(car) {
  if (sim.token === car) {
    sim.token = null;
    sim.lastRelease = sim.time;
  }
}

function removeCar(car) {
  scene.remove(car.group);
  car.state = "gone";
  cars.splice(cars.indexOf(car), 1);
}

function stepCar(car, dt) {
  const px = car.x, pz = car.z, pth = car.th;
  car.t += dt;

  if (car.leg) {
    car.leg.t += dt;
    const p = clamp(car.leg.t / car.leg.T, 0, 1);
    pose(car, car.leg.from + (car.leg.to - car.leg.from) * car.leg.ease(p));
    if (p >= 1) finishLeg(car);
  }

  if (car.state === "ticket") {
    /* push the button, take the ticket, lift the barrier, then go */
    if (car.t > 0.35) fixtures.buttonTarget = 1;
    if (car.t > 0.6) fixtures.ticketTarget = 1;
    if (car.t > 0.9 && car.t < 1.5) fixtures.buttonTarget = 0;
    if (car.t > 1.5) { fixtures.ticketTarget = 0; fixtures.inBarrier.target = 1.35; }
    if (car.t > 2.3) {
      const xs = car.bx - R - OVERSHOOT;
      startLeg(car, "gate", STOP_X, xs, (STOP_X - xs) / 4.6, easeInOut, "lane");
    }
  } else if (car.state === "gate") {
    if (car.x < IN_BARRIER_X - 2.0) fixtures.inBarrier.target = 0;
  } else if (car.state === "parked") {
    if (car.t > car.dwell) car.state = "waiting";
  } else if (car.state === "leaving") {
    const b = fixtures.outBarrier;
    if (car.x < -6.5) b.target = 1.35;
    if (car.x < OUT_BARRIER_X - 2.2) b.target = 0;
  } else if (car.state === "reversing") {
    if (car.v < S1 + 0.6) releaseToken(car);
  }

  /* wheels roll by the distance driven along the nose, the front ones steer */
  const dx = car.x - px, dz = car.z - pz;
  const along = dx * Math.cos(car.th) + dz * -Math.sin(car.th);
  let dth = car.th - pth;
  let steerTarget = car.steer;
  if (Math.abs(along) > 1e-5) steerTarget = clamp(Math.atan(WHEELBASE * dth / along), -0.55, 0.55);
  car.steer += (steerTarget - car.steer) * Math.min(1, dt * 10);
  for (const w of car.wheels) {
    w.spin.rotation.z -= along / 0.36;
    if (w.front) w.steer.rotation.y = car.steer;
  }

  car.group.position.set(car.x, 0, car.z);
  car.group.rotation.y = car.th;
}

function finishLeg(car) {
  const leg = car.leg;
  car.leg = null;
  if (car.state === "approach") {
    car.state = "ticket";
    car.t = 0;
    car.ticketAt = sim.time;
  } else if (car.state === "gate") {
    car.mode = "path";
    const sStart = S1 + ARC + OVERSHOOT;
    car.state = "pause";
    car.t = 0;
    car.pauseUntil = 0.5;
    car.v = sStart;
  } else if (car.state === "reversing") {
    car.state = "parked";
    car.t = 0;
    car.dwell = rand(7, 11);
    releaseToken(car);
  } else if (car.state === "leaving") {
    releaseToken(car);
    removeCar(car);
  }
}

function pauseTick(car) {
  /* a short stop in the aisle before backing up */
  if (car.state === "pause" && car.t >= car.pauseUntil) {
    const sStart = S1 + ARC + OVERSHOOT;
    startLeg(car, "reversing", sStart, 0, (sStart) / 2.0, easeInOut, "path");
  }
}

function schedule() {
  if (sim.token) return;
  const ready = cars.filter(c => c.state === "waiting");
  if (ready.length) {
    const car = ready[Math.floor(Math.random() * ready.length)];
    sim.token = car;
    const total = S1 + ARC + (car.bx - R - END_X);
    startLeg(car, "leaving", 0, total, total / 3.2, easeIn, "path");
    car.t = 0;
    return;
  }
  const parked = cars.length;
  if (parked < 3 && sim.time >= sim.nextSpawn && sim.time - sim.lastRelease > 1.0) {
    const bay = pickBay();
    if (bay >= 0) {
      spawnCar(Math.random() < 0.45 ? "accent" : "white", bay, false);
      sim.nextSpawn = sim.time + rand(2, 5);
    }
  }
}

/* two oriented boxes overlap? (separating axis test, top view) */
function overlap(a, b) {
  const axes = [];
  for (const c of [a, b]) {
    axes.push([Math.cos(c.th), -Math.sin(c.th)], [Math.sin(c.th), Math.cos(c.th)]);
  }
  const corners = c => {
    const f = [Math.cos(c.th), -Math.sin(c.th)];
    const s = [Math.sin(c.th), Math.cos(c.th)];
    const out = [];
    for (const i of [-1, 1]) for (const j of [-1, 1]) {
      out.push([c.x + f[0] * CAR_L / 2 * i + s[0] * CAR_W / 2 * j, c.z + f[1] * CAR_L / 2 * i + s[1] * CAR_W / 2 * j]);
    }
    return out;
  };
  const ca = corners(a), cb = corners(b);
  for (const ax of axes) {
    const pa = ca.map(p => p[0] * ax[0] + p[1] * ax[1]);
    const pb = cb.map(p => p[0] * ax[0] + p[1] * ax[1]);
    if (Math.max(...pa) < Math.min(...pb) || Math.max(...pb) < Math.min(...pa)) return false;
  }
  return true;
}

function checkCollisions() {
  for (let i = 0; i < cars.length; i++) {
    for (let j = i + 1; j < cars.length; j++) {
      if (overlap(cars[i], cars[j])) sim.collisions++;
    }
  }
}

function animateFixtures(dt) {
  const k = Math.min(1, dt * 7);
  for (const b of [fixtures.inBarrier, fixtures.outBarrier]) {
    b.angle += (b.target - b.angle) * k;
    b.pivot.rotation.x = b.angle;
  }
  fixtures.ticketAmount += (fixtures.ticketTarget - fixtures.ticketAmount) * k;
  fixtures.ticket.position.y = 0.75 + fixtures.ticketAmount * 0.55;
  fixtures.button.position.x = -0.46 + (1 - 0) * 0.0 + fixtures.buttonTarget * 0.05;
}

/* ---------- robot and plate tag ---------- */

function pickRobotTarget() {
  if (sim.token && sim.token.ticketAt !== null) return sim.token;
  const lot = cars.filter(c => c.state === "parked" || c.state === "waiting");
  if (!lot.length) return null;
  if (sim.robotTarget && lot.includes(sim.robotTarget) && sim.time < sim.robotSwitch) return sim.robotTarget;
  sim.robotSwitch = sim.time + 5;
  return lot[Math.floor(Math.random() * lot.length)];
}

function formatClock(seconds) {
  const s = Math.max(0, seconds);
  const two = n => String(Math.floor(n)).padStart(2, "0");
  return two(s / 3600) + ":" + two((s / 60) % 60) + ":" + two(s % 60) + ":" + two((s % 1) * 100);
}

let lastTagText = "";
function animateRobot(dt) {
  sim.robotTarget = pickRobotTarget();
  const t = sim.robotTarget;
  const g = robot.group;
  const bob = Math.sin(sim.time * 1.9) * 0.14;
  const goalX = t ? t.x + 0.4 : 2.5;
  const goalZ = t ? Math.min(t.z, LANE_Z) - 0.6 : 0.8;
  const k = Math.min(1, dt * 2.2);
  g.position.x += (goalX - g.position.x) * k;
  g.position.z += (goalZ - g.position.z) * k;
  g.position.y = 3.5 + bob;
  g.rotation.z = clamp((goalX - g.position.x) * -0.08, -0.25, 0.25);

  /* face the camera, a little toward the car it follows */
  const want = Math.atan2(-(camera.position.z - g.position.z), camera.position.x - g.position.x) + 0.45;
  robot.yaw += (want - robot.yaw) * Math.min(1, dt * 3);
  g.rotation.y = robot.yaw;

  if (t && t.ticketAt !== null) {
    const text = t.plate;
    const timeText = formatClock(sim.time - t.ticketAt);
    if (text !== tagPlate.textContent) { tagPlate.textContent = text; }
    if (timeText !== lastTagText) { tagTime.textContent = timeText; lastTagText = timeText; }
    tagEl.classList.add("on");
  } else {
    tagEl.classList.remove("on");
  }
}

const tmp = new THREE.Vector3();
function placeTag() {
  tmp.copy(robot.group.position);
  tmp.y += 1.5;
  tmp.project(camera);
  const w = sceneBox.clientWidth, h = sceneBox.clientHeight;
  const x = (tmp.x * 0.5 + 0.5) * w - tagWidth / 2;
  const y = (-tmp.y * 0.5 + 0.5) * h - tagHeight;
  tagEl.style.transform = "translate(" + x.toFixed(1) + "px," + y.toFixed(1) + "px)";
}

/* ---------- main step ---------- */

function step(dt) {
  sim.time += dt;
  schedule();
  for (const car of cars.slice()) {
    pauseTick(car);
    stepCar(car, dt);
  }
  checkCollisions();
  animateFixtures(dt);
  animateRobot(dt);
  const free = freeBays();
  if (free !== sim.freeShown) {
    sim.freeShown = free;
    paintDisplays();
  }
}

function render() {
  placeTag();
  renderer.render(scene, camera);
}

/* ---------- start ---------- */

function applyTheme() {
  M.accent.color.set(accentColor());
  ground.material.opacity = document.documentElement.dataset.theme === "dark" ? 0.4 : 0.24;
  paintDisplays();
  if (reducedMotion) render();
}

buildBays();
buildBollards();
buildEntrance();
buildExit();
buildRobot();
fit();

/* two cars are already parked when the page opens, one arrives right away */
spawnCar("white", 1, true);
spawnCar("accent", 3, true);
sim.nextSpawn = 0.6;
applyTheme();
window.addEventListener("smartpark:accent", applyTheme);

if (document.fonts && document.fonts.ready) document.fonts.ready.then(paintDisplays);

new ResizeObserver(() => { fit(); if (reducedMotion) render(); }).observe(sceneBox);

window.__sp = {
  cars: cars,
  sim: sim,
  advance(seconds) {
    const dt = 1 / 30;
    for (let t = 0; t < seconds; t += dt) step(dt);
    render();
  },
  render: render
};

if (reducedMotion) {
  /* a still picture: run the lot for a while, then draw it once */
  window.__sp.advance(14);
} else {
  let last = performance.now();
  function frame(now) {
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    step(dt);
    render();
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
}
