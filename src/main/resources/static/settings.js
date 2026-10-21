const SETTINGS_API = "/api/settings";

const DEFAULT_SETTINGS = {
  application_name: "Em-Ployger",
  company_name: "Em-Ployger Company",
  currency_code: "PHP",
  currency_symbol: "₱",
  default_overtime_rate: "1.25",
  factor_rate: "261",
  late_deduction_amount: "0.00",
  absent_deduction_amount: "0.00",
  standard_start_time: "08:00",
  standard_end_time: "17:00",
  unpaid_break_minutes: "60",
  regular_hours_per_day: "8",
  late_grace_period_minutes: "0",
  night_differential_rate: "0.10",
  night_differential_start: "22:00",
  night_differential_end: "06:00",
};

const settingsForm = document.getElementById("settingsForm");
const settingsMessage = document.getElementById("settingsMessage");
const saveSettingsButton = document.getElementById("saveSettingsButton");
const reloadSettingsButton = document.getElementById("reloadSettingsButton");
const logoutButton = document.getElementById("logoutButton");

let savedSettings = {};
let loadingSettings = false;

function showSettingsMessage(message, type = "info") {
  settingsMessage.textContent = message;
  settingsMessage.className = `settings-message show ${type}`;
}

function clearSettingsMessage() {
  settingsMessage.textContent = "";
  settingsMessage.className = "settings-message";
}

function setFormValues(settings) {
  for (const [name, defaultValue] of Object.entries(DEFAULT_SETTINGS)) {
    const input = document.getElementById(name);

    if (input) {
      input.value = settings[name] ?? defaultValue;
    }
  }
}

function normalizeSettingsResponse(data) {
  // Expected response: [{ settingName: "...", settingValue: "..." }, ...]
  if (Array.isArray(data)) {
    return Object.fromEntries(
      data
        .filter((item) => item && item.settingName != null)
        .map((item) => [item.settingName, String(item.settingValue ?? "")]),
    );
  }

  // Support a response wrapped in a content or data array.
  if (data && Array.isArray(data.content)) {
    return normalizeSettingsResponse(data.content);
  }

  if (data && Array.isArray(data.data)) {
    return normalizeSettingsResponse(data.data);
  }

  // Also support a direct object keyed by setting name.
  if (data && typeof data === "object") {
    if (data.settingName != null) {
      return {
        [data.settingName]: String(data.settingValue ?? ""),
      };
    }

    return Object.fromEntries(
      Object.entries(data).map(([key, value]) => {
        if (
          value &&
          typeof value === "object" &&
          value.settingValue !== undefined
        ) {
          return [key, String(value.settingValue)];
        }

        return [key, String(value ?? "")];
      }),
    );
  }

  throw new Error("The settings API returned an unsupported response.");
}

async function readError(response) {
  const text = await response.text();

  if (!text) {
    return `Request failed with status ${response.status}.`;
  }

  try {
    const data = JSON.parse(text);
    return data.message || data.error || data.detail || text;
  } catch {
    return text;
  }
}

async function loadSettings() {
  if (loadingSettings) return;

  loadingSettings = true;
  clearSettingsMessage();
  reloadSettingsButton.disabled = true;
  saveSettingsButton.disabled = true;

  try {
    const response = await fetch(SETTINGS_API, {
      method: "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
      },
    });

    if (!response.ok) {
      throw new Error(await readError(response));
    }

    const data = await response.json();
    savedSettings = normalizeSettingsResponse(data);

    // Fill known settings, using defaults only when a key is absent.
    setFormValues({
      ...DEFAULT_SETTINGS,
      ...savedSettings,
    });

    showSettingsMessage("Saved settings loaded successfully.", "success");
  } catch (error) {
    // Show defaults so the page remains usable, but don't imply
    // they were retrieved from the database.
    setFormValues({
      ...DEFAULT_SETTINGS,
      ...savedSettings,
    });

    showSettingsMessage(
      `Could not load settings from the server: ${error.message}`,
      "error",
    );
  } finally {
    loadingSettings = false;
    reloadSettingsButton.disabled = false;
    saveSettingsButton.disabled = false;
  }
}

function collectFormValues() {
  const values = {};

  for (const name of Object.keys(DEFAULT_SETTINGS)) {
    const input = document.getElementById(name);

    if (input) {
      values[name] = input.value.trim();
    }
  }

  return values;
}

