/* Small SVG chart helpers. No libraries needed, and they follow the light/dark theme. */

let tipElement = null;

function esc(text) {
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function niceMax(value) {
  if (value <= 0) return 1;
  const power = Math.pow(10, Math.floor(Math.log10(value)));
  const fraction = value / power;
  let nice = 10;
  if (fraction <= 1) nice = 1;
  else if (fraction <= 2) nice = 2;
  else if (fraction <= 2.5) nice = 2.5;
  else if (fraction <= 5) nice = 5;
  return nice * power;
}

function chartTip() {
  if (!tipElement) {
    tipElement = document.createElement("div");
    tipElement.className = "chart-tip";
    tipElement.hidden = true;
    document.body.appendChild(tipElement);
  }
  return tipElement;
}

function attachTips(container) {
  if (container.tipsAttached) return;
  container.tipsAttached = true;

  container.addEventListener("mousemove", event => {
    const tip = chartTip();
    const target = event.target.closest ? event.target.closest("[data-tip]") : null;
    if (!target) {
      tip.hidden = true;
      return;
    }
    tip.textContent = target.getAttribute("data-tip");
    tip.hidden = false;
    tip.style.left = (event.clientX + 14) + "px";
    tip.style.top = (event.clientY + 14) + "px";
  });

  container.addEventListener("mouseleave", () => {
    chartTip().hidden = true;
  });
}

function emptyChart(container, text) {
  container.innerHTML = '<p class="empty">' + esc(text) + "</p>";
}

/* a smooth line through the points that never overshoots between them */
function smoothPath(points) {
  if (points.length === 1) return "M" + points[0].x + " " + points[0].y;

  let path = "M" + points[0].x.toFixed(1) + " " + points[0].y.toFixed(1);
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i - 1] || points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2] || p2;

    const low = Math.min(p1.y, p2.y);
    const high = Math.max(p1.y, p2.y);
    const c1y = Math.min(high, Math.max(low, p1.y + (p2.y - p0.y) / 6));
    const c2y = Math.min(high, Math.max(low, p2.y - (p3.y - p1.y) / 6));
    const c1x = p1.x + (p2.x - p0.x) / 6;
    const c2x = p2.x - (p3.x - p1.x) / 6;

    path += " C" + c1x.toFixed(1) + " " + c1y.toFixed(1) + " " +
      c2x.toFixed(1) + " " + c2y.toFixed(1) + " " +
      p2.x.toFixed(1) + " " + p2.y.toFixed(1);
  }
  return path;
}

/* items: [{ label, value, tip }]   options: { formatAxis, ariaLabel } */
function lineChart(container, items, options) {
  if (items.length === 0) {
    emptyChart(container, "No data yet.");
    return;
  }

  const W = 640, H = 270, left = 58, right = 30, top = 18, bottom = 36;
  const plotW = W - left - right;
  const plotH = H - top - bottom;
  const max = niceMax(Math.max(...items.map(i => i.value)));
  const baseY = top + plotH;
  const formatAxis = options.formatAxis || String;

  const points = items.map((item, index) => ({
    x: items.length === 1 ? left + plotW / 2 : left + (plotW * index) / (items.length - 1),
    y: top + plotH - (item.value / max) * plotH,
    item
  }));

  let svg = '<svg class="chart-svg" viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="' +
    esc(options.ariaLabel || "Line chart") + '">';

  const gradientId = (container.id || "chart") + "-fill";
  svg += '<defs><linearGradient id="' + gradientId + '" x1="0" y1="0" x2="0" y2="1">' +
    '<stop offset="0%" style="stop-color:var(--primary);stop-opacity:0.28"/>' +
    '<stop offset="100%" style="stop-color:var(--primary);stop-opacity:0"/>' +
    "</linearGradient></defs>";

  for (let i = 0; i <= 4; i++) {
    const y = top + plotH - (plotH * i) / 4;
    svg += '<line class="chart-grid" x1="' + left + '" y1="' + y.toFixed(1) +
      '" x2="' + (W - right) + '" y2="' + y.toFixed(1) + '"/>';
    svg += '<text class="chart-axis" x="' + (left - 10) + '" y="' + (y + 4).toFixed(1) +
      '" text-anchor="end">' + esc(formatAxis((max * i) / 4)) + "</text>";
  }

  const line = smoothPath(points);
  const first = points[0];
  const last = points[points.length - 1];
  if (points.length > 1) {
    svg += '<path d="' + line + " L" + last.x.toFixed(1) + " " + baseY + " L" +
      first.x.toFixed(1) + " " + baseY + ' Z" fill="url(#' + gradientId + ')"/>';
  }
  svg += '<path class="chart-line" d="' + line + '"/>';

  const showDots = points.length <= 14;
  for (const p of points) {
    if (showDots) {
      svg += '<circle class="chart-dot" cx="' + p.x.toFixed(1) + '" cy="' + p.y.toFixed(1) + '" r="4.5"/>';
    }
    svg += '<circle class="chart-hit" cx="' + p.x.toFixed(1) + '" cy="' + p.y.toFixed(1) +
      '" r="11" data-tip="' + esc(p.item.tip) + '"/>';
  }

  const step = Math.ceil(points.length / 8);
  points.forEach((p, index) => {
    if ((points.length - 1 - index) % step !== 0) return;
    svg += '<text class="chart-axis" x="' + p.x.toFixed(1) + '" y="' + (H - 12) +
      '" text-anchor="middle">' + esc(p.item.label) + "</text>";
  });

  container.innerHTML = svg + "</svg>";
  attachTips(container);
}

