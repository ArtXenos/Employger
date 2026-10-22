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
6. **Minimum wage earners (MWE).** Tick *Minimum wage earner* on the employee's form. For those employees:
   - regular pay and overtime pay are exempt from withholding tax;
   - only other compensation (gross pay minus regular and overtime pay, i.e. bonuses once they're recorded) goes through the withholding table, and contributions aren't subtracted from it again;
   - SSS, PhilHealth and Pag-IBIG still apply as usual.

   The payroll's calculation notes record that the exemption was applied. Changing the flag is written to the audit trail (`EMPLOYEE_MWE_CHANGED`).

   Source: [RR 11-2018](https://bir-cdn.bir.gov.ph/local/pdf/Digest%20RR%2011-2018.pdf), amending RR 2-98 §2.78.1(B)(13) and §2.79(A), read from the BIR's own digest on 2026-10-23. It covers the statutory minimum wage (SMW) plus holiday pay, overtime pay, night shift differential and hazard pay of private-sector workers paid the regional SMW fixed by the RTWPB. Additional compensation from the same employer "shall be taxable only on such additional compensation received."

   **HR decides who is an MWE.** Em-Ployger doesn't know regional wage orders, so it can't check that the salary really equals the SMW. Under the same regulation, reducing wages to obtain the exemption is misrepresentation.

7. **Employer shares** (migration V5) are recorded on every payroll calculated from now on, using the same salary basis and per-cycle spreading as the employee shares. They're a **company cost on top of gross pay and are never deducted** from the employee. The Payroll page shows the total as *Employer contributions (company cost)*. Payroll calculated before V5 shows "Not recorded".

| Item | Employer share | Official source (verified 2026-10-23) |
|---|---|---|
| **SSS** | **10%** of the whole Monthly Salary Credit, regular SS plus MPF (e.g. MSC ₱25,000 → ₱2,000 + ₱500). | SSS Circular 2024-006 schedule table, *Employer* columns, read from the page image of the official PDF. |
| **SSS EC** | **₱10** a month for MSC below ₱15,000, **₱30** from MSC ₱15,000 (compensation ₱14,750 and up). Paid by the employer only. | Same table, *EC* column. |
| **PhilHealth** | The other half of the premium: the same amount as the employee share. | Equal sharing, RA 11223; premium rules as above. |
| **Pag-IBIG** | **2%** of monthly compensation up to the ₱10,000 maximum fund salary (₱200 a month), **including** employees earning ₱1,500 or less, who themselves pay 1%. | RA 9679 §7: "All employers - two percent (2%) of the monthly compensation of all covered employees" ([lawphil](https://lawphil.net/statutes/repacts/ra2009/ra_9679_2009.html)); cap: Circular 460. |

Settings (Settings → Statutory Deductions): `sss_employer_rate`, `sss_ec_msc_threshold`, `sss_ec_amount_low`, `sss_ec_amount_high`, `pagibig_employer_rate`. The PhilHealth employer share follows `philhealth_employee_share`.

Spreading per cycle means that, for example, a semi-monthly cycle records ₱15.00 EC (half of ₱30). SSS bills EC per month, so the remittance total for a month is the sum of that month's cycles.

## Not covered (review with your accountant)

- Holiday pay, night shift differential and hazard pay aren't computed, so the MWE exemption covers only regular and overtime pay.
- Non-taxable benefits: 13th-month pay and other benefits up to ₱90,000, de minimis benefits.
- Remittance reports (SSS R-3, PhilHealth RF-1, HDMF MCRF, BIR 1601-C, 2316). Employer shares are recorded, but no report is produced from them yet.
- Year-end tax annualization.
- Holiday, rest-day and night-shift premium pay.

**This page is not legal or tax advice.** Have a qualified payroll accountant review these rules before using them for real employee pay.
