// Benefits & Holidays page: holiday calendar, earnings entries, 13th month
// generation and previous-employer compensation.

const TYPE_LABELS = {
  THIRTEENTH_MONTH: "13th month pay",
  OTHER_BENEFIT: "Other benefit",
  HAZARD_PAY: "Hazard pay",
  TAXABLE_ALLOWANCE: "Taxable allowance",
  DE_MINIMIS_RICE: "Rice subsidy",
  DE_MINIMIS_CLOTHING: "Clothing allowance",
  DE_MINIMIS_MEDICAL: "Medical allowance",
  DE_MINIMIS_LEAVE: "Monetized leave",
};

const TYPE_HINTS = {
  THIRTEENTH_MONTH: "Tax-exempt with other benefits up to ₱90,000 a year.",
  OTHER_BENEFIT: "Christmas bonus, productivity incentive, loyalty award. Tax-exempt with 13th month pay up to ₱90,000 a year.",
  HAZARD_PAY: "Taxable, except for minimum wage earners.",
  TAXABLE_ALLOWANCE: "Always taxable, including for minimum wage earners.",
  DE_MINIMIS_RICE: "Exempt up to ₱2,000 a month; the excess counts toward the ₱90,000 benefits cap.",
  DE_MINIMIS_CLOTHING: "Exempt up to ₱6,000 a year; the excess counts toward the ₱90,000 benefits cap.",
  DE_MINIMIS_MEDICAL: "Exempt up to ₱1,500 per half-year; the excess counts toward the ₱90,000 benefits cap.",
  DE_MINIMIS_LEAVE: "Exempt up to 10 days' pay a year; the excess counts toward the ₱90,000 benefits cap.",
};

const HOLIDAY_LABELS = { REGULAR: "Regular", SPECIAL: "Special", DOUBLE: "Double" };

let employees = [];
let holidays = [];
let earnings = [];
let priorRecords = [];

const $ = (id) => document.getElementById(id);

document.addEventListener("DOMContentLoaded", () => {
  const thisYear = new Date().getFullYear();

  $("holidayYear").value = thisYear;
  $("thirteenthYear").value = thisYear;
  $("thirteenthDate").value = `${thisYear}-12-15`;

  $("holidayYear").addEventListener("change", loadHolidays);
  $("addHolidayButton").addEventListener("click", () => openHolidayModal(null));
  $("addEarningButton").addEventListener("click", () => openEarningModal(null));
  $("addPriorButton").addEventListener("click", () => openPriorModal(null));
  $("earningType").addEventListener("change", showTypeHint);

  $("holidayForm").addEventListener("submit", saveHoliday);
  $("earningForm").addEventListener("submit", saveEarning);
  $("priorForm").addEventListener("submit", savePrior);
  $("thirteenthForm").addEventListener("submit", generateThirteenthMonth);

  document.querySelectorAll("[data-close]").forEach((button) => {
    button.addEventListener("click", () => closeModal(button.dataset.close));
  });

  $("logoutButton").addEventListener("click", (event) => {
    event.preventDefault();
    logout();
  });

  loadEmployees().then(() => {
    loadHolidays();
    loadEarnings();
    loadPriorRecords();
  });
});

// ---------- requests ----------

// Fetches JSON; throws an Error with the server's message on failure.
async function api(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: options.body ? { "Content-Type": "application/json" } : undefined,
  });

  if (response.status === 401) {
    window.location.href = "/";
    throw new Error("Please log in.");
  }

  if (!response.ok) {
    const text = (await response.text()).trim();
    throw new Error(text || `Request failed (${response.status}).`);
  }

  return response.status === 204 ? null : response.json();
}

async function loadEmployees() {
  try {
    employees = await api("/api/employees");
  } catch (error) {
    employees = [];
  }

  const options = employees
    .slice()
    .sort((a, b) => `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`))
    .map((employee) => {
      const option = document.createElement("option");
      option.value = employee.employeeId;
      option.textContent = `${employee.lastName}, ${employee.firstName} (#${employee.employeeId})`;
      return option;
    });

  ["earningEmployee", "priorEmployee"].forEach((id) => {
    const select = $(id);
    select.innerHTML = "";
    options.forEach((option) => select.appendChild(option.cloneNode(true)));
  });
}