/* items: [{ label, value, tip }] */
function barChart(container, items, options) {
  if (items.length === 0 || items.every(i => i.value === 0)) {
    emptyChart(container, "No data yet.");
    return;
  }

  const W = 900, H = 290, left = 52, right = 24, top = 30, bottom = 38;
  const plotW = W - left - right;
  const plotH = H - top - bottom;
  const biggest = Math.max(...items.map(i => i.value));
  const max = Math.max(4, Math.ceil(biggest / 4) * 4);
  const band = plotW / items.length;
  const barWidth = Math.min(56, band * 0.45);
  const peak = items.findIndex(i => i.value === biggest);
  const gradientId = (container.id || "chart") + "-bars";

  let svg = '<svg class="chart-svg" viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="' +
    esc((options && options.ariaLabel) || "Bar chart") + '">';

  svg += '<defs><linearGradient id="' + gradientId + '" x1="0" y1="0" x2="0" y2="1">' +
    '<stop offset="0%" style="stop-color:var(--primary);stop-opacity:0.9"/>' +
    '<stop offset="100%" style="stop-color:var(--primary);stop-opacity:0.3"/>' +
    "</linearGradient></defs>";

  for (let i = 0; i <= 4; i++) {
    const y = top + plotH - (plotH * i) / 4;
    svg += '<line class="chart-grid" x1="' + left + '" y1="' + y.toFixed(1) +
      '" x2="' + (W - right) + '" y2="' + y.toFixed(1) + '"/>';
    svg += '<text class="chart-axis" x="' + (left - 10) + '" y="' + (y + 4).toFixed(1) +
      '" text-anchor="end">' + Math.round((max * i) / 4) + "</text>";
  }

  items.forEach((item, index) => {
    const height = (item.value / max) * plotH;
    const centerX = left + band * index + band / 2;
    const y = top + plotH - height;

    if (item.value > 0) {
      const fill = index === peak ? "" : ' style="fill:url(#' + gradientId + ')"';
      svg += '<rect class="chart-bar" x="' + (centerX - barWidth / 2).toFixed(1) + '" y="' + y.toFixed(1) +
        '" width="' + barWidth.toFixed(1) + '" height="' + height.toFixed(1) +
        '" rx="9"' + fill + ' data-tip="' + esc(item.tip) + '"/>';
      svg += '<text class="chart-value" x="' + centerX.toFixed(1) + '" y="' + (y - 9).toFixed(1) +
        '" text-anchor="middle">' + item.value + "</text>";
    }
    svg += '<text class="chart-axis" x="' + centerX.toFixed(1) +
      '" y="' + (H - 12) + '" text-anchor="middle">' + esc(item.label) + "</text>";
  });

  container.innerHTML = svg + "</svg>";
  attachTips(container);
}

/* items: [{ label, value, color, detail }]   options: { centerLabel } */
function donutChart(container, items, options) {
  const total = items.reduce((sum, i) => sum + i.value, 0);
  if (total === 0) {
    emptyChart(container, "No data yet.");
    return;
  }

  const radius = 70;
  const circumference = 2 * Math.PI * radius;
  let svg = '<svg class="donut-svg" viewBox="0 0 200 200" role="img" aria-label="' +
    esc((options && options.ariaLabel) || "Donut chart") + '">';
  svg += '<circle cx="100" cy="100" r="' + radius + '" fill="none" style="stroke:var(--line)" stroke-width="26"/>';

  let offset = 0;
  for (const item of items) {
    if (item.value === 0) continue;
    const length = (item.value / total) * circumference;
    svg += '<circle cx="100" cy="100" r="' + radius + '" fill="none" stroke-width="26" ' +
      'stroke-dasharray="' + length.toFixed(2) + " " + (circumference - length).toFixed(2) +
      '" stroke-dashoffset="' + (-offset).toFixed(2) + '" transform="rotate(-90 100 100)" ' +
      'style="stroke:' + item.color + '" data-tip="' + esc(item.label + ": " + item.detail) + '"/>';
    offset += length;
  }

  svg += '<text class="donut-total" x="100" y="102" text-anchor="middle">' + total + "</text>";
  svg += '<text class="donut-sub" x="100" y="120" text-anchor="middle">' +
    esc((options && options.centerLabel) || "total") + "</text>";
  svg += "</svg>";

  let legend = '<ul class="legend-list">';
  for (const item of items) {
    const percent = Math.round((item.value / total) * 100);
    legend += '<li><span class="legend-name"><i class="dot" style="background:' + item.color + '"></i>' +
      esc(item.label) + '</span><span class="legend-value">' + percent + "% &middot; " +
      esc(item.detail) + "</span></li>";
  }
  legend += "</ul>";

  container.innerHTML = '<div class="donut-wrap">' + svg + legend + "</div>";
  attachTips(container);
}