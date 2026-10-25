// Corrections to PAID payroll (Payroll page). Administrators and HR managers
// record, change and delete them; the General Manager sees the list only.
// A correction is settled by the later payroll whose cycle contains its
// settle date and is final once that payroll is processed. csrf.js adds the
// security token to every write request. Values are escaped before display.

(() => {
  const $ = (id) => document.getElementById(id);

  const form = $("adjustmentForm");
  const employeeSelect = $("adjustmentEmployee");
  const payrollSelect = $("adjustmentPayroll");
  const kindSelect = $("adjustmentKind");
  const amountInput = $("adjustmentAmount");
  const dateInput = $("adjustmentDate");
  const reasonInput = $("adjustmentReason");
  const consentRow = $("adjustmentConsentRow");
  const consentInput = $("adjustmentConsent");
  const submitButton = $("adjustmentSubmit");
  const cancelButton = $("adjustmentCancel");
  const message = $("adjustmentMessage");
  const tableBody = $("adjustmentTableBody");

  let canChange = false;
  let adjustments = [];
  let paidPayroll = [];

  const peso = new Intl.NumberFormat("en-PH", { style: "currency", currency: "PHP" });

  document.addEventListener("DOMContentLoaded", async () => {
    kindSelect.addEventListener("change", showConsent);
    employeeSelect.addEventListener("change", () => fillPayrollSelect());
    form.addEventListener("submit", save);
    cancelButton.addEventListener("click", resetForm);

    try {
      const me = await getJson("/api/auth/me");
      canChange = me.role === "ADMINISTRATOR" || me.role === "HR_MANAGER";
    } catch (error) {
      canChange = false;
    }

    if (canChange) {
      await loadPaidPayroll();
      form.hidden = false;
      showConsent();
    }

    await loadAdjustments();
  });

  // ---------- requests ----------

  async function getJson(url) {
    const response = await fetch(url, { credentials: "same-origin" });

    if (response.status === 401) {
      window.location.href = "/";
      throw new Error("Please log in.");
    }

    if (!response.ok) {
      throw new Error((await response.text()).trim() || `Request failed (${response.status}).`);
    }

    return response.json();
  }

  async function send(method, url, body) {
    const response = await fetch(url, {
      method,
      credentials: "same-origin",
      headers: body ? { "Content-Type": "application/json" } : {},
      body: body ? JSON.stringify(body) : undefined,
    });

    if (response.status === 401) {
      window.location.href = "/";
      throw new Error("Please log in.");
    }

    if (!response.ok) {
      throw new Error((await response.text()).trim() || `Request failed (${response.status}).`);
    }

    return response.status === 204 ? null : response.json();
  }

  // ---------- data ----------

  async function loadPaidPayroll() {
    try {
      const payroll = await getJson("/api/payroll");
      paidPayroll = payroll
        .filter((record) => record.status === "PAID")
        .sort((a, b) => String(b.payCycle?.startDate).localeCompare(String(a.payCycle?.startDate)));
    } catch (error) {
      setMessage(error.message, true);
      paidPayroll = [];
    }

    const people = new Map();
    paidPayroll.forEach((record) => people.set(record.employee.employeeId, record.employee));

    employeeSelect.replaceChildren(option("", people.size ? "Choose an employee" : "No paid payroll yet"));
    [...people.values()]
      .sort((a, b) => `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`))
      .forEach((employee) => {
        employeeSelect.appendChild(option(employee.employeeId,
          `${employee.lastName}, ${employee.firstName} (#${employee.employeeId})`));
      });

    fillPayrollSelect();
  }

  function fillPayrollSelect(selectedId) {
    const employeeId = Number(employeeSelect.value);
    const records = paidPayroll.filter((record) => record.employee.employeeId === employeeId);

    payrollSelect.replaceChildren(option("", employeeId ? "Choose the paid payroll" : "Choose an employee first"));
    records.forEach((record) => {
      payrollSelect.appendChild(option(record.payrollId,
        `#${record.payrollId} · ${record.payCycle.cycleName} · net ${peso.format(Number(record.netPay || 0))}`));
    });

    if (selectedId) {
      payrollSelect.value = String(selectedId);
    }
  }

  async function loadAdjustments() {
    try {
      adjustments = await getJson("/api/payroll-adjustments");
    } catch (error) {
      tableBody.replaceChildren(row(8, error.message));
      return;
    }

    render();
  }

  // ---------- table ----------

  function render() {
    if (adjustments.length === 0) {
      tableBody.replaceChildren(row(8, "No corrections recorded."));
      return;
    }

    tableBody.innerHTML = "";

    adjustments.forEach((adjustment) => {
      const tr = document.createElement("tr");
      const signed = Number(adjustment.signedAmount);
      const settledBy = adjustment.payrollId
        ? `Payroll #${adjustment.payrollId} (${adjustment.payrollStatus})`
        : "Not yet: next payroll in that cycle";

      tr.innerHTML = `
        <td>${escapeHtml(adjustment.employee.lastName)}, ${escapeHtml(adjustment.employee.firstName)}</td>
        <td>#${adjustment.correctedPayrollId} · ${escapeHtml(adjustment.correctedCycleName)}</td>
        <td>${escapeHtml(adjustment.kindLabel)}</td>
        <td class="${signed < 0 ? "amount-negative" : ""}">${escapeHtml(peso.format(signed))}</td>
        <td>${escapeHtml(adjustment.applyDate)}</td>
        <td class="reason-cell">${escapeHtml(adjustment.reason)}</td>
        <td>${escapeHtml(settledBy)}</td>
        <td class="action-buttons"></td>`;

      const actions = tr.querySelector(".action-buttons");

      if (adjustment.locked) {
        actions.textContent = "Final";
      } else if (canChange) {
        actions.append(
          button("Edit", "edit-button", () => edit(adjustment)),
          button("Delete", "delete-button", () => remove(adjustment)),
        );
      } else {
        actions.textContent = "-";
      }

      tableBody.appendChild(tr);
    });
  }

  // ---------- form ----------

  function showConsent() {
    const recovery = kindSelect.value === "OVERPAYMENT";
    consentRow.hidden = !recovery;
    if (!recovery) {
      consentInput.checked = false;
    }
  }

  function edit(adjustment) {
    $("adjustmentId").value = adjustment.adjustmentId;
    employeeSelect.value = String(adjustment.employee.employeeId);
    fillPayrollSelect(adjustment.correctedPayrollId);
    kindSelect.value = adjustment.kind;
    kindSelect.disabled = true;
    amountInput.value = adjustment.amount;
    dateInput.value = adjustment.applyDate;
    reasonInput.value = adjustment.reason;
    showConsent();
    consentInput.checked = adjustment.employeeConsent;

    submitButton.textContent = "Save Changes";
    cancelButton.hidden = false;
    setMessage(`Editing correction #${adjustment.adjustmentId}. Its kind can't change.`, false);
    form.scrollIntoView({ behavior: "smooth", block: "start" });
    amountInput.focus();
  }

  function resetForm() {
    form.reset();
    $("adjustmentId").value = "";
    kindSelect.disabled = false;
    submitButton.textContent = "Record Correction";
    cancelButton.hidden = true;
    fillPayrollSelect();
    showConsent();
  }

  async function save(event) {
    event.preventDefault();

    const id = $("adjustmentId").value;
    const body = {
      kind: kindSelect.value,
      correctedPayrollId: payrollSelect.value ? Number(payrollSelect.value) : null,
      amount: amountInput.value,
      reason: reasonInput.value.trim(),
      applyDate: dateInput.value || null,
      employeeConsent: consentInput.checked,
    };

    submitButton.disabled = true;
    setMessage("Saving...", false);

    try {
      await send(id ? "PUT" : "POST", id ? `/api/payroll-adjustments/${id}` : "/api/payroll-adjustments", body);
      resetForm();
      setMessage(id ? "Correction updated." : "Correction recorded. It is settled by the payroll for that date.", false);
      await loadAdjustments();
    } catch (error) {
      setMessage(error.message, true);
    } finally {
      submitButton.disabled = false;
    }
  }

  async function remove(adjustment) {
    if (!window.confirm(`Delete this ${adjustment.kindLabel.toLowerCase()} correction of payroll #${adjustment.correctedPayrollId}?`)) {
      return;
    }

    try {
      await send("DELETE", `/api/payroll-adjustments/${adjustment.adjustmentId}`);
      setMessage("Correction deleted.", false);
      await loadAdjustments();
    } catch (error) {
      setMessage(error.message, true);
    }
  }

  // ---------- helpers ----------

  function setMessage(text, isError) {
    message.textContent = text;
    message.className = "adjustment-message" + (isError ? " error" : "");
  }

  function option(value, label) {
    const element = document.createElement("option");
    element.value = value;
    element.textContent = label;
    return element;
  }

  function button(label, className, onClick) {
    const element = document.createElement("button");
    element.type = "button";
    element.className = className;
    element.textContent = label;
    element.addEventListener("click", onClick);
    return element;
  }

  function row(columns, text) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.colSpan = columns;
    td.className = "empty-cell";
    td.textContent = text;
    tr.appendChild(td);
    return tr;
  }

  function escapeHtml(value) {
    const div = document.createElement("div");
    div.textContent = value === null || value === undefined ? "" : String(value);
    return div.innerHTML;
  }
})();
