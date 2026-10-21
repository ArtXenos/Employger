let payCycles = [];

const tableBody = document.getElementById("payCycleTableBody");

const modal = document.getElementById("payCycleModal");

const form = document.getElementById("payCycleForm");

const modalTitle = document.getElementById("modalTitle");

const payCycleId = document.getElementById("payCycleId");

const cycleName = document.getElementById("cycleName");

const cycleType = document.getElementById("cycleType");

const startDate = document.getElementById("startDate");

const endDate = document.getElementById("endDate");

const payDate = document.getElementById("payDate");

const status = document.getElementById("status");

const formMessage = document.getElementById("formMessage");

document.addEventListener("DOMContentLoaded", () => {
  loadPayCycles();

  document
    .getElementById("addPayCycleButton")
    .addEventListener("click", openAddModal);

  document
    .getElementById("closeModalButton")
    .addEventListener("click", closeModal);

  document.getElementById("cancelButton").addEventListener("click", closeModal);

  form.addEventListener("submit", savePayCycle);

  document.getElementById("logoutButton").addEventListener("click", (event) => {
    event.preventDefault();

    logout();
  });
});

async function loadPayCycles() {
  try {
    const response = await fetch("/api/pay-cycles");

    if (response.status === 401 || response.status === 403) {
      window.location.href = "/";
      return;
    }

    if (!response.ok) {
      throw new Error("Failed to load pay cycles.");
    }

    payCycles = await response.json();

    renderPayCycles();

    updateSummary();
  } catch (error) {
    console.error(error);

    tableBody.innerHTML = `
            <tr>
                <td colspan="8" class="empty-row">
                    Unable to load pay cycles.
                </td>
            </tr>
        `;
  }
}

function renderPayCycles() {
  tableBody.innerHTML = "";

  if (payCycles.length === 0) {
    tableBody.innerHTML = `
            <tr>
                <td colspan="8" class="empty-row">
                    No pay cycles found.
                </td>
            </tr>
        `;

    return;
  }

  payCycles.forEach((cycle) => {
    const row = document.createElement("tr");

    const statusClass = getStatusClass(cycle.status);

    row.innerHTML = `

            <td>
                ${cycle.payCycleId}
            </td>

            <td>
                <strong>
                    ${escapeHtml(cycle.cycleName)}
                </strong>
            </td>

            <td>
                ${formatCycleType(cycle.cycleType)}
            </td>

            <td>
                ${cycle.startDate || "-"}
            </td>

            <td>
                ${cycle.endDate || "-"}
            </td>

            <td>
                ${cycle.payDate || "-"}
            </td>

            <td>
                <span class="status ${statusClass}">
                    ${cycle.status}
                </span>
            </td>

            <td>

                <button
                    type="button"
                    class="action-button edit-button"
                    onclick="editPayCycle(${cycle.payCycleId})"
                >
                    Edit
                </button>

                <button
                    type="button"
                    class="action-button delete-button"
                    onclick="deletePayCycle(${cycle.payCycleId})"
                >
                    Delete
                </button>

            </td>

        `;

    tableBody.appendChild(row);
  });
}

function updateSummary() {
  document.getElementById("totalCycles").textContent = payCycles.length;

  document.getElementById("openCycles").textContent = payCycles.filter(
    (cycle) => cycle.status === "OPEN",
  ).length;

  document.getElementById("processingCycles").textContent = payCycles.filter(
    (cycle) => cycle.status === "PROCESSING",
  ).length;

  document.getElementById("processedCycles").textContent = payCycles.filter(
    (cycle) => cycle.status === "PROCESSED",
  ).length;
}

function getStatusClass(statusValue) {
  switch (statusValue) {
    case "OPEN":
      return "status-open";

    case "PROCESSING":
      return "status-processing";

    case "PROCESSED":
      return "status-processed";

    case "CLOSED":
      return "status-closed";

    default:
      return "";
  }
}

function formatCycleType(type) {
  switch (type) {
    case "WEEKLY":
      return "Weekly";

    case "BI_WEEKLY":
      return "Bi-weekly";

    case "SEMI_MONTHLY":
      return "Semi-monthly";

    case "MONTHLY":
      return "Monthly";

    default:
      return type || "-";
  }
}

function openAddModal() {
  form.reset();

  payCycleId.value = "";

  cycleType.value = "SEMI_MONTHLY";

  status.value = "OPEN";

  modalTitle.textContent = "Add Pay Cycle";

  formMessage.textContent = "";

  formMessage.className = "form-message";

  modal.classList.add("show");

  modal.setAttribute("aria-hidden", "false");
}

