#!/usr/bin/env python3
"""
Generates core/designsystem/.../icon/AppIcons.kt from Phosphor Icons SVGs.

Phosphor Icons (https://phosphoricons.com) are MIT licensed. Only the icons
listed below are bundled, so the APK stays small.

Usage:
    npm pack @phosphor-icons/core && tar xzf phosphor-icons-core-*.tgz
    python3 tools/generate_icons.py package/assets
"""
import re
import sys
from pathlib import Path

# Kotlin name -> (weight folder, svg file stem)
ICONS = {
    # Bottom navigation: regular when unselected, fill when selected
    "Home": ("regular", "house"),
    "HomeFilled": ("fill", "house-fill"),
    "Sales": ("regular", "receipt"),
    "SalesFilled": ("fill", "receipt-fill"),
    "Items": ("regular", "package"),
    "ItemsFilled": ("fill", "package-fill"),
    "Parties": ("regular", "users-three"),
    "PartiesFilled": ("fill", "users-three-fill"),
    "More": ("regular", "squares-four"),
    "MoreFilled": ("fill", "squares-four-fill"),
    # Two-tone feature icons
    "Receipt": ("duotone", "receipt-duotone"),
    "CashRegister": ("duotone", "cash-register-duotone"),
    "HandCoins": ("duotone", "hand-coins-duotone"),
    "Package": ("duotone", "package-duotone"),
    "ShoppingCart": ("duotone", "shopping-cart-duotone"),
    "Wallet": ("duotone", "wallet-duotone"),
    "NotePencil": ("duotone", "note-pencil-duotone"),
    "ChartBar": ("duotone", "chart-bar-duotone"),
    "ArrowDownLeft": ("bold", "arrow-down-left-bold"),
    "ArrowUpRight": ("bold", "arrow-up-right-bold"),
    "Warning": ("duotone", "warning-duotone"),
    "CloudCheck": ("duotone", "cloud-check-duotone"),
    "CloudSlash": ("duotone", "cloud-slash-duotone"),
    "Settings": ("duotone", "gear-six-duotone"),
    "Sun": ("duotone", "sun-duotone"),
    "Moon": ("duotone", "moon-duotone"),
    "DeviceMobile": ("duotone", "device-mobile-duotone"),
    "Storefront": ("duotone", "storefront-duotone"),
    "Database": ("duotone", "database-duotone"),
    "Info": ("duotone", "info-duotone"),
    "Palette": ("duotone", "palette-duotone"),
    "CurrencyInr": ("duotone", "currency-inr-duotone"),
    "Hammer": ("duotone", "hammer-duotone"),
    "Printer": ("duotone", "printer-duotone"),
    "ShieldCheck": ("duotone", "shield-check-duotone"),
    "Sparkle": ("duotone", "sparkle-duotone"),
    # Single-tone UI glyphs
    "TrendUp": ("regular", "trend-up"),
    "TrendDown": ("regular", "trend-down"),
    "Plus": ("regular", "plus"),
    "ArrowLeft": ("regular", "arrow-left"),
    "CaretRight": ("regular", "caret-right"),
    "Check": ("regular", "check"),
    "CheckCircle": ("fill", "check-circle-fill"),
    # UI glyphs added for the full app
    "Search": ("regular", "magnifying-glass"),
    "Barcode": ("regular", "barcode"),
    "Scan": ("duotone", "scan-duotone"),
    "Trash": ("regular", "trash"),
    "Edit": ("regular", "pencil-simple"),
    "Share": ("regular", "share-network"),
    "WhatsApp": ("regular", "whatsapp-logo"),
    "Phone": ("regular", "phone"),
    "User": ("duotone", "user-duotone"),
    "UserPlus": ("regular", "user-plus"),
    "PlusCircle": ("duotone", "plus-circle-duotone"),
    "Minus": ("regular", "minus"),
    "Close": ("regular", "x"),
    "Calendar": ("regular", "calendar-blank"),
    "Funnel": ("regular", "funnel"),
    "Download": ("duotone", "download-simple-duotone"),
    "Upload": ("duotone", "upload-simple-duotone"),
    "FilePdf": ("duotone", "file-pdf-duotone"),
    "FileCsv": ("duotone", "file-csv-duotone"),
    "Return": ("duotone", "arrow-u-up-left-duotone"),
    "Star": ("regular", "star"),
    "StarFilled": ("fill", "star-fill"),
    "Bluetooth": ("duotone", "bluetooth-duotone"),
    "Lock": ("duotone", "lock-simple-duotone"),
    "MoreVertical": ("regular", "dots-three-vertical"),
    "Copy": ("regular", "copy"),
    "Tag": ("duotone", "tag-duotone"),
    "Note": ("regular", "note"),
    "History": ("duotone", "clock-counter-clockwise-duotone"),
    "ChartLineUp": ("duotone", "chart-line-up-duotone"),
    "PiggyBank": ("duotone", "piggy-bank-duotone"),
    "Bank": ("duotone", "bank-duotone"),
    "QrCode": ("duotone", "qr-code-duotone"),
    "CreditCard": ("duotone", "credit-card-duotone"),
    "Money": ("duotone", "money-duotone"),
    "Scales": ("duotone", "scales-duotone"),
    "Stack": ("duotone", "stack-duotone"),
    "Truck": ("duotone", "truck-duotone"),
    "FileText": ("duotone", "file-text-duotone"),
    "ArrowRight": ("regular", "arrow-right"),
    "CaretDown": ("regular", "caret-down"),
    "Calculator": ("duotone", "calculator-duotone"),
    "Coins": ("duotone", "coins-duotone"),
    "Refresh": ("regular", "arrows-clockwise"),
    "ReceiptX": ("duotone", "receipt-x-duotone"),
    "Eye": ("regular", "eye"),
    "ListBullets": ("duotone", "list-bullets-duotone"),
    "Clipboard": ("duotone", "clipboard-text-duotone"),
    "ArrowUUpRight": ("duotone", "arrow-u-up-right-duotone"),
    "XCircle": ("regular", "x-circle"),
}

