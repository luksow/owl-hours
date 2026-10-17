/**
 * iPhone portrait screens: CSS width and height, device pixel ratio. iOS shows the launch image whose media query
 * matches exactly; a screen not listed opens on the manifest's background colour. Phones only: the app is portrait.
 */
export const splashScreens = [
  { w: 440, h: 956, r: 3 }, // 16 Pro Max, 17 Pro Max
  { w: 430, h: 932, r: 3 }, // 14 Pro Max, 15 Plus, 15 Pro Max, 16 Plus
  { w: 428, h: 926, r: 3 }, // 12 Pro Max, 13 Pro Max, 14 Plus
  { w: 420, h: 912, r: 3 }, // Air
  { w: 414, h: 896, r: 3 }, // XS Max, 11 Pro Max
  { w: 414, h: 896, r: 2 }, // XR, 11
  { w: 402, h: 874, r: 3 }, // 16 Pro, 17, 17 Pro
  { w: 393, h: 852, r: 3 }, // 14 Pro, 15, 15 Pro, 16
  { w: 390, h: 844, r: 3 }, // 12, 13, 14, 12 Pro, 13 Pro, 16e
  { w: 375, h: 812, r: 3 }, // X, XS, 11 Pro, 12 mini, 13 mini
  { w: 375, h: 667, r: 2 }, // SE (2nd and 3rd generation), 8
];

export const splashFile = (s) => `splash/apple-splash-${s.w * s.r}x${s.h * s.r}.png`;

export const splashMedia = (s) =>
  `(device-width: ${s.w}px) and (device-height: ${s.h}px) and (-webkit-device-pixel-ratio: ${s.r}) and (orientation: portrait)`;
