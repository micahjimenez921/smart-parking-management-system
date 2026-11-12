/* The notification bell: a badge with the number of unread alerts, and a list that opens under it.
   An alert counts as read once the list has been opened and closed again; this is remembered per account
   in this browser. Uses helpers from app.js (make, getJson) and the signed-in person from session.js. */

const notifyState = { items: [], read: new Set(), open: false, started: false };

function notifyKey() {
  return "smartpark-read-" + (session.user ? session.user.id : "");
}

function loadReadList() {
  try {
    const saved = JSON.parse(localStorage.getItem(notifyKey()) || "[]");
    notifyState.read = new Set(Array.isArray(saved) ? saved : []);
  } catch (error) {
    notifyState.read = new Set(); /* storage can be blocked; the bell still works for this visit */
  }
}

function saveReadList() {
  try {
    localStorage.setItem(notifyKey(), JSON.stringify(Array.from(notifyState.read).slice(-300)));
  } catch (error) {
    /* not remembered, no harm */
  }
}

async function loadNotifications() {
  if (!session.user) return;
  try {
    const data = await getJson("/api/notifications");
    notifyState.items = data.items;
    renderBell();
  } catch (error) {
    /* the offline banner already tells the person; keep the last list */
  }
}

function unreadItems() {
  return notifyState.items.filter(item => !notifyState.read.has(item.id));
}

function renderBell() {
  const unread = unreadItems().length;
  const badge = document.getElementById("bell-count");
  badge.hidden = unread === 0;
  badge.textContent = unread > 9 ? "9+" : String(unread);
  document.getElementById("bell-button").setAttribute("aria-label",
    unread > 0 ? "Notifications, " + unread + " unread" : "Notifications");

  if (notifyState.open) renderPanel();
}

function timeAgo(item) {
  if (item.id.startsWith("live:")) return "Now";
  const minutes = Math.round((Date.now() - new Date(item.time).getTime()) / 60000);
  if (minutes < 1) return "Just now";
  if (minutes < 60) return minutes + " min ago";
  const hours = Math.floor(minutes / 60);
  return hours < 24 ? hours + " h ago" : Math.floor(hours / 24) + " d ago";
}

function renderPanel() {
  const panel = document.getElementById("bell-panel");
  panel.innerHTML = "";

  const head = make("div", "bell-head");
  head.appendChild(make("b", "", "Notifications"));
  const markAll = make("button", "link-button", "Mark all as read");
  markAll.setAttribute("type", "button");
  markAll.disabled = unreadItems().length === 0;
  markAll.addEventListener("click", () => {
    for (const item of notifyState.items) notifyState.read.add(item.id);
    saveReadList();
    renderBell();
  });
  head.appendChild(markAll);
  panel.appendChild(head);

  if (notifyState.items.length === 0) {
    panel.appendChild(make("p", "bell-empty", "You are all caught up."));
    return;
  }

  const list = make("ul", "bell-list");
  for (const item of notifyState.items) {
    const line = make("li", "bell-item " + item.level + (notifyState.read.has(item.id) ? "" : " unread"));
    line.appendChild(make("span", "bell-mark"));
    const body = make("div", "bell-body");
    body.appendChild(make("b", "", item.title));
    body.appendChild(make("span", "", item.text));
    body.appendChild(make("small", "muted", timeAgo(item)));
    line.appendChild(body);
    list.appendChild(line);
  }
  panel.appendChild(list);
}

function setBellOpen(open) {
  if (open === notifyState.open) return;
  notifyState.open = open;
  document.getElementById("bell-panel").hidden = !open;
  document.getElementById("bell-button").setAttribute("aria-expanded", String(open));

  if (open) {
    loadNotifications();
    renderPanel();
  } else {
    /* what was on the list while it was open counts as seen */
    for (const item of notifyState.items) notifyState.read.add(item.id);
    saveReadList();
    renderBell();
  }
}

function startNotifications() {
  if (notifyState.started) return;
  notifyState.started = true;
  loadReadList();

  document.getElementById("bell-button").addEventListener("click", event => {
    event.stopPropagation();
    setBellOpen(!notifyState.open);
  });
  document.addEventListener("click", event => {
    if (notifyState.open && !document.getElementById("bell-panel").contains(event.target)) setBellOpen(false);
  });
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && notifyState.open) {
      setBellOpen(false);
      document.getElementById("bell-button").focus();
    }
  });

  loadNotifications();
  setInterval(loadNotifications, 10000);
}
