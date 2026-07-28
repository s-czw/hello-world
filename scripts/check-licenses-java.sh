#!/usr/bin/env bash
# Cairn license gate (Java / Maven) — enforces D-021 for the shipped API runtime.
#
# Generates the third-party license inventory via the license-maven-plugin
# (`mvn license:add-third-party`, compile+runtime scope only) then classifies every
# dependency against scripts/licenses-java-allow.txt:
#   FAIL  strong copyleft / source-available: GPL/AGPL/SSPL/RSAL/BUSL/FSL/Elastic/…
#   WARN  weak copyleft (dev/build tolerated): LGPL / EPL / MPL / CDDL
#   PASS  at least one allowlisted permissive token
#   FAIL  no recognized token at all (mis-declared → caught, never silent)
#
# GPL2/GPL3 carrying a Classpath Exception (CPE) is treated as permissive — that is
# the documented Temurin/OpenJDK-style exception (arch licensing appendix).
#
# Exit 0 = clean (warnings allowed), 1 = a disallowed license was found.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
API_DIR="${REPO_ROOT}/apps/api"
ALLOW_FILE="${SCRIPT_DIR}/licenses-java-allow.txt"
REPORT="${API_DIR}/target/generated-sources/license/THIRD-PARTY.txt"

MVN="${API_DIR}/mvnw"
[ -x "${MVN}" ] || MVN="mvn"

echo "[licenses:java] generating THIRD-PARTY inventory (${MVN} license:add-third-party)…"
( cd "${API_DIR}" && "${MVN}" -B -q license:add-third-party )

if [ ! -f "${REPORT}" ]; then
  echo "[licenses:java] ERROR: report not found at ${REPORT}" >&2
  exit 2
fi

# Load permissive allowlist tokens (skip comments/blanks).
mapfile -t ALLOW_TOKENS < <(grep -vE '^\s*(#|$)' "${ALLOW_FILE}")

fail_count=0
warn_count=0
pass_count=0

# Only dependency lines start with whitespace and an opening paren.
while IFS= read -r line; do
  case "${line}" in
    *"("*")"*) : ;;
    *) continue ;;
  esac
  # Strip the trailing coordinate group "(group:artifact:version - url)" (the only
  # parenthesized group containing a colon) so we scan license annotations only.
  lic="$(printf '%s' "${line}" | sed -E 's/\([^()]*:[^()]*\)[[:space:]]*$//')"
  low="$(printf '%s' "${lic}" | tr '[:upper:]' '[:lower:]')"

  # Does it carry a classpath exception? (makes GPL-with-CPE permissive)
  has_cpe=0
  case "${low}" in
    *"classpath exception"* | *"w/ cpe"* | *"with cpe"* | *"(cpe)"*) has_cpe=1 ;;
  esac

  # ---- strong copyleft / source-available => FAIL ----
  hard_fail=""
  # Affero / SSPL / source-available families (unconditional).
  for bad in "affero" "agpl" "sspl" "server side public" "rsal" "busl" \
             "business source" "fsl-" "prosperity" "commons clause" \
             "cc-by-nc" "elastic license" "elastic-2.0"; do
    case "${low}" in *"${bad}"*) hard_fail="${bad}"; break ;; esac
  done
  # Plain GPL (v2/v3) — but Lesser (LGPL) is weak, and a Classpath Exception clears it.
  if [ -z "${hard_fail}" ] && [ "${has_cpe}" -eq 0 ]; then
    case "${low}" in
      *"lesser"*) : ;;  # LGPL handled as WARN below
      *"gpl-2"* | *"gpl-3"* | *"gplv2"* | *"gplv3"* | *"gpl2"* | *"gpl3"* | *"general public license"*)
        hard_fail="gpl" ;;
    esac
  fi
  if [ -n "${hard_fail}" ]; then
    echo "  FAIL  ${line}"
    echo "        └─ banned license token: '${hard_fail}'"
    fail_count=$((fail_count + 1))
    continue
  fi

  # ---- permissive allowlist match => PASS ----
  matched=""
  for tok in "${ALLOW_TOKENS[@]}"; do
    tl="$(printf '%s' "${tok}" | tr '[:upper:]' '[:lower:]')"
    case "${low}" in *"${tl}"*) matched="${tok}"; break ;; esac
  done
  # A GPL+CPE dependency (e.g. jakarta.annotation) is permissive by exception even
  # if its only other token is EPL — treat CPE as an allow signal.
  if [ -z "${matched}" ] && [ "${has_cpe}" -eq 1 ]; then
    matched="GPL+Classpath-Exception"
  fi
  if [ -n "${matched}" ]; then
    pass_count=$((pass_count + 1))
    continue
  fi

  # ---- weak copyleft => WARN (allowed) ----
  weak=""
  for w in "lgpl" "lesser general public" "epl" "eclipse public license" \
           "mpl" "mozilla public" "cddl"; do
    case "${low}" in *"${w}"*) weak="${w}"; break ;; esac
  done
  if [ -n "${weak}" ]; then
    echo "  WARN  ${line}"
    echo "        └─ weak copyleft ('${weak}') — permitted, review before shipping"
    warn_count=$((warn_count + 1))
    continue
  fi

  # ---- nothing recognized => FAIL (mis-declared / unknown) ----
  echo "  FAIL  ${line}"
  echo "        └─ no recognized permissive license token"
  fail_count=$((fail_count + 1))
done < "${REPORT}"

echo "[licenses:java] ${pass_count} permissive, ${warn_count} warn, ${fail_count} fail."
if [ "${fail_count}" -gt 0 ]; then
  echo "[licenses:java] ${fail_count} disallowed license(s) — see D-021 in CLAUDE.md." >&2
  exit 1
fi
echo "[licenses:java] OK — all runtime licenses are permissive (or exception-cleared)."
exit 0
