import { nextThemeMode } from "../context/themePreference.js";
import { useTheme } from "../context/useTheme.js";
import Icon from "./Icon.jsx";

const themeLabels = {
  system: "system preference",
  light: "light",
  dark: "dark",
};

const themeIcons = {
  system: "system",
  light: "sun",
  dark: "moon",
};

export default function ThemeToggle() {
  const { mode, theme, cycleTheme } = useTheme();
  const nextMode = nextThemeMode(mode);

  return (
    <button
      className="theme-toggle"
      type="button"
      onClick={cycleTheme}
      aria-label={`Theme: ${themeLabels[mode]} (${theme}). Switch to ${themeLabels[nextMode]}.`}
      title={`Theme: ${themeLabels[mode]}`}
    >
      <Icon name={themeIcons[mode]} size={17} />
      <span>{mode === "system" ? "Auto" : mode === "light" ? "Light" : "Dark"}</span>
    </button>
  );
}
