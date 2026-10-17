import "./main.css";
import { registerSW } from "virtual:pwa-register";

// The app shows its own "new version" toast; it calls window.ohApplyUpdate() when the user taps Reload.
const applyUpdate = registerSW({
  onNeedRefresh() {
    window.dispatchEvent(new CustomEvent("oh-update-ready"));
  },
});
window.ohApplyUpdate = () => applyUpdate(true);

// Install (spec §10): Chromium offers this once the app is installable, possibly before the Scala app has mounted, so
// it is kept here. The app reads ohInstallReady() and listens for "oh-install-changed".
let installEvent = null;
const installChanged = () => window.dispatchEvent(new CustomEvent("oh-install-changed"));
window.addEventListener("beforeinstallprompt", (e) => {
  e.preventDefault();
  installEvent = e;
  installChanged();
});
window.addEventListener("appinstalled", () => {
  installEvent = null;
  installChanged();
});
window.ohInstallReady = () => installEvent !== null;
window.ohInstall = async () => {
  const e = installEvent;
  if (!e) return "unavailable";
  installEvent = null; // prompt() works once per event
  try {
    await e.prompt();
    const { outcome } = await e.userChoice;
    return outcome;
  } catch {
    return "failed"; // the event is spent either way, so the card must not keep a dead Install button
  } finally {
    installChanged();
  }
};

import "scalajs:main.js";
