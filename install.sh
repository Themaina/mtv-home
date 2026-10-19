#!/usr/bin/env bash
# mtv Home installer: puts the dashboard on an Android TV over Wi-Fi, from a Mac or Linux PC.
#
#   curl -fsSL https://raw.githubusercontent.com/Themaina/mtv-home/main/install.sh | bash
#
# It finds the TV on your network, installs the app, asks your name, city and stocks,
# grants the permissions it needs and makes it the home screen. Run it again to update.
#
# Options:
#   --tv IP[:PORT]        skip the network scan
#   --name NAME           your name for the greeting
#   --city CITY           city for the weather
#   --watchlist A,B,C     Yahoo Finance symbols, e.g. TSLA,SPCX,USDKES=X
#   --apk FILE            install a local APK instead of the latest GitHub release
#   --no-launch           don't switch the TV to the dashboard at the end
#   --yes                 accept defaults, never prompt
#   --uninstall           put the original home screen back and remove the app
set -euo pipefail

REPO="${MTV_REPO:-Themaina/mtv-home}"
APK_URL="https://github.com/$REPO/releases/latest/download/mtv-home.apk"
PKG="com.mtv.launcher"
HOME_ACTIVITY="$PKG/.HomeActivity"
WORK="$HOME/.mtv"

TV="${TV_ADDR:-}"; NAME=""; CITY=""; WATCHLIST=""; APK=""; LAUNCH=1; YES=""; UNINSTALL=""
while [ $# -gt 0 ]; do
  case "$1" in
    --tv) TV="$2"; shift ;;
    --name) NAME="$2"; shift ;;
    --city) CITY="$2"; shift ;;
    --watchlist) WATCHLIST="$2"; shift ;;
    --apk) APK="$2"; shift ;;
    --no-launch) LAUNCH="" ;;
    --yes|-y) YES=1 ;;
    --uninstall) UNINSTALL=1 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
info() { printf '  %s\n' "$*"; }
die() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }
ask() { # ask "Question" "default" -> answer (works under curl | bash by reading the terminal)
  local answer=""
  if [ -z "$YES" ] && { true </dev/tty; } 2>/dev/null; then
    read -r -p "  $1${2:+ [$2]}: " answer </dev/tty || true
  fi
  printf '%s' "${answer:-$2}"
}

mkdir -p "$WORK"

# ---- 1. adb -------------------------------------------------------------------------------
find_adb() {
  for c in "$(command -v adb 2>/dev/null || true)" "$HOME/Library/Android/sdk/platform-tools/adb" \
           "$HOME/Android/Sdk/platform-tools/adb" "$WORK/platform-tools/adb"; do
    [ -n "$c" ] && [ -x "$c" ] && { echo "$c"; return; }
  done
  local os
  case "$(uname -s)" in Darwin) os=darwin ;; Linux) os=linux ;; *) die "Use macOS or Linux (Windows: install adb, then run the steps in the README)." ;; esac
  info "Downloading Google's adb tools (about 10 MB)…" >&2
  curl -fsSL -o "$WORK/platform-tools.zip" "https://dl.google.com/android/repository/platform-tools-latest-$os.zip"
  unzip -qo "$WORK/platform-tools.zip" -d "$WORK"
  echo "$WORK/platform-tools/adb"
}
bold "mtv Home installer"
ADB="$(find_adb)"
"$ADB" start-server >/dev/null 2>&1 || true

# ---- 2. find the TV ---------------------------------------------------------------------
local_prefix() {
  local ip=""
  if [ "$(uname -s)" = Darwin ]; then
    for i in en0 en1 en2; do ip=$(ipconfig getifaddr "$i" 2>/dev/null || true); [ -n "$ip" ] && break; done
  else
    ip=$(hostname -I 2>/dev/null | awk '{print $1}')
  fi
  [ -n "$ip" ] && echo "${ip%.*}"
}
scan() {
  local prefix; prefix="$(local_prefix)" || true
  [ -n "$prefix" ] || return 0
  info "Looking for TVs with network debugging on $prefix.0/24…" >&2
  local flag="-w1"; [ "$(uname -s)" = Darwin ] && flag="-G1"
  for i in $(seq 1 254); do
    ( nc -z $flag "$prefix.$i" 5555 >/dev/null 2>&1 && echo "$prefix.$i:5555" ) &
  done
  wait
}
if [ -z "$TV" ]; then
  FOUND=$(scan | sort -t. -k4 -n)
  COUNT=$(printf '%s' "$FOUND" | grep -c . || true)
  if [ "$COUNT" = 1 ]; then
    TV="$FOUND"
  elif [ "$COUNT" -gt 1 ]; then
    info "Found more than one:"; printf '%s\n' "$FOUND" | sed 's/^/    /'
    TV=$(ask "Which TV" "$(printf '%s' "$FOUND" | head -1)")
  else
    cat <<'EOF'
  No TV found. On the TV:
    1. Settings > System > About: click "Build" 7 times.
    2. Settings > System > Developer options: turn on "USB debugging" / "Network debugging".
    3. Settings > Network: note the TV's IP address.
EOF
    TV=$(ask "TV IP address" "")
    [ -n "$TV" ] || die "No TV address given."
  fi
fi
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac

