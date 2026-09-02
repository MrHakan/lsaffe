package com.deckwatch.feature.report

/**
 * The inline stylesheet and the inline interactive layer of §13.2.
 *
 * Both are constants, both are embedded verbatim: **no CDN, no `fetch`, no external reference of
 * any kind**. The file has to open by double-click on a laptop with no network, on a phone in a
 * cabin, and print from a ship's office printer.
 */
internal object ReportAssets {

    /**
     * Screen + print stylesheet.
     *
     * Print rules follow §13.4: A4 with a 14 mm margin, no dark backgrounds (an ink-heavy report
     * is a report nobody prints twice), `thead` repeated on every page via
     * `display: table-header-group`, and `break-inside: avoid` on the blocks that must not be
     * split — a deck plan, a signature block, a deficiency card.
     */
    val CSS: String = """
        :root {
          --ink: #10151f;
          --ink-soft: #47526a;
          --rule: #c9ced8;
          --rule-soft: #e4e7ec;
          --ground: #ffffff;
          --ground-alt: #f4f5f7;
          --accent: #1c2536;
          --good: #1B873F;
          --acceptable: #6FA82C;
          --monitor: #E8A317;
          --defective: #E5661B;
          --outofservice: #C2261B;
          --notchecked: #8A8F98;
        }
        * { box-sizing: border-box; }
        html { -webkit-text-size-adjust: 100%; }
        body {
          margin: 0;
          background: var(--ground-alt);
          color: var(--ink);
          font: 14px/1.45 -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
        }
        #report {
          max-width: 1000px;
          margin: 0 auto;
          padding: 24px 20px 64px;
          background: var(--ground);
        }
        h1, h2, h3 { margin: 0 0 6px; font-weight: 600; line-height: 1.2; }
        h1 { font-size: 22px; letter-spacing: 0.01em; }
        h2 { font-size: 16px; text-transform: uppercase; letter-spacing: 0.06em; color: var(--ink-soft); }
        h3 { font-size: 14px; }
        p { margin: 0 0 8px; }
        a { color: var(--accent); }
        .mono { font-family: ui-monospace, "SFMono-Regular", "Cascadia Mono", Consolas, "Liberation Mono", monospace; }

        header.doc {
          border-top: 4px solid var(--accent);
          border-bottom: 1px solid var(--rule);
          padding: 14px 0 12px;
          margin-bottom: 16px;
        }
        header.doc .type {
          font-size: 11px; text-transform: uppercase; letter-spacing: 0.14em; color: var(--ink-soft);
        }
        header.doc .vessel { font-size: 24px; font-weight: 700; margin: 2px 0 6px; }
        .particulars { display: flex; flex-wrap: wrap; gap: 4px 24px; font-size: 12px; color: var(--ink-soft); }
        .particulars span b { color: var(--ink); font-weight: 600; }

        .summary { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 18px; }
        .stat {
          border: 1px solid var(--rule-soft); border-left-width: 4px; border-radius: 4px;
          padding: 6px 10px; min-width: 96px; background: var(--ground);
        }
        .stat .n { font-size: 18px; font-weight: 700; line-height: 1.1; }
        .stat .l { font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--ink-soft); }

        section.block { margin: 0 0 22px; }
        section.block > h2 { border-bottom: 1px solid var(--rule-soft); padding-bottom: 4px; margin-bottom: 10px; }

        table { border-collapse: collapse; width: 100%; font-size: 12px; }
        thead { display: table-header-group; }
        tfoot { display: table-footer-group; }
        tr { break-inside: avoid; page-break-inside: avoid; }
        th, td { border-bottom: 1px solid var(--rule-soft); padding: 5px 6px; text-align: left; vertical-align: top; }
        th {
          border-bottom: 1px solid var(--rule); font-size: 10px; text-transform: uppercase;
          letter-spacing: 0.07em; color: var(--ink-soft); background: var(--ground-alt);
        }
        td.num, th.num { text-align: right; white-space: nowrap; }
        tbody tr:nth-child(even) td { background: #fbfbfc; }

        .chip {
          display: inline-block; border-radius: 4px; padding: 1px 6px; font-size: 10px;
          font-weight: 600; letter-spacing: 0.03em; color: #ffffff; white-space: nowrap;
        }
        .chip.hollow { background: transparent !important; color: var(--ink-soft); border: 1px solid var(--rule); }

        .plan { display: flex; flex-wrap: wrap; gap: 18px; align-items: flex-start; break-inside: avoid; }
        .plan figure { margin: 0; flex: 0 0 auto; }
        .plan figcaption { font-size: 11px; color: var(--ink-soft); margin-top: 4px; }
        svg.deckplan { background: var(--ground); border: 1px solid var(--rule-soft); border-radius: 4px; }
        svg.deckplan .hull { fill: #eef0f4; stroke: #47526a; stroke-width: 2; }
        svg.deckplan .zone { fill-opacity: 0.18; stroke-opacity: 0.55; stroke-width: 1; }
        svg.deckplan text.mk { font: 600 11px sans-serif; fill: #ffffff; }
        svg.deckplan text.mkdark { font: 600 11px sans-serif; fill: #10151f; }
        .legend { flex: 1 1 320px; min-width: 280px; }

        .photos { display: flex; flex-wrap: wrap; gap: 6px; margin: 6px 0 0; }
        .photos img { max-height: 150px; border: 1px solid var(--rule-soft); border-radius: 3px; }
        .photo-missing {
          display: inline-flex; align-items: center; justify-content: center; width: 110px; height: 84px;
          border: 1px dashed var(--rule); border-radius: 3px; font-size: 10px; color: var(--ink-soft);
          text-align: center; padding: 4px;
        }

        .card { border: 1px solid var(--rule-soft); border-left-width: 4px; border-radius: 4px; padding: 8px 10px; margin: 0 0 8px; break-inside: avoid; }
        .card h3 { margin-bottom: 2px; }
        .card .meta { font-size: 11px; color: var(--ink-soft); margin-bottom: 4px; }

        .signature { display: flex; flex-wrap: wrap; gap: 24px; margin-top: 14px; break-inside: avoid; }
        .signature div { flex: 1 1 220px; }
        .signature .rule { border-bottom: 1px solid var(--ink); height: 34px; }
        .signature .cap { font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--ink-soft); margin-top: 4px; }

        footer.doc {
          margin-top: 28px; border-top: 1px solid var(--rule); padding-top: 10px;
          font-size: 10.5px; line-height: 1.5; color: var(--ink-soft);
        }
        footer.doc .disclaimer { font-weight: 600; color: var(--ink); }

        .controls { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 0 0 14px; }
        .controls input[type="search"] {
          flex: 1 1 220px; min-height: 34px; padding: 6px 10px; font-size: 13px;
          border: 1px solid var(--rule); border-radius: 4px; background: var(--ground); color: var(--ink);
        }
        .controls button {
          min-height: 34px; padding: 6px 12px; font-size: 12px; cursor: pointer;
          border: 1px solid var(--rule); border-radius: 4px; background: var(--ground); color: var(--ink);
        }
        .controls button[aria-selected="true"] { background: var(--accent); color: #ffffff; border-color: var(--accent); }
        .controls .count { font-size: 11px; color: var(--ink-soft); }
        .js-only { display: none; }
        .interactive .js-only { display: flex; }
        .hidden-row { display: none; }

        @media print {
          @page { size: A4; margin: 14mm; }
          body { background: #ffffff; }
          #report { max-width: none; padding: 0; }
          .controls, .js-only { display: none !important; }
          tbody tr:nth-child(even) td { background: transparent; }
          .stat, .card, svg.deckplan { border-color: #999; }
          section.block { break-inside: auto; }
          h2 { break-after: avoid; page-break-after: avoid; }
          .page-break { break-before: page; page-break-before: always; }
          a[href]:after { content: ""; }
        }
    """.trimIndent()

