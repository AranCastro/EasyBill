#!/usr/bin/env python3
"""Builds the Google Play graphics from the app's own screens.

1. ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshotsTest*" --tests "*StoreIconTest*"
   (writes app/build/outputs/roborazzi/store_raw_*.png)
2. python3 tools/store/make_store_graphics.py

Writes into fastlane/metadata/android/en-IN/images/:
  icon.png                  512 x 512, 32-bit PNG
  featureGraphic.png        1024 x 500, 24-bit PNG (no transparency)
  phoneScreenshots/N.png    1080 x 1920, 24-bit PNG, with a caption

Needs Pillow (pip install pillow); tested with Pillow 12.3.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "app/build/outputs/roborazzi"
OUT = ROOT / "fastlane/metadata/android/en-IN/images"
FONTS = ROOT / "core/designsystem/src/main/res/font"

# The app's primary colour and the purple of its gradient cards
TOP = (79, 70, 229)
BOTTOM = (124, 58, 237)
WHITE = (255, 255, 255)
SOFT = (224, 231, 255)

# Raw screen name -> caption shown above it on the store
SCREENS = [
    ("1_home", "Today's sales, dues and stock at a glance"),
    ("2_bill", "GST bills with UPI QR, shared as PDF on WhatsApp"),
    ("7_counter", "Fast counter billing for walk-in customers"),
    ("4_party", "Customer balances, statements and reminders"),
    ("3_items", "Stock with low-stock alerts and barcodes"),
    ("6_gst", "GST summary for GSTR-1 and GSTR-3B"),
    ("5_capital", "Track loans from family and what is left to repay"),
]


def font(weight: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / f"inter_{weight}.ttf"), size)


def gradient(size: tuple[int, int]) -> Image.Image:
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w * 0.35 + y / h * 0.65)
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM))
    return img


def rounded(img: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *img.size), radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def wrap(draw: ImageDraw.ImageDraw, text: str, f: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=f) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    lines.append(line)
    return lines


def paste_with_shadow(canvas: Image.Image, img: Image.Image, pos: tuple[int, int], radius: int) -> None:
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (pos[0], pos[1] + 18, pos[0] + img.width, pos[1] + img.height + 18), radius, fill=(20, 10, 60, 110),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    canvas.paste(shadow, (0, 0), shadow)
    canvas.paste(img, pos, img)


def screenshot(raw: Path, caption: str, dest: Path) -> None:
    canvas = gradient((1080, 1920)).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    f = font("bold", 64)
    lines = wrap(draw, caption, f, 940)
    y = 96
    for line in lines:
        draw.text((540, y), line, font=f, fill=WHITE, anchor="ma")
        y += 80
    screen = Image.open(raw).convert("RGB")
    top = max(y + 48, 300)
    height = 1920 - top - 70
    width = round(screen.width * height / screen.height)
    screen = rounded(screen.resize((width, height), Image.LANCZOS), 44)
    paste_with_shadow(canvas, screen, ((1080 - width) // 2, top), 44)
    canvas.convert("RGB").save(dest, optimize=True)


def feature_graphic(icon: Image.Image, dest: Path) -> None:
    canvas = gradient((1024, 500)).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    mark = rounded(icon.convert("RGB").resize((280, 280), Image.LANCZOS), 64)
    paste_with_shadow(canvas, mark, (84, 110), 64)
    x = 420
    draw.text((x, 132), "Modern", font=font("semibold", 40), fill=SOFT)
    draw.text((x, 178), "Kallaa Petti", font=font("bold", 76), fill=WHITE)
    draw.text((x, 282), "Free billing app for Indian shops", font=font("medium", 32), fill=WHITE)
    draw.text((x, 336), "GST bills · Stock · Payments · Reports", font=font("regular", 26), fill=SOFT)
    draw.text((x, 372), "Works offline · No sign-up · No ads", font=font("regular", 26), fill=SOFT)
    canvas.convert("RGB").save(dest, optimize=True)


def main() -> None:
    (OUT / "phoneScreenshots").mkdir(parents=True, exist_ok=True)
    icon = Image.open(RAW / "store_raw_icon.png").convert("RGBA")
    assert icon.size == (512, 512), icon.size
    icon.save(OUT / "icon.png", optimize=True)
    feature_graphic(icon, OUT / "featureGraphic.png")
    for old in (OUT / "phoneScreenshots").glob("*.png"):
        old.unlink()
    for i, (name, caption) in enumerate(SCREENS, start=1):
        screenshot(RAW / f"store_raw_{name}.png", caption, OUT / "phoneScreenshots" / f"{i}.png")
    print(f"Wrote icon, feature graphic and {len(SCREENS)} screenshots to {OUT}")


if __name__ == "__main__":
    main()
