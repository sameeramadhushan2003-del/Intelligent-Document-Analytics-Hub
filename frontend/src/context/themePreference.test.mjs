import test from "node:test";
import assert from "node:assert/strict";
import { nextThemeMode, normalizeThemeMode, resolveTheme } from "./themePreference.js";

test("resolves system preference and explicit modes", () => {
  assert.equal(resolveTheme("system", true), "dark");
  assert.equal(resolveTheme("system", false), "light");
  assert.equal(resolveTheme("dark", false), "dark");
  assert.equal(resolveTheme("light", true), "light");
});

test("normalizes invalid stored preferences and cycles through all modes", () => {
  assert.equal(normalizeThemeMode("unknown"), "system");
  assert.deepEqual(["system", "light", "dark"].map(nextThemeMode), ["light", "dark", "system"]);
});