// ---------- holidays ----------

async function loadHolidays() {
  const body = $("holidayTableBody");

  try {
    holidays = await api(`/api/holidays?year=${encodeURIComponent($("holidayYear").value)}`);
  } catch (error) {
    setEmptyRow(body, 5, error.message);
    return;
  }

  if (holidays.length === 0) {
    setEmptyRow(body, 5, "No holidays entered for this year.");
    return;
  }

  body.innerHTML = "";

  holidays.forEach((holiday) => {
    const row = document.createElement("tr");
    const day = new Date(`${holiday.holidayDate}T00:00:00`).toLocaleDateString("en-PH", { weekday: "long" });

    row.innerHTML = `
      <td>${escapeHtml(holiday.holidayDate)}</td>
      <td>${escapeHtml(day)}</td>
      <td>${escapeHtml(holiday.name)}</td>
      <td><span class="badge badge-${holiday.holidayType.toLowerCase()}">${escapeHtml(HOLIDAY_LABELS[holiday.holidayType] || holiday.holidayType)}</span></td>
      <td>
        <button type="button" class="action-button edit-button">Edit</button>
        <button type="button" class="action-button delete-button">Delete</button>
      </td>`;

    row.querySelector(".edit-button").addEventListener("click", () => openHolidayModal(holiday));
    row.querySelector(".delete-button").addEventListener("click", () => deleteHoliday(holiday));

    body.appendChild(row);
  });
}

function openHolidayModal(holiday) {
  $("holidayModalTitle").textContent = holiday ? "Edit Holiday" : "Add Holiday";
  $("holidayId").value = holiday ? holiday.holidayId : "";
  $("holidayDate").value = holiday ? holiday.holidayDate : "";
  $("holidayName").value = holiday ? holiday.name : "";
  $("holidayType").value = holiday ? holiday.holidayType : "REGULAR";
  clearMessage("holidayMessage");
  openModal("holidayModal");
}

async function saveHoliday(event) {
  event.preventDefault();

  const id = $("holidayId").value;
  const body = JSON.stringify({
    holidayDate: $("holidayDate").value,
    name: $("holidayName").value.trim(),
    holidayType: $("holidayType").value,
  });

  try {
    await api(id ? `/api/holidays/${id}` : "/api/holidays", { method: id ? "PUT" : "POST", body });
    closeModal("holidayModal");
    await loadHolidays();
  } catch (error) {
    showError("holidayMessage", error.message);
  }
}

async function deleteHoliday(holiday) {
  if (!confirm(`Delete the holiday "${holiday.name}" on ${holiday.holidayDate}?`)) {
    return;
  }

  try {
    await api(`/api/holidays/${holiday.holidayId}`, { method: "DELETE" });
    await loadHolidays();
  } catch (error) {
    alert(error.message);
  }
}

// ---------- earnings ----------

async function loadEarnings() {
  const body = $("earningTableBody");

  try {
    earnings = await api("/api/earnings");
  } catch (error) {
    setEmptyRow(body, 7, error.message);
    return;
  }

  if (earnings.length === 0) {
    setEmptyRow(body, 7, "No earnings entered yet.");
    return;
  }

  body.innerHTML = "";

  earnings.forEach((earning) => {
    const row = document.createElement("tr");
    const locked = earning.payrollStatus && earning.payrollStatus !== "DRAFT";
    const paidBy = earning.payrollId
      ? `<span class="badge ${locked ? "badge-locked" : ""}">#${earning.payrollId} ${escapeHtml(earning.payrollStatus)}</span>`
      : "Not yet";

    row.innerHTML = `
      <td>${escapeHtml(earning.dateGiven)}</td>
      <td>${escapeHtml(employeeName(earning.employee))}</td>
      <td>${escapeHtml(TYPE_LABELS[earning.earningType] || earning.earningType)}</td>
      <td class="money">${formatMoney(earning.amount)}</td>
      <td>${escapeHtml(earning.description || "")}</td>
      <td>${paidBy}</td>
      <td>${locked ? "Locked" : `
        <button type="button" class="action-button edit-button">Edit</button>
        <button type="button" class="action-button delete-button">Delete</button>`}
      </td>`;

    if (!locked) {
      row.querySelector(".edit-button").addEventListener("click", () => openEarningModal(earning));
      row.querySelector(".delete-button").addEventListener("click", () => deleteEarning(earning));
    }

    body.appendChild(row);
  });
}

