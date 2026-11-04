"""Baut index.html (installierbare iPhone-App) aus game.html (Vorschau-Datei).

    python3 build.py
"""
from pathlib import Path

here = Path(__file__).parent
s = (here / "game.html").read_text()
title_end = s.index("</title>") + len("</title>")
style_start = s.index("<style>")
style_end = s.index("</style>") + len("</style>")

head = """<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Sandpit Stakes</title>
<link rel="manifest" href="manifest.webmanifest">
<meta name="theme-color" content="#0f0c09">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<meta name="apple-mobile-web-app-title" content="Stakes">
<link rel="apple-touch-icon" href="icon-180.png">
"""
reset = ("<style>\n  :root { padding-top: env(safe-area-inset-top, 0px); padding-bottom: env(safe-area-inset-bottom, 0px); }\n"
         "  * { box-sizing: border-box; }\n  [hidden] { display: none !important; }\n  html, body { margin: 0; }\n"
         "  body { overscroll-behavior: none; user-select: none; -webkit-user-select: none; }")

out = (head + s[title_end:style_start].strip() + "\n" + s[style_start:style_end].replace("<style>", reset, 1)
       + "\n</head>\n<body>\n" + s[style_end:].strip() + "\n</body>\n</html>\n")
(here / "index.html").write_text(out)
print("index.html gebaut,", len(out), "Zeichen")
