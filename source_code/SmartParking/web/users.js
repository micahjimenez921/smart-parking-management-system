/* Users page (administrator): list every account, add one of any role, reset a password, delete.
   Uses helpers from app.js (make, getJson, currentView) and flows.js (postForm, setMessage). */

const usersState = { users: [], resetting: null, confirmDelete: null, notice: null, loaded: false };

const DETAIL_SHIFTS = ["DAY", "EVENING", "NIGHT"];

async function loadUsersPage(force) {
  if (!canUse("USERS")) return;

  try {
    const data = await getJson("/api/users");
    usersState.users = data.users;
    usersState.loaded = true;
    /* the automatic refresh must not close a reset panel or wipe what someone is typing */
    if (!force && (usersState.resetting || usersFormBusy())) return;
    renderUsersList();
  } catch (error) {
    if (!usersState.loaded) {
      const card = document.getElementById("users-list");
      card.innerHTML = "";
      card.appendChild(make("h2", "", "Accounts"));
      card.appendChild(make("p", "empty", "Could not load the accounts. Is the SmartPark server running?"));
    }
  }
}

function usersFormBusy() {
  const form = document.getElementById("users-form");
  if (!form) return false;
  return ["new-user-name", "new-user-username", "new-user-password", "new-user-detail-text"].some(id => {
    const input = document.getElementById(id);
    return input && input.value !== "";
  });
}

/* ---------- the list ---------- */

function renderUsersList() {
  const card = document.getElementById("users-list");
  card.innerHTML = "";
  card.appendChild(make("h2", "", "Accounts (" + usersState.users.length + ")"));

  if (usersState.notice) card.appendChild(usersNoticeBox(usersState.notice));
  if (usersState.resetting) card.appendChild(usersResetPanel(usersState.resetting));

  const wrap = make("div", "table-wrap");
  const table = make("table", "table");
  const headRow = make("tr");
  for (const title of ["Account", "Role", ""]) headRow.appendChild(make("th", "", title));
  table.appendChild(make("thead")).appendChild(headRow);

  const body = make("tbody");
  for (const user of usersState.users) body.appendChild(usersRow(user));
  table.appendChild(body);
  wrap.appendChild(table);
  card.appendChild(wrap);
}

function usersDetailText(user) {
  if (user.role === "ATTENDANT") return (SHIFT_LABEL[user.detail] || user.detail);
  if (user.role === "DRIVER") return user.detail ? user.detail.split(";").join(", ") : "No plates yet";
  return "";
}

function usersRow(user) {
  const isMe = session.user && session.user.id === user.id;
  const line = make("tr");
  line.dataset.username = user.username;

  const name = make("td", "users-who");
  const top = make("b", "", user.name);
  if (isMe) top.appendChild(make("span", "pill free users-you", "You"));
  name.appendChild(top);
  name.appendChild(make("small", "muted", user.username));
  line.appendChild(name);

  const role = make("td", "users-role");
  role.appendChild(make("span", "", ROLE_LABEL[user.role] || user.role));
  const detail = usersDetailText(user);
  if (detail) role.appendChild(make("small", "muted", detail));
  line.appendChild(role);

  const cell = make("td");
  const actions = make("div", "users-actions");
  cell.appendChild(actions);
  if (!isMe) {
    const reset = make("button", "btn-small", "Reset password");
    reset.setAttribute("type", "button");
    reset.addEventListener("click", () => {
      usersState.resetting = user;
      usersState.notice = null;
      renderUsersList();
      const input = document.getElementById("reset-password");
      if (input) input.focus();
    });
    actions.appendChild(reset);

    const confirming = usersState.confirmDelete === user.id;
    const del = make("button", "btn-small danger", confirming ? "Click again to delete" : "Delete");
    del.setAttribute("type", "button");
    del.addEventListener("click", () => {
      if (!confirming) {
        usersState.confirmDelete = user.id;
        renderUsersList();
        /* an unconfirmed delete quietly cancels itself */
        setTimeout(() => {
          if (usersState.confirmDelete === user.id) {
            usersState.confirmDelete = null;
            renderUsersList();
          }
        }, 4000);
      } else {
        deleteUser(user);
      }
    });
    actions.appendChild(del);
  }
  line.appendChild(cell);
  return line;
}

async function deleteUser(user) {
  usersState.confirmDelete = null;
  try {
    await postForm("/api/users/remove", { id: user.id });
    usersState.notice = { kind: "success", text: "Deleted the account " + user.username + "." };
  } catch (error) {
    usersState.notice = { kind: "error", text: error.message };
  }
  await loadUsersPage(true);
}

/* a message above the table; a new password is shown here once, and never again */
function usersNoticeBox(notice) {
  const box = make("div", "form-msg " + notice.kind);
  box.id = "users-notice";
  box.appendChild(make("span", "", notice.text));
  if (notice.password) {
    const secret = make("div", "users-secret");
    secret.appendChild(make("code", "", notice.password));
    box.appendChild(secret);
    box.appendChild(make("small", "", "Write it down and give it to the person now. It is not shown again."));
  }
  return box;
}

/* ---------- reset a password ---------- */