bold "Connecting to $TV"
"$ADB" connect "$TV" >/dev/null 2>&1 || true
for _ in $(seq 1 30); do
  STATE=$("$ADB" -s "$TV" get-state 2>&1 || true)
  case "$STATE" in
    device) break ;;
    *unauthorized*) info "Accept the \"Allow debugging?\" prompt on the TV (tick Always allow)…"; sleep 2 ;;
    *) "$ADB" connect "$TV" >/dev/null 2>&1 || true; sleep 1 ;;
  esac
done
[ "$("$ADB" -s "$TV" get-state 2>/dev/null)" = device ] || die "Couldn't connect to $TV. Is network debugging on?"
tv() { "$ADB" -s "$TV" "$@"; }
info "Connected: $(tv shell getprop ro.product.manufacturer | tr -d '\r') $(tv shell getprop ro.product.model | tr -d '\r'), Android $(tv shell getprop ro.build.version.release | tr -d '\r')"

current_home() { tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r'; }
SAVED_HOME="$WORK/previous-home-${TV//[:.]/_}.txt"

# ---- uninstall ----------------------------------------------------------------------------
if [ -n "$UNINSTALL" ]; then
  if [ -s "$SAVED_HOME" ]; then tv shell cmd package set-home-activity "$(cat "$SAVED_HOME")" >/dev/null || true; fi
  tv uninstall "$PKG" >/dev/null && bold "Removed mtv Home. Home screen: $(current_home)"
  exit 0
fi

# ---- 3. get the app ---------------------------------------------------------------------
if [ -z "$APK" ]; then
  APK="$WORK/mtv-home.apk"
  bold "Downloading the latest mtv Home"
  curl -fL --progress-bar -o "$APK" "$APK_URL" || die "Download failed: $APK_URL"
fi
[ -s "$APK" ] || die "APK not found: $APK"

# Remember the original home screen once, for --uninstall.
PREV="$(current_home)"
case "$PREV" in "$PKG"/*|*ResolverActivity*|"") ;; *) [ -s "$SAVED_HOME" ] || echo "$PREV" > "$SAVED_HOME" ;; esac

# ---- 4. install ---------------------------------------------------------------------------
WAS_INSTALLED=$(tv shell pm list packages "$PKG" | tr -d '\r' | grep -cx "package:$PKG" || true)
bold "$([ "$WAS_INSTALLED" = 1 ] && echo Updating || echo Installing)"
if ! OUT=$(tv install -r "$APK" 2>&1); then
  if printf '%s' "$OUT" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
    info "An older test build with a different signature is installed; replacing it."
    tv uninstall "$PKG" >/dev/null
    tv install "$APK" >/dev/null || die "Install failed."
  else
    die "Install failed: $OUT"
  fi
fi

# ---- 5. personalise -----------------------------------------------------------------------
# First install: ask. Updates keep the TV's settings unless options were passed.
if [ "$WAS_INSTALLED" != 1 ]; then
  bold "Make it yours"
  [ -n "$NAME" ] || NAME=$(ask "Your name" "")
  [ -n "$CITY" ] || CITY=$(ask "City for weather" "Nairobi")
  [ -n "$WATCHLIST" ] || WATCHLIST=$(ask "Stocks and currencies to watch (Yahoo symbols)" "TSLA,SPCX,USDKES=X")
fi
EXTRAS=()
[ -n "$NAME" ] && EXTRAS+=(--es name "'$NAME'")
[ -n "$CITY" ] && EXTRAS+=(--es city "'$CITY'")
[ -n "$WATCHLIST" ] && EXTRAS+=(--es watchlist "'$WATCHLIST'")
if [ ${#EXTRAS[@]} -gt 0 ]; then
  # -f 0x20 (FLAG_INCLUDE_STOPPED_PACKAGES): a freshly installed app is "stopped" and would miss it.
  RESULT=$(tv shell am broadcast -f 0x20 -a com.mtv.launcher.CONFIGURE -n "$PKG/.config.ConfigReceiver" "${EXTRAS[@]}" 2>&1)
  if printf '%s' "$RESULT" | grep -q 'data="ok"'; then info "Saved your settings."; else info "Couldn't save settings ($RESULT). Use Personalize on the TV."; fi
else
  info "Keeping your existing settings."
fi

# ---- 6. permissions -----------------------------------------------------------------------
bold "Granting permissions"
tv shell cmd notification allow_listener "$PKG/$PKG.notify.NotificationService" >/dev/null 2>&1 \
  && info "Notifications on the dashboard: on" || info "Notifications: not supported on this TV"
tv shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 \
  && info "Market alerts over other apps: on" || info "Pop-up alerts: not supported on this TV"
tv shell cmd deviceidle whitelist +"$PKG" >/dev/null 2>&1 || true

# ---- 7. home screen -----------------------------------------------------------------------
tv shell cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null 2>&1 || true
tv shell am start-foreground-service -n "$PKG/.market.MarketService" >/dev/null 2>&1 || true
if [ -n "$LAUNCH" ]; then tv shell input keyevent KEYCODE_HOME; fi

bold "Done. Home screen: $(current_home)"
info "Change your name, city or stocks any time: Settings row > Personalize."
info "To undo: curl -fsSL https://raw.githubusercontent.com/$REPO/main/install.sh | bash -s -- --uninstall"