function validateSettings(values) {
  const positiveNumbers = [
    ["default_overtime_rate", "Overtime multiplier", 0.01, 10],
    ["factor_rate", "Annual salary divisor", 1, 366],
    ["regular_hours_per_day", "Regular hours per day", 1, 24],
  ];

  for (const [key, label, min, max] of positiveNumbers) {
    const value = Number(values[key]);

    if (!Number.isFinite(value) || value < min || value > max) {
      return `${label} must be between ${min} and ${max}.`;
    }
  }

  const nonNegativeNumbers = [
    ["late_deduction_amount", "Legacy late deduction"],
    ["absent_deduction_amount", "Legacy absence deduction"],
    ["unpaid_break_minutes", "Unpaid break"],
    ["late_grace_period_minutes", "Late grace period"],
    ["night_differential_rate", "Night differential rate"],
  ];

  for (const [key, label] of nonNegativeNumbers) {
    const value = Number(values[key]);

    if (!Number.isFinite(value) || value < 0) {
      return `${label} must be zero or greater.`;
    }
  }

  if (Number(values.unpaid_break_minutes) > 360) {
    return "Unpaid break cannot exceed 360 minutes.";
  }

  if (Number(values.late_grace_period_minutes) > 120) {
    return "Late grace period cannot exceed 120 minutes.";
  }

  if (Number(values.night_differential_rate) > 5) {
    return "Night differential rate cannot exceed 5.00.";
  }

  const requiredText = [
    ["application_name", "Application name"],
    ["company_name", "Company name"],
    ["currency_code", "Currency code"],
    ["currency_symbol", "Currency symbol"],
    ["standard_start_time", "Standard start time"],
    ["standard_end_time", "Standard end time"],
    ["night_differential_start", "Night differential start time"],
    ["night_differential_end", "Night differential end time"],
  ];

  for (const [key, label] of requiredText) {
    if (!values[key]) {
      return `${label} is required.`;
    }
  }

  if (!/^[A-Z]{3}$/.test(values.currency_code)) {
    return "Currency code must contain three uppercase letters.";
  }

  if (values.standard_start_time === values.standard_end_time) {
    return "Standard start and end times cannot be identical.";
  }

  if (
    Number(values.unpaid_break_minutes) >=
    Number(values.regular_hours_per_day) * 60
  ) {
    return "The unpaid break must be shorter than the regular workday.";
  }

  return null;
}

async function saveOneSetting(name, value) {
  const response = await fetch(`${SETTINGS_API}/${encodeURIComponent(name)}`, {
    method: "PUT",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({
      settingValue: value,
    }),
  });

  if (!response.ok) {
    throw new Error(`${name}: ${await readError(response)}`);
  }
}

settingsForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearSettingsMessage();

  const values = collectFormValues();
  const validationError = validateSettings(values);

  if (validationError) {
    showSettingsMessage(validationError, "error");
    return;
  }

  saveSettingsButton.disabled = true;
  reloadSettingsButton.disabled = true;
  saveSettingsButton.textContent = "Saving...";

  const changedSettings = Object.entries(values).filter(
    ([name, value]) => String(savedSettings[name] ?? "") !== value,
  );

  if (changedSettings.length === 0) {
    showSettingsMessage("There are no changes to save.", "info");
    saveSettingsButton.disabled = false;
    reloadSettingsButton.disabled = false;
    saveSettingsButton.textContent = "Save Settings";
    return;
  }

  let savedCount = 0;

  try {
    for (const [name, value] of changedSettings) {
      await saveOneSetting(name, value);
      savedSettings[name] = value;
      savedCount++;
    }

    showSettingsMessage(
      `Successfully saved ${savedCount} setting(s).`,
      "success",
    );
  } catch (error) {
    showSettingsMessage(
      `${savedCount} setting(s) saved before an error occurred. ` +
        `Reload the page before retrying. Details: ${error.message}`,
      "error",
    );
  } finally {
    saveSettingsButton.disabled = false;
    reloadSettingsButton.disabled = false;
    saveSettingsButton.textContent = "Save Settings";
  }
});

reloadSettingsButton.addEventListener("click", loadSettings);

if (logoutButton) {
  logoutButton.addEventListener("click", async () => {
    logoutButton.disabled = true;

    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "same-origin",
      });
    } finally {
      window.location.href = "/index.html";
    }
  });
}

loadSettings();
