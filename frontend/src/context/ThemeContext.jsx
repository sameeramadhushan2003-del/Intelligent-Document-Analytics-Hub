import { useCallback, useEffect, useMemo, useState } from "react";
import { THEME_STORAGE_KEY, nextThemeMode, normalizeThemeMode, resolveTheme } from "./themePreference.js";
import { ThemeContext } from "./themeContext.js";

function getInitialMode() {
  try {
    return normalizeThemeMode(window.localStorage.getItem(THEME_STORAGE_KEY));
  } catch {
    return "system";
  }
}

export function ThemeProvider({ children }) {
  const [mode, setMode] = useState(getInitialMode);
  const [systemPrefersDark, setSystemPrefersDark] = useState(
    () => window.matchMedia("(prefers-color-scheme: dark)").matches
  );
  const theme = resolveTheme(mode, systemPrefersDark);

  useEffect(() => {
    const preference = window.matchMedia("(prefers-color-scheme: dark)");
    const updateSystemPreference = (event) => setSystemPrefersDark(event.matches);
    preference.addEventListener("change", updateSystemPreference);
    return () => preference.removeEventListener("change", updateSystemPreference);
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = theme;
    root.dataset.themeMode = mode;
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, mode);
    } catch {
      // The selected theme remains active for this page when storage is unavailable.
    }
  }, [mode, theme]);

  const cycleTheme = useCallback(() => {
    setMode((current) => nextThemeMode(current));
  }, []);

  const value = useMemo(() => ({ mode, theme, cycleTheme }), [mode, theme, cycleTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