    /**
     * The interactive layer: deck-switching tabs, a filter box and an SVG re-render of each deck
     * plan from the JSON block.
     *
     * Everything it does is an *enhancement*. The static `#report` div already contains the plans,
     * the legends and every table, so the file is complete with JavaScript disabled (§13.2) — this
     * script only adds the controls, which is why they live behind `.js-only`.
     *
     * Written in plain ES5-compatible script so it runs in an old WebView or a mail client's
     * preview pane, and deliberately free of `${'$'}` so it survives being a Kotlin raw string.
     */
    val JS: String = """
        (function () {
          "use strict";
          var node = document.getElementById("deckwatch-data");
          var data = null;
          try { data = JSON.parse(node ? node.textContent : "null"); } catch (e) { data = null; }
          if (!data) { return; }
          document.body.className += " interactive";
          // Symbol-key -> signage ground colour, emitted by the renderer just above this script so
          // the re-rendered markers match the printed ones exactly (§10.3, §14).
          var grounds = window.DW_GROUNDS || {};

          function el(name, attrs, text) {
            var n = document.createElementNS("http://www.w3.org/2000/svg", name);
            for (var k in attrs) { if (attrs.hasOwnProperty(k)) { n.setAttribute(k, attrs[k]); } }
            if (text !== undefined && text !== null) { n.appendChild(document.createTextNode(text)); }
            return n;
          }

          // ---- Re-render every deck plan's markers from the payload, so the interactive plan and
          // ---- the printed one come from one source of truth.
          var byDeck = {};
          var items = data.equipment || [];
          for (var i = 0; i < items.length; i++) {
            var it = items[i];
            if (it.deletedAt) { continue; }
            if (!it.deckId) { continue; }
            if (!byDeck[it.deckId]) { byDeck[it.deckId] = []; }
            byDeck[it.deckId].push(it);
          }
          var plans = document.querySelectorAll("svg.deckplan");
          for (var p = 0; p < plans.length; p++) {
            var svg = plans[p];
            var deckId = svg.getAttribute("data-deck-id");
            var layer = svg.querySelector("g.markers");
            if (!layer || !deckId) { continue; }
            var w = parseFloat(svg.getAttribute("data-w")) || 0;
            var h = parseFloat(svg.getAttribute("data-h")) || 0;
            var list = (byDeck[deckId] || []).slice().sort(function (a, b) {
              return (a.tag || "").localeCompare(b.tag || "");
            });
            while (layer.firstChild) { layer.removeChild(layer.firstChild); }
            for (var m = 0; m < list.length; m++) {
              var eq = list[m];
              var x = Math.min(1, Math.max(0, eq.posX || 0)) * w;
              var y = Math.min(1, Math.max(0, eq.posY || 0)) * h;
              var g = el("g", { "class": "marker", "data-id": eq.id, "data-tag": eq.tag || "" });
              g.appendChild(el("rect", {
                x: (x - 11).toFixed(2), y: (y - 11).toFixed(2), width: "22", height: "22",
                rx: "5", ry: "5", fill: grounds[eq.symbolKey] || "#5C6779",
                stroke: "#ffffff", "stroke-width": "1.5"
              }));
              g.appendChild(el("text", {
                x: x.toFixed(2), y: (y + 4).toFixed(2), "class": "mk", "text-anchor": "middle"
              }, String(m + 1)));
              g.appendChild(el("title", {}, (eq.tag || "") + (eq.name ? " — " + eq.name : "")));
              layer.appendChild(g);
            }
          }

          // ---- Deck tabs: show one deck section at a time.
          var tabBars = document.querySelectorAll("[data-deck-tabs]");
          for (var t = 0; t < tabBars.length; t++) {
            (function (bar) {
              var buttons = bar.querySelectorAll("button[data-deck-target]");
              function select(id) {
                var sections = document.querySelectorAll("[data-deck-section]");
                for (var s = 0; s < sections.length; s++) {
                  var match = id === "ALL" || sections[s].getAttribute("data-deck-section") === id;
                  sections[s].style.display = match ? "" : "none";
                }
                for (var b = 0; b < buttons.length; b++) {
                  buttons[b].setAttribute("aria-selected",
                    buttons[b].getAttribute("data-deck-target") === id ? "true" : "false");
                }
              }
              for (var b = 0; b < buttons.length; b++) {
                (function (btn) {
                  btn.addEventListener("click", function () { select(btn.getAttribute("data-deck-target")); });
                })(buttons[b]);
              }
              bar.style.display = "";
            })(tabBars[t]);
          }

          // ---- Free-text filter over every row that opted in with data-filter.
          var box = document.getElementById("dw-filter");
          var counter = document.getElementById("dw-filter-count");
          if (box) {
            box.addEventListener("input", function () {
              var needle = box.value.toLowerCase().trim();
              var rows = document.querySelectorAll("[data-filter]");
              var shown = 0;
              for (var r = 0; r < rows.length; r++) {
                var hit = needle === "" || rows[r].getAttribute("data-filter").indexOf(needle) >= 0;
                if (hit) { rows[r].className = rows[r].className.replace(/\s*hidden-row/g, ""); shown++; }
                else if (rows[r].className.indexOf("hidden-row") < 0) { rows[r].className += " hidden-row"; }
              }
              if (counter) { counter.textContent = shown + " / " + rows.length; }
            });
          }
        })();
    """.trimIndent()
}
