/* Sign-in page. Sends the username and password, then opens the main page. */

const form = document.getElementById("login-form");
const message = document.getElementById("login-msg");
const submit = document.getElementById("login-submit");

function showMessage(text) {
  message.hidden = !text;
  message.className = "form-msg " + (text ? "error" : "");
  message.textContent = text || "";
}

/* the same light or dark choice as the main page */
try {
  document.documentElement.dataset.theme =
    localStorage.getItem("smartpark-theme") === "dark" ? "dark" : "light";
} catch (error) {
  document.documentElement.dataset.theme = "light";
}

async function signIn(event) {
  event.preventDefault();
  showMessage("");

  const username = document.getElementById("login-username").value.trim();
  const password = document.getElementById("login-password").value;
  if (username === "" || password === "") {
    showMessage("Enter your username and password.");
    return;
  }

  submit.disabled = true;
  submit.textContent = "Signing in...";

  try {
    const response = await fetch("/api/login", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
      body: new URLSearchParams({ username: username, password: password }).toString()
    });

    let body = {};
    try {
      body = await response.json();
    } catch (error) {
      /* the server sent no JSON */
    }

    if (!response.ok) {
      showMessage(body.error || "Could not sign in (" + response.status + ").");
      document.getElementById("login-password").value = "";
      document.getElementById("login-password").focus();
      return;
    }

    location.replace("index.html");
  } catch (error) {
    showMessage("Cannot reach the SmartPark server. Is it still running?");
  } finally {
    submit.disabled = false;
    submit.textContent = "Sign in";
  }
}

form.addEventListener("submit", signIn);

/* ---------- creating a driver account ---------- */

const registerForm = document.getElementById("register-form");
const registerMessage = document.getElementById("register-msg");
const registerSubmit = document.getElementById("register-submit");
let registering = false;

function showRegisterMessage(text) {
  registerMessage.hidden = !text;
  registerMessage.className = "form-msg " + (text ? "error" : "");
  registerMessage.textContent = text || "";
}

/* swaps between the sign-in form and the new driver form */
function showMode(createAccount) {
  registering = createAccount;
  form.hidden = createAccount;
  registerForm.hidden = !createAccount;
  document.getElementById("login-title").textContent = createAccount ? "Create a driver account" : "Sign in";
  document.getElementById("login-lead").textContent = createAccount
    ? "Park, see your fee, and pay online. Staff accounts are made by an administrator."
    : "Use your SmartPark account to continue.";
  document.getElementById("switch-text").textContent = createAccount ? "Already have an account?" : "New driver?";
  document.getElementById("switch-button").textContent = createAccount ? "Sign in" : "Create a driver account";
  showMessage("");
  showRegisterMessage("");
  document.getElementById(createAccount ? "reg-name" : "login-username").focus();
}

async function createAccount(event) {
  event.preventDefault();
  showRegisterMessage("");

  const name = document.getElementById("reg-name").value.trim();
  const username = document.getElementById("reg-username").value.trim();
  const password = document.getElementById("reg-password").value;
  const again = document.getElementById("reg-again").value;
  const plate = document.getElementById("reg-plate").value.trim();

  if (name === "" || username === "" || password === "") {
    showRegisterMessage("Enter your name, a username and a password.");
    return;
  }
  if (password !== again) {
    showRegisterMessage("The two passwords are not the same.");
    return;
  }

  registerSubmit.disabled = true;
  registerSubmit.textContent = "Creating...";

  try {
    const response = await fetch("/api/register", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
      body: new URLSearchParams({
        name: name, username: username, password: password, again: again, plate: plate
      }).toString()
    });

    let body = {};
    try {
      body = await response.json();
    } catch (error) {
      /* the server sent no JSON */
    }

    if (!response.ok) {
      showRegisterMessage(body.error || "Could not create the account (" + response.status + ").");
      return;
    }
    location.replace("index.html");
  } catch (error) {
    showRegisterMessage("Cannot reach the SmartPark server. Is it still running?");
  } finally {
    registerSubmit.disabled = false;
    registerSubmit.textContent = "Create account";
  }
}

registerForm.addEventListener("submit", createAccount);
document.getElementById("switch-button").addEventListener("click", () => showMode(!registering));

/* someone who is already signed in goes straight to the app */
fetch("/api/me")
  .then(response => {
    if (response.ok) location.replace("index.html");
  })
  .catch(() => { /* the server is not reachable; the form still shows its own message on submit */ });
