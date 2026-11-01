export const THEME_STORAGE_KEY = "document-hub.theme.v1";
export const THEME_MODES = ["system", "light", "dark"];

export function normalizeThemeMode(mode) {
  return THEME_MODES.includes(mode) ? mode : "system";
}

export function resolveTheme(mode, systemPrefersDark) {
  const preference = normalizeThemeMode(mode);
  return preference === "system"
    ? systemPrefersDark ? "dark" : "light"
    : preference;
}

export function nextThemeMode(mode) {
  const preference = normalizeThemeMode(mode);
  return THEME_MODES[(THEME_MODES.indexOf(preference) + 1) % THEME_MODES.length];
}