function openEarningModal(earning) {
  $("earningModalTitle").textContent = earning ? "Edit Earning" : "Add Earning";
  $("earningId").value = earning ? earning.earningId : "";
  $("earningEmployee").value = earning ? earning.employee.employeeId : ($("earningEmployee").value || "");
  $("earningType").value = earning ? earning.earningType : "OTHER_BENEFIT";
  $("earningAmount").value = earning ? earning.amount : "";
  $("earningDate").value = earning ? earning.dateGiven : new Date().toISOString().slice(0, 10);
  $("earningDescription").value = earning ? earning.description || "" : "";
  showTypeHint();
  clearMessage("earningMessage");
  openModal("earningModal");
}

function showTypeHint() {
  $("earningTypeHint").textContent = TYPE_HINTS[$("earningType").value] || "";
}

async function saveEarning(event) {
  event.preventDefault();

  const id = $("earningId").value;
  const body = JSON.stringify({
    employeeId: Number($("earningEmployee").value),
    earningType: $("earningType").value,
    amount: $("earningAmount").value,
    dateGiven: $("earningDate").value,
    description: $("earningDescription").value.trim(),
  });

  try {
    await api(id ? `/api/earnings/${id}` : "/api/earnings", { method: id ? "PUT" : "POST", body });
    closeModal("earningModal");
    await loadEarnings();
  } catch (error) {
    showError("earningMessage", error.message);
  }
}

async function deleteEarning(earning) {
  if (!confirm(`Delete this ${TYPE_LABELS[earning.earningType] || "earning"} of ${formatMoney(earning.amount)}?`)) {
    return;
  }

  try {
    await api(`/api/earnings/${earning.earningId}`, { method: "DELETE" });
    await loadEarnings();
  } catch (error) {
    alert(error.message);
  }
}

// ---------- 13th month ----------