async function editPayCycle(id) {
  try {
    const response = await fetch(`/api/pay-cycles/${id}`);

    if (response.status === 401 || response.status === 403) {
      window.location.href = "/";
      return;
    }

    if (!response.ok) {
      throw new Error("Failed to load pay cycle.");
    }

    const cycle = await response.json();

    payCycleId.value = cycle.payCycleId;

    cycleName.value = cycle.cycleName || "";

    cycleType.value = cycle.cycleType || "SEMI_MONTHLY";

    startDate.value = cycle.startDate || "";

    endDate.value = cycle.endDate || "";

    payDate.value = cycle.payDate || "";

    status.value = cycle.status || "OPEN";

    modalTitle.textContent = "Edit Pay Cycle";

    formMessage.textContent = "";

    formMessage.className = "form-message";

    modal.classList.add("show");

    modal.setAttribute("aria-hidden", "false");
  } catch (error) {
    console.error(error);

    alert("Unable to load the selected pay cycle.");
  }
}

function closeModal() {
  modal.classList.remove("show");

  modal.setAttribute("aria-hidden", "true");

  form.reset();

  payCycleId.value = "";

  formMessage.textContent = "";

  formMessage.className = "form-message";
}

async function savePayCycle(event) {
  event.preventDefault();

  formMessage.textContent = "";

  formMessage.className = "form-message";

  const data = {
    cycleName: cycleName.value.trim(),

    cycleType: cycleType.value,

    startDate: startDate.value,

    endDate: endDate.value,

    payDate: payDate.value,

    status: status.value,
  };

  const validationError = validatePayCycle(data);

  if (validationError) {
    showMessage(validationError, "error");

    return;
  }

  const id = payCycleId.value;

  const isEditing = id !== "";

  const url = isEditing ? `/api/pay-cycles/${id}` : "/api/pay-cycles";

  const method = isEditing ? "PUT" : "POST";

  try {
    const response = await fetch(url, {
      method: method,

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify(data),
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "/";

      return;
    }

    if (!response.ok) {
      // The server explains validation and conflict errors in plain text.
      const errorText = (await response.text()).trim();

      throw new Error(errorText || "Unable to save pay cycle.");
    }

    showMessage(
      isEditing
        ? "Pay cycle updated successfully."
        : "Pay cycle added successfully.",
      "success",
    );

    setTimeout(() => {
      closeModal();

      loadPayCycles();
    }, 500);
  } catch (error) {
    console.error(error);

    showMessage(error.message, "error");
  }
}

function validatePayCycle(data) {
  if (!data.cycleName) {
    return "Cycle name is required.";
  }

  if (!data.startDate || !data.endDate || !data.payDate) {
    return "Start date, end date, and pay date are required.";
  }

  const start = new Date(data.startDate + "T00:00:00");

  const end = new Date(data.endDate + "T00:00:00");

  const pay = new Date(data.payDate + "T00:00:00");

  if (end < start) {
    return "End date cannot be before start date.";
  }

  if (pay < end) {
    return "Pay date cannot be before end date.";
  }

  const numberOfDays = Math.round((end - start) / 86400000) + 1;

  switch (data.cycleType) {
    case "WEEKLY":
      if (numberOfDays !== 7) {
        return "Weekly pay cycles must cover exactly 7 days.";
      }

      break;

    case "BI_WEEKLY":
      if (numberOfDays !== 14) {
        return "Bi-weekly pay cycles must cover exactly 14 days.";
      }

      break;

    case "SEMI_MONTHLY":
      const firstHalf = start.getDate() === 1 && end.getDate() === 15;

      const lastDay = new Date(
        end.getFullYear(),
        end.getMonth() + 1,
        0,
      ).getDate();

      const secondHalf = start.getDate() === 16 && end.getDate() === lastDay;

      if (!firstHalf && !secondHalf) {
        return "Semi-monthly cycles must cover days 1-15 or 16-last day of the month.";
      }

      break;

    case "MONTHLY":
      const lastDayOfMonth = new Date(
        end.getFullYear(),
        end.getMonth() + 1,
        0,
      ).getDate();

      if (start.getDate() !== 1 || end.getDate() !== lastDayOfMonth) {
        return "Monthly cycles must cover the entire month.";
      }

      break;

    default:
      return "Please select a valid cycle type.";
  }

  return null;
}

async function deletePayCycle(id) {
  const cycle = payCycles.find((item) => item.payCycleId === id);

  if (!cycle) {
    return;
  }

  const confirmed = confirm(`Delete the pay cycle "${cycle.cycleName}"?`);

  if (!confirmed) {
    return;
  }

  try {
    const response = await fetch(`/api/pay-cycles/${id}`, {
      method: "DELETE",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "/";

      return;
    }

    if (!response.ok) {
      const errorText = (await response.text()).trim();

      throw new Error(errorText || "Unable to delete pay cycle.");
    }

    await loadPayCycles();
  } catch (error) {
    console.error(error);

    alert(error.message);
  }
}

function showMessage(message, type) {
  formMessage.textContent = message;

  formMessage.className = `form-message ${type}`;
}

function escapeHtml(value) {
  const div = document.createElement("div");

  div.textContent = value;

  return div.innerHTML;
}

async function logout() {
  try {
    await fetch("/api/auth/logout", {
      method: "POST",
    });
  } finally {
    window.location.href = "/";
  }
}
