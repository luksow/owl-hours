import "./main.css";
import { registerSW } from "virtual:pwa-register";

// The app shows its own "new version" toast; it calls window.ohApplyUpdate() when the user taps Reload.
const applyUpdate = registerSW({
  onNeedRefresh() {
    window.dispatchEvent(new CustomEvent("oh-update-ready"));
  },
});
window.ohApplyUpdate = () => applyUpdate(true);

import "scalajs:main.js";
