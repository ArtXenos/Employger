# Philippine statutory deductions

Em-Ployger deducts the **employee share** of SSS, PhilHealth and Pag-IBIG contributions, plus BIR withholding tax on compensation. The rates below were verified on **2026-10-22** against each agency's own published documents.

**Rates change by law.** When an agency issues a new schedule, update the settings (Settings → Statutory Deductions) or, for the tax table, `PhilippineWithholdingTax.java`, and update this page.

## Sources and current values

| Item | Rule used | Official source |
|---|---|---|
| **SSS** | Employee pays **5%** of the Monthly Salary Credit (MSC). MSC runs from ₱5,000 to ₱35,000 in ₱500 brackets centred on each credit (e.g. ₱5,250–₱5,749.99 → ₱5,500; below ₱5,250 → ₱5,000; ₱34,750 and over → ₱35,000). The amount above ₱20,000 goes to the MPF, but it's in the same 5%. Employee share: ₱250 to ₱1,750 a month. | SSS Circular No. 2024-006, *Schedule of SSS Contributions Effective January 2025* (RA 11199), [sss.gov.ph PDF](https://www.sss.gov.ph/wp-content/uploads/2024/12/CI-2024-006-Publication.pdf). Still current: the [circulars list](https://www.sss.gov.ph/sss-circulars/) shows no newer schedule. Circular 2026-002 only raises pensions. |
| **PhilHealth** | Premium **5%** of monthly basic salary, with the salary floored at ₱10,000 and capped at ₱100,000 (premium ₱500 to ₱5,000). Employed members share it equally with the employer, so the employee pays ₱250 to ₱2,500. | [PhilHealth Advisory 2026-0042](https://www.philhealth.gov.ph/advisories/2026/PA2026-0042.pdf) (signed July 21, 2026: 5% since January 2025, ₱500 minimum), [contribution table](https://www.philhealth.gov.ph/partners/employers/ContributionTable_v2.pdf) (₱10,000 floor, ₱100,000 ceiling, basis = monthly basic salary). Equal sharing: RA 11223 (UHC Act). |
| **Pag-IBIG** | Employee pays **2%** of monthly compensation, capped at a maximum fund salary of ₱10,000 (₱200 a month). **1%** at or below ₱1,500. | HDMF [Circular No. 460](https://www.pagibigfund.gov.ph/documents/Circular%20No.%20460%20-%20Guidelines%20on%20the%20Pag-IBIG%20Fund's%20Implementation%20of%20Increase%20in%20the%20MFS%20Effective%20February%202024.pdf) (MFS ₱10,000, effective February 2024; RA 9679 §7). **The 1% rule for ≤ ₱1,500 was confirmed only from secondary sources**, because the Pag-IBIG site blocked automated reading. Confirm it with the official text. |
| **BIR withholding tax** | Applied per pay period to taxable compensation, using the weekly, semi-monthly or monthly table. Bi-weekly cycles use the weekly table twice. | [RR 11-2018 Annex "E"](https://bir-cdn.bir.gov.ph/local/pdf/Annex%20E%20RR%2011-2018.pdf), *Revised Withholding Tax Table, effective January 1, 2023 and onwards*. |

### BIR Annex E brackets in use

| Period | Not taxed up to | Brackets: tax = fixed + rate × (compensation − over) |
|---|---|---|
| Weekly | ₱4,808 | 0 + 15% over 4,808 · 432.60 + 20% over 7,692 · 1,971.20 + 25% over 15,385 · 7,740.45 + 30% over 38,462 · 42,355.65 + 35% over 153,846 |
| Semi-monthly | ₱10,417 | 0 + 15% over 10,417 · 937.50 + 20% over 16,667 · 4,270.70 + 25% over 33,333 · 16,770.70 + 30% over 83,333 · 91,770.70 + 35% over 333,333 |
| Monthly | ₱20,833 | 0 + 15% over 20,833 · 1,875.00 + 20% over 33,333 · 8,541.80 + 25% over 66,667 · 33,541.80 + 30% over 166,667 · 183,541.80 + 35% over 666,667 |

## How Em-Ployger applies them

1. **Basis:** the employee's monthly base salary for all three contributions. That's PhilHealth's prescribed basis. SSS technically uses total monthly compensation, so overtime-heavy months may differ slightly from SSS's own computation.
2. **Per cycle:** each monthly employee share × 12 ÷ cycles per year. That's half per semi-monthly cycle, the full amount per monthly cycle, and × 12/52 per weekly cycle. Some employers instead deduct the full month in one cutoff; that isn't supported yet.
3. **Taxable compensation** = gross pay (regular + overtime + bonus) − the three employee contributions.
4. **Net pay** = gross pay − contributions − withholding tax. If deductions exceed gross pay, net pay is set to ₱0 and the payroll shows a warning.
5. Each payroll stores the SSS, PhilHealth and Pag-IBIG amounts, the taxable compensation, and the tax. Paid payroll is never recalculated when rates change.

## Not covered (review with your accountant)

- **Minimum wage earner** tax exemption: there's no per-employee flag yet.
- Non-taxable benefits: 13th-month pay and other benefits up to ₱90,000, de minimis benefits.
- Employer shares and EC contributions, and remittance reports (SSS R-3, PhilHealth RF-1, HDMF MCRF, BIR 1601-C, 2316).
- Year-end tax annualization.
- Holiday, rest-day and night-shift premium pay.

**This page is not legal or tax advice.** Have a qualified payroll accountant review these rules before using them for real employee pay.