PATH_RE = re.compile(r'<path d="([^"]+)"(?: opacity="([0-9.]+)")?\s*/>')

HEADER = '''// GENERATED by tools/generate_icons.py — do not edit by hand.
// Icons: Phosphor Icons (https://phosphoricons.com), MIT License.
package online.draran.billing.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * App icon set. Duotone icons draw a 20 % tinted fill behind the outline,
 * which gives the two-tone look. All icons tint with Icon(tint = ...).
 */
object AppIcons {
'''

FOOTER = '''}

private fun icon(name: String, vararg paths: Pair<String, Float>): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).apply {
        paths.forEach { (data, alpha) ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = SolidColor(Color.Black),
                fillAlpha = alpha,
            )
        }
    }.build()
'''


def main(assets: Path, out: Path) -> None:
    body = []
    for name, (weight, stem) in ICONS.items():
        svg = (assets / weight / f"{stem}.svg").read_text()
        paths = PATH_RE.findall(svg)
        if not paths or svg.count("<path") != len(paths):
            raise SystemExit(f"Unsupported SVG content in {stem}")
        args = ",\n".join(
            f'            "{d}" to {float(op) if op else 1.0}f' for d, op in paths
        )
        body.append(
            f"    val {name}: ImageVector by lazy {{\n"
            f'        icon(\n            "{name}",\n{args},\n        )\n    }}\n'
        )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(HEADER + "\n".join(body) + FOOTER)
    print(f"Wrote {len(ICONS)} icons to {out}")


if __name__ == "__main__":
    root = Path(__file__).resolve().parent.parent
    main(
        Path(sys.argv[1]),
        root / "core/designsystem/src/main/kotlin/online/draran/billing/core/designsystem/icon/AppIcons.kt",
    )