async function generateThirteenthMonth(event) {
  event.preventDefault();

  const year = Number($("thirteenthYear").value);
  const dateGiven = $("thirteenthDate").value;

  if (!confirm(`Create or update 13th month pay for ${year}, dated ${dateGiven}, for every eligible employee?`)) {
    return;
  }

  const message = $("thirteenthMessage");
  message.textContent = "Calculating...";
  message.className = "form-message";

  try {
    const lines = await api("/api/earnings/thirteenth-month", {
      method: "POST",
      body: JSON.stringify({ year, dateGiven }),
    });

    const body = $("thirteenthTableBody");
    body.innerHTML = "";

    lines.forEach((line) => {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${escapeHtml(line.employeeName)}</td>
        <td class="money">${formatMoney(line.basicSalaryEarned)}</td>
        <td class="money">${formatMoney(line.thirteenthMonthPay)}</td>
        <td class="money">${formatMoney(line.alreadyPaid)}</td>
        <td class="money">${formatMoney(line.amountEntered)}</td>
        <td>${escapeHtml(line.result)}</td>`;
      body.appendChild(row);
    });

    $("thirteenthResults").classList.remove("hidden");
    message.textContent = "Done. The amounts are now listed under Bonuses & Other Earnings.";
    message.className = "form-message success";

    await loadEarnings();
  } catch (error) {
    message.textContent = error.message;
    message.className = "form-message error";
  }
}

// ---------- previous employers ----------

async function loadPriorRecords() {
  const body = $("priorTableBody");

  try {
    priorRecords = await api("/api/prior-employers");
  } catch (error) {
    setEmptyRow(body, 7, error.message);
    return;
  }

  if (priorRecords.length === 0) {
    setEmptyRow(body, 7, "No previous-employer records.");
    return;
  }

  body.innerHTML = "";

  priorRecords.forEach((record) => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${record.taxYear}</td>
      <td>${escapeHtml(employeeName(record.employee))}</td>
      <td>${escapeHtml(record.employerName)}</td>
      <td class="money">${formatMoney(record.taxableCompensation)}</td>
      <td class="money">${formatMoney(record.exemptBenefits)}</td>
      <td class="money">${formatMoney(record.taxWithheld)}</td>
      <td>
        <button type="button" class="action-button edit-button">Edit</button>
        <button type="button" class="action-button delete-button">Delete</button>
      </td>`;

    row.querySelector(".edit-button").addEventListener("click", () => openPriorModal(record));
    row.querySelector(".delete-button").addEventListener("click", () => deletePrior(record));

    body.appendChild(row);
  });
}

function openPriorModal(record) {
  $("priorModalTitle").textContent = record ? "Edit Previous Employer" : "Add Previous Employer";
  $("priorId").value = record ? record.priorId : "";
  $("priorEmployee").value = record ? record.employee.employeeId : ($("priorEmployee").value || "");
  $("priorYear").value = record ? record.taxYear : new Date().getFullYear();
  $("priorEmployerName").value = record ? record.employerName : "";
  $("priorEmployerTin").value = record ? record.employerTin || "" : "";
  $("priorTaxable").value = record ? record.taxableCompensation : "0";
  $("priorExempt").value = record ? record.exemptBenefits : "0";
  $("priorWithheld").value = record ? record.taxWithheld : "0";
  clearMessage("priorMessage");
  openModal("priorModal");
}

async function savePrior(event) {
  event.preventDefault();

  const id = $("priorId").value;
  const body = JSON.stringify({
    employeeId: Number($("priorEmployee").value),
    taxYear: Number($("priorYear").value),
    employerName: $("priorEmployerName").value.trim(),
    employerTin: $("priorEmployerTin").value.trim(),
    taxableCompensation: $("priorTaxable").value,
    exemptBenefits: $("priorExempt").value,
    taxWithheld: $("priorWithheld").value,
  });

  try {
    await api(id ? `/api/prior-employers/${id}` : "/api/prior-employers", { method: id ? "PUT" : "POST", body });
    closeModal("priorModal");
    await loadPriorRecords();
  } catch (error) {
    showError("priorMessage", error.message);
  }
}

async function deletePrior(record) {
  if (!confirm(`Delete the ${record.taxYear} record from ${record.employerName}?`)) {
    return;
  }

  try {
    await api(`/api/prior-employers/${record.priorId}`, { method: "DELETE" });
    await loadPriorRecords();
  } catch (error) {
    alert(error.message);
  }
}

// ---------- helpers ----------

function openModal(id) {
  $(id).classList.add("show");
  $(id).setAttribute("aria-hidden", "false");
}

function closeModal(id) {
  $(id).classList.remove("show");
  $(id).setAttribute("aria-hidden", "true");
}

function showError(id, text) {
  $(id).textContent = text;
  $(id).className = "form-message error";
}

function clearMessage(id) {
  $(id).textContent = "";
  $(id).className = "form-message";
}

function setEmptyRow(body, columns, text) {
  body.innerHTML = `<tr><td colspan="${columns}" class="empty-row"></td></tr>`;
  body.querySelector("td").textContent = text;
}

function employeeName(employee) {
  return employee ? `${employee.lastName}, ${employee.firstName}` : "-";
}

function formatMoney(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return "₱" + Number(value).toLocaleString("en-PH", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value === null || value === undefined ? "" : String(value);
  return div.innerHTML;
}

async function logout() {
  try {
    await fetch("/api/auth/logout", { method: "POST" });
  } finally {
    window.location.href = "/";
  }
}