function usersResetPanel(user) {
  const form = make("form", "form users-reset");
  form.id = "users-reset-form";
  form.setAttribute("autocomplete", "off");
  form.setAttribute("novalidate", "");

  form.appendChild(make("h3", "", "New password for " + user.name + " (" + user.username + ")"));

  const label = make("label", "field");
  label.appendChild(make("span", "", "New password (leave empty to generate one)"));
  const input = make("input");
  input.id = "reset-password";
  input.setAttribute("type", "text");
  input.setAttribute("autocomplete", "off");
  label.appendChild(input);
  form.appendChild(label);

  const message = make("div", "form-msg");
  message.hidden = true;
  form.appendChild(message);

  const buttons = make("div", "users-buttons");
  const save = make("button", "btn", "Set password");
  save.setAttribute("type", "submit");
  const cancel = make("button", "btn-ghost", "Cancel");
  cancel.setAttribute("type", "button");
  cancel.addEventListener("click", () => {
    usersState.resetting = null;
    renderUsersList();
  });
  buttons.appendChild(save);
  buttons.appendChild(cancel);
  form.appendChild(buttons);

  form.addEventListener("submit", async event => {
    event.preventDefault();
    save.disabled = true;
    try {
      const result = await postForm("/api/users/reset", { id: user.id, password: input.value });
      usersState.resetting = null;
      usersState.notice = {
        kind: "success",
        text: "Password changed for " + user.username + ". They were signed out everywhere.",
        password: result.generatedPassword || null
      };
      renderUsersList();
    } catch (error) {
      setMessage(message, error.message, "error");
      save.disabled = false;
    }
  });
  return form;
}

/* ---------- add an account ---------- */

function renderUsersForm() {
  const card = document.getElementById("users-add");
  card.innerHTML = "";
  card.appendChild(make("h2", "", "Add an account"));
  card.appendChild(make("p", "muted", "Leave the password empty to generate one."));

  const form = make("form", "form");
  form.id = "users-form";
  form.setAttribute("autocomplete", "off");
  form.setAttribute("novalidate", "");

  const roleLabel = make("label", "field");
  roleLabel.appendChild(make("span", "", "Role"));
  const role = make("select", "select users-select");
  role.id = "new-user-role";
  for (const key of ["ATTENDANT", "DRIVER", "ADMINISTRATOR"]) {
    const option = make("option", "", ROLE_LABEL[key]);
    option.value = key;
    role.appendChild(option);
  }
  roleLabel.appendChild(role);
  form.appendChild(roleLabel);

  form.appendChild(usersField("Full name", "new-user-name", "text", ""));
  form.appendChild(usersField("Username", "new-user-username", "text", "letters, numbers, . _ -"));
  form.appendChild(usersField("Password", "new-user-password", "text", "at least 8 characters, or leave empty"));

  /* the last field depends on the role: a shift for an attendant, plate numbers for a driver */
  const detailBox = make("div", "users-detail");
  detailBox.id = "users-detail-box";
  form.appendChild(detailBox);

  function showDetail() {
    detailBox.innerHTML = "";
    if (role.value === "ATTENDANT") {
      const label = make("label", "field");
      label.appendChild(make("span", "", "Shift"));
      const shift = make("select", "select users-select");
      shift.id = "new-user-detail-select";
      for (const key of DETAIL_SHIFTS) {
        const option = make("option", "", SHIFT_LABEL[key]);
        option.value = key;
        shift.appendChild(option);
      }
      label.appendChild(shift);
      detailBox.appendChild(label);
    } else if (role.value === "DRIVER") {
      detailBox.appendChild(usersField("Plate numbers (optional, separate with ;)", "new-user-detail-text", "text", "e.g. ABC 123; XYZ 789"));
    }
  }
  role.addEventListener("change", showDetail);
  showDetail();

  const message = make("div", "form-msg");
  message.id = "users-msg";
  message.hidden = true;
  form.appendChild(message);

  const submit = make("button", "btn", "Create account");
  submit.setAttribute("type", "submit");
  form.appendChild(submit);

  form.addEventListener("submit", async event => {
    event.preventDefault();
    setMessage(message, "", "");

    const name = document.getElementById("new-user-name").value.trim();
    const username = document.getElementById("new-user-username").value.trim();
    if (name === "" || username === "") {
      setMessage(message, "Enter the full name and the username.", "error");
      return;
    }

    const select = document.getElementById("new-user-detail-select");
    const text = document.getElementById("new-user-detail-text");
    submit.disabled = true;
    try {
      const result = await postForm("/api/users/add", {
        role: role.value,
        name: name,
        username: username,
        password: document.getElementById("new-user-password").value,
        detail: select ? select.value : (text ? text.value : "")
      });
      usersState.notice = {
        kind: "success",
        text: "Created the " + (ROLE_LABEL[result.user.role] || "").toLowerCase() + " account " + result.user.username + ".",
        password: result.generatedPassword || null
      };
      usersState.resetting = null;
      form.reset();
      showDetail();
      await loadUsersPage(true);
    } catch (error) {
      setMessage(message, error.message, "error");
    } finally {
      submit.disabled = false;
    }
  });

  card.appendChild(form);
}

function usersField(title, id, type, hint) {
  const label = make("label", "field");
  label.appendChild(make("span", "", title));
  const input = make("input");
  input.id = id;
  input.setAttribute("type", type);
  input.setAttribute("autocomplete", "off");
  input.setAttribute("autocapitalize", "none");
  input.setAttribute("spellcheck", "false");
  if (hint) input.setAttribute("placeholder", hint);
  label.appendChild(input);
  return label;
}

/* ---------- starting and refreshing ---------- */

function startUsersPage() {
  if (!canUse("USERS")) return;
  renderUsersForm();
  loadUsersPage(true);
}

window.addEventListener("hashchange", () => {
  if (currentView() === "users") loadUsersPage(true);
});

setInterval(() => {
  if (currentView() === "users") loadUsersPage(false);
}, 8000);
