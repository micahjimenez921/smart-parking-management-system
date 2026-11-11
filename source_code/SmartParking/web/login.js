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

/* someone who is already signed in goes straight to the app */
fetch("/api/me")
  .then(response => {
    if (response.ok) location.replace("index.html");
  })
  .catch(() => { /* the server is not reachable; the form still shows its own message on submit */ });
