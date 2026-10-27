// The Company Overview: the month's payroll totals, cost by department and
// the labor cost of the last 6 months as a chart. One component for every
// page that shows it (Dashboard, Reports): a page adds
//
//   <section class="section-card" data-company-overview></section>
//
// inside an element with class "dashboard-container" or "company-overview",
// and loads this script. The server sends totals only, no personal data.
// window.CompanyOverview.reload() refreshes it, e.g. after an approval.

(function () {
  const MARKUP = `
    <div class="section-header">
      <div>
        <h2 id="overviewHeading">Company Overview</h2>
        <p>Payroll cost from processed and paid payroll, by the month the pay cycle ends. Totals only, no personal data.</p>
      </div>
      <div class="overview-filter">
        <label for="overviewMonth">Month</label>
        <input id="overviewMonth" type="month" />
      </div>
    </div>

    <p id="overviewMessage" class="overview-feedback" role="status" aria-live="polite"></p>

    <div id="overviewTotals" class="overview-totals" aria-label="Totals for the month"></div>
    <ul id="overviewNotes" class="overview-notes"></ul>

    <div class="overview-columns">
      <div>
        <h3 class="overview-subheading">By department</h3>
        <div class="overview-table">
          <table>
            <caption class="visually-hidden">Payroll cost by department</caption>
            <thead>
              <tr>
                <th scope="col">Department</th>
                <th scope="col" class="amount">Active employees</th>
                <th scope="col" class="amount">Gross pay</th>
                <th scope="col" class="amount">Employer contributions</th>
                <th scope="col" class="amount">Labor cost</th>
              </tr>
            </thead>
            <tbody id="departmentTableBody"></tbody>
          </table>
        </div>
      </div>

      <figure class="trend-figure" aria-labelledby="trendTitle">
        <figcaption id="trendTitle" class="overview-subheading">Labor cost, last 6 months</figcaption>
        <div id="trendChart" class="trend-chart"></div>
        <div id="trendTooltip" class="trend-tooltip" role="tooltip" hidden></div>
        <div class="overview-table">
          <table>
            <caption class="visually-hidden">Labor cost by month</caption>
            <thead>
              <tr>
                <th scope="col">Month</th>
                <th scope="col" class="amount">Gross pay</th>
                <th scope="col" class="amount">Overtime pay</th>
                <th scope="col" class="amount">Labor cost</th>
              </tr>
            </thead>
            <tbody id="trendTableBody"></tbody>
          </table>
        </div>
      </figure>
    </div>`;

  const byId = (id) => document.getElementById(id);
  const monthName = new Intl.DateTimeFormat("en-PH", { month: "short", year: "numeric" });
  const currency = new Intl.NumberFormat("en-PH", {
    style: "currency",
    currency: "PHP",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  function money(value) {
    const number = Number(value);
    return currency.format(Number.isFinite(number) ? number : 0);
  }

  function monthLabel(yearMonth) {
    const [year, month] = String(yearMonth).split("-").map(Number);
    return monthName.format(new Date(year, month - 1, 1));
  }

  function compactMoney(value) {
    const number = Number(value) || 0;
    return number >= 1000 ? `₱${(number / 1000).toLocaleString("en-PH", { maximumFractionDigits: 1 })}k` : money(number);
  }

  function cell(content, className) {
    const td = document.createElement("td");
    td.textContent = content;
    if (className) {
      td.className = className;
    }
    return td;
  }

  function emptyRow(body, columns, message) {
    const row = document.createElement("tr");
    const td = cell(message, "empty");
    td.colSpan = columns;
    row.appendChild(td);
    body.replaceChildren(row);
  }

  function feedback(message, kind) {
    const element = byId("overviewMessage");
    element.textContent = message || "";
    element.className = "overview-feedback" + (kind ? " " + kind : "");
  }

  async function load() {
    const [year, month] = byId("overviewMonth").value.split("-");

    feedback("Loading...", null);

    let summary;

    try {
      const response = await fetch(`/api/dashboard/executive?year=${Number(year)}&month=${Number(month)}`, {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
      });

      if (response.status === 401) {
        window.location.href = "/";
        return;
      }
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      summary = await response.json();
    } catch (error) {
      feedback("The company overview could not be loaded.", "error");
      console.error(error);
      return;
    }

    feedback("", null);
    renderTotals(summary);
    renderDepartments(summary);
    renderTrend(summary.trend);

    byId("overviewNotes").replaceChildren(
      ...(summary.notes || []).map((note) => {
        const item = document.createElement("li");
        item.textContent = note;
        return item;
      }),
    );
  }

  function renderTotals(summary) {
    const tiles = summary.totals.map((total) => [total.label, money(total.amount), total.key === "laborCost"]);

    tiles.push(["Active employees", String(summary.activeEmployees), false]);
    tiles.push(["Pending overtime requests", String(summary.pendingOvertimeRequests), false]);

    byId("overviewTotals").replaceChildren(
      ...tiles.map(([label, value, primary]) => {
        const tile = document.createElement("div");
        const name = document.createElement("span");
        const amount = document.createElement("strong");

        tile.className = "overview-tile" + (primary ? " primary" : "");
        name.textContent = label;
        amount.textContent = value;
        tile.append(name, amount);

        return tile;
      }),
    );
  }

  function renderDepartments(summary) {
    const body = byId("departmentTableBody");

    if (summary.departments.length === 0) {
      emptyRow(body, 5, "No employees or payroll for this month.");
      return;
    }

    body.replaceChildren(
      ...summary.departments.map((department) => {
        const row = document.createElement("tr");

        row.append(
          cell(department.department),
          cell(String(department.activeEmployees), "amount"),
          cell(money(department.amounts.grossPay), "amount"),
          cell(money(department.amounts.employerContributions), "amount"),
          cell(money(department.amounts.laborCost), "amount"),
        );

        return row;
      }),
    );
  }

  function renderTrend(trend) {
    const chart = byId("trendChart");
    const tooltip = byId("trendTooltip");
    const highest = Math.max(...trend.map((point) => Number(point.amounts.laborCost) || 0), 0);

    chart.replaceChildren(
      ...trend.map((point, index) => {
        const value = Number(point.amounts.laborCost) || 0;
        const column = document.createElement("button");
        const bar = document.createElement("span");
        const label = `${monthLabel(point.month)}: ${money(value)}`;

        column.type = "button";
        column.className = "trend-column";
        column.setAttribute("aria-label", `Labor cost ${label}`);

        bar.className = "trend-bar";
        bar.style.height = highest > 0 ? `${(value / highest) * 100}%` : "0";
        column.appendChild(bar);

        // Direct label on the latest month only; the rest are in the tooltip and table.
        if (index === trend.length - 1 && value > 0) {
          const tip = document.createElement("span");
          tip.className = "trend-value";
          tip.textContent = compactMoney(value);
          tip.style.bottom = bar.style.height;
          column.appendChild(tip);
        }

        const show = () => {
          tooltip.textContent = label;
          tooltip.hidden = false;
          tooltip.style.left = `${column.offsetLeft + column.offsetWidth / 2}px`;
          tooltip.style.top = `${column.offsetTop + column.offsetHeight - bar.offsetHeight - 6}px`;
        };
        const hide = () => {
          tooltip.hidden = true;
        };

        column.addEventListener("mouseenter", show);
        column.addEventListener("focus", show);
        column.addEventListener("mouseleave", hide);
        column.addEventListener("blur", hide);

        return column;
      }),
    );

    let labels = chart.nextElementSibling;
    if (!labels || !labels.classList.contains("trend-labels")) {
      labels = document.createElement("div");
      labels.className = "trend-labels";
      labels.setAttribute("aria-hidden", "true");
      chart.after(labels);
    }
    labels.replaceChildren(
      ...trend.map((point) => {
        const name = document.createElement("span");
        name.textContent = monthLabel(point.month).replace(/ \d{4}$/, "");
        return name;
      }),
    );

    byId("trendTableBody").replaceChildren(
      ...trend.slice().reverse().map((point) => {
        const row = document.createElement("tr");

        row.append(
          cell(monthLabel(point.month)),
          cell(money(point.amounts.grossPay), "amount"),
          cell(money(point.amounts.overtimePay), "amount"),
          cell(money(point.amounts.laborCost), "amount"),
        );

        return row;
      }),
    );
  }

  function mount() {
    const section = document.querySelector("[data-company-overview]");

    if (!section) {
      return;
    }

    section.id = section.id || "overviewSection";
    section.setAttribute("aria-labelledby", "overviewHeading");
    section.innerHTML = MARKUP;

    const today = new Date();
    const monthInput = byId("overviewMonth");

    monthInput.value = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
    monthInput.addEventListener("change", () => {
      if (monthInput.value) {
        load();
      }
    });

    load();
  }

  window.CompanyOverview = { reload: () => (byId("overviewMonth") ? load() : Promise.resolve()) };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mount);
  } else {
    mount();
  }
})();
