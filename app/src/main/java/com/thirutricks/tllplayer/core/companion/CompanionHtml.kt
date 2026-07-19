package com.thirutricks.tllplayer.core.companion

import com.thirutricks.tllplayer.core.model.SourceType

/**
 * The HTML the [CompanionHttpServer] serves to the phone, styled to match OwnTV: the app's near-black
 * teal-tinted dark palette, Lora (the app's popup serif, served from `/lora.ttf`) for headings, and
 * the system sans for body text. Kept separate from the socket plumbing so the markup is easy to
 * iterate on and unit-testable in isolation.
 *
 * The palette mirrors [tv.own.owntv.ui.theme] dark tokens: background #040E0B, surfaces #1B211F /
 * #252B29, text #DEE4E1 / #BFC9C4, outline #3F4945, teal accent #52DBC8.
 */
internal object CompanionHtml {

    private const val CSS = """
      :root{
        color-scheme: dark;
        --bg:#040E0B; --panel:#12191700; --card:#1B211F; --card-2:#252B29;
        --line:#3F4945; --text:#DEE4E1; --muted:#BFC9C4;
        --accent:#8CEE2B; --accent-ink:#123A06; --danger:#FFB4AB;
      }
      @font-face{
        font-family:'Lora'; font-style:normal; font-weight:400 700;
        src:url('/lora.ttf') format('truetype'); font-display:swap;
      }
      *{box-sizing:border-box}
      body{
        margin:0; color:var(--text);
        font-family:system-ui,-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;
        background:
          radial-gradient(1200px 520px at 12% -12%, rgba(140,238,43,0.10) 0%, transparent 60%),
          radial-gradient(900px 460px at 100% 0%, rgba(140,238,43,0.06) 0%, transparent 62%),
          var(--bg);
        min-height:100vh;
      }
      h1,h2,.brand{font-family:'Lora',Georgia,'Times New Roman',serif; font-weight:700; letter-spacing:.2px}
      main{max-width:760px; margin:0 auto; padding:28px 20px 56px}
      .brandrow{display:flex; align-items:center; gap:12px; margin-bottom:22px}
      .dot{width:34px; height:34px; border-radius:10px; background:#52DBC8;
        display:grid; place-items:center; color:#003730}
      .dot svg{width:18px; height:18px}
      .brand{font-size:20px; color:var(--text)}
      .card{background:var(--card); border:1px solid var(--line); border-radius:20px;
        padding:26px 22px; box-shadow:0 24px 60px rgba(0,0,0,.45)}
      h1{margin:0 0 8px; font-size:24px}
      p{line-height:1.55; color:var(--muted); margin:0 0 16px}
      form{display:grid; gap:14px}
      label{display:grid; gap:6px; font-size:14px; color:var(--text)}
      input,select{border:1px solid var(--line); border-radius:12px; padding:13px 14px;
        background:#0C1311; color:var(--text); font-size:16px; width:100%}
      input:focus,select:focus{outline:none; border-color:var(--accent);
        box-shadow:0 0 0 3px rgba(140,238,43,.20)}
      .grid{display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px}
      .checks{display:grid; gap:10px; grid-template-columns:repeat(2,minmax(0,1fr)); margin-top:2px}
      .check{display:flex; align-items:center; gap:9px; color:var(--muted)}
      .check input{width:18px; height:18px}
      .tabs{display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; margin-bottom:18px}
      .tab{border:1px solid var(--line); border-radius:12px; padding:12px 10px; background:var(--card-2);
        color:var(--muted); font-weight:600; font-size:15px; cursor:pointer; font-family:inherit}
      .tab.active{border-color:var(--accent); background:#1E2E0C; color:#EAFFD0}
      .panel{display:none}
      .panel.active{display:grid; gap:14px}
      button.go{border:0; border-radius:12px; padding:15px 16px; background:var(--accent);
        color:var(--accent-ink); font-weight:700; font-size:16px; cursor:pointer; margin-top:4px}
      .pin{font-family:'Lora',Georgia,serif; font-size:34px; letter-spacing:12px; text-align:center;
        padding:16px; caret-color:var(--accent)}
      .err{color:var(--danger); font-size:14px; margin:0 0 12px}
      .hint{font-size:13px; color:var(--muted); margin-top:6px}
      a{color:var(--accent)}
      @media (max-width:640px){.grid,.checks,.tabs{grid-template-columns:1fr}}
    """

    private const val LOGO_SVG =
        """<svg viewBox="0 0 24 24" fill="none"><path d="M8 5v14l11-7z" fill="currentColor"/></svg>"""

    private fun page(title: String, inner: String): String = """
        <!doctype html><html lang="en"><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title</title>
        <style>$CSS</style>
        </head><body><main>
          <div class="brandrow"><span class="dot">$LOGO_SVG</span><span class="brand">OwnTV</span></div>
          $inner
        </main></body></html>
    """.trimIndent()

    /** The PIN gate shown when the plain QR/URL is opened without a valid PIN. */
    fun pinPage(error: String?): String = page(
        "OwnTV — Enter PIN",
        """
          <div class="card">
            <h1>Enter the PIN</h1>
            <p>Type the 6-digit PIN shown on your TV to continue.</p>
            ${if (error != null) """<p class="err">${error.escapeHtml()}</p>""" else ""}
            <form method="post" action="/">
              <input class="pin" name="pin" inputmode="numeric" pattern="[0-9]*" maxlength="6"
                     autofocus placeholder="••••••" aria-label="PIN" required>
              <button class="go" type="submit">Continue</button>
            </form>
          </div>
        """.trimIndent(),
    )

    /** The add-source form. [pin] is baked into every submit endpoint so posts stay authenticated. */
    fun formPage(pin: String): String = page(
        "OwnTV — Add source",
        """
          <div class="card">
            <h1>Add a source</h1>
            <p>Fill in one source and press Send. It appears on the TV — pick up the remote and press
               <strong>Start Import</strong> there when you are ready.</p>

            <div class="tabs">
              <button type="button" class="tab active" data-k="xtream">Xtream</button>
              <button type="button" class="tab" data-k="m3u">M3U</button>
              <button type="button" class="tab" data-k="stalker">Stalker</button>
            </div>

            <form class="panel active" data-k="xtream" method="post" action="/xtream?pin=$pin">
              <input type="hidden" name="type" value="xtream">
              <div class="grid">
                <label>Name <input name="name" placeholder="My IPTV"></label>
                ${autoRefreshSelect()}
              </div>
              <label>Server URL <input name="server" placeholder="http://host:port" required></label>
              <div class="grid">
                <label>Username <input name="user" autocomplete="username" required></label>
                <label>Password <input name="pass" type="password" autocomplete="current-password" required></label>
              </div>
              <label>User-Agent <input name="userAgent" placeholder="Optional"></label>
              <label>EPG URL <input name="epgUrl" placeholder="Optional"></label>
              <!-- Hidden "false" pairs so an unchecked box still submits a value (checkboxes post
                   nothing when off); the checkbox, posted after, overrides to true when checked. -->
              <input type="hidden" name="syncLive" value="false">
              <input type="hidden" name="syncMovies" value="false">
              <input type="hidden" name="syncSeries" value="false">
              <input type="hidden" name="isDefault" value="false">
              <div class="checks">
                <label class="check"><input type="checkbox" name="syncLive" value="true" checked> Live TV</label>
                <label class="check"><input type="checkbox" name="syncMovies" value="true" checked> Movies</label>
                <label class="check"><input type="checkbox" name="syncSeries" value="true" checked> Series</label>
                <label class="check"><input type="checkbox" name="isDefault" value="true"> Default playlist</label>
              </div>
              <button class="go" type="submit">Send to TV</button>
            </form>

            <form class="panel" data-k="m3u" method="post" action="/m3u?pin=$pin">
              <input type="hidden" name="type" value="m3u">
              <div class="grid">
                <label>Name <input name="name" placeholder="My Playlist"></label>
                ${autoRefreshSelect()}
              </div>
              <label>Playlist URL <input name="server" placeholder="http://…/playlist.m3u" required></label>
              <label>User-Agent <input name="userAgent" placeholder="Optional"></label>
              <label>EPG URL <input name="epgUrl" placeholder="Optional"></label>
              <input type="hidden" name="isDefault" value="false">
              <label class="check"><input type="checkbox" name="isDefault" value="true"> Default playlist</label>
              <button class="go" type="submit">Send to TV</button>
            </form>

            <form class="panel" data-k="stalker" method="post" action="/stalker?pin=$pin">
              <input type="hidden" name="type" value="stalker">
              <div class="grid">
                <label>Name <input name="name" placeholder="My Portal"></label>
                ${autoRefreshSelect()}
              </div>
              <label>Portal URL <input name="portalUrl" placeholder="http://host:port/c/" required></label>
              <label>MAC address <input name="mac" placeholder="00:1A:79:AA:BB:CC" required></label>
              <label>User-Agent <input name="userAgent" placeholder="Optional"></label>
              <input type="hidden" name="isDefault" value="false">
              <label class="check"><input type="checkbox" name="isDefault" value="true"> Default playlist</label>
              <button class="go" type="submit">Send to TV</button>
            </form>
          </div>
          <script>
            var tabs=document.querySelectorAll('.tab'), panels=document.querySelectorAll('.panel');
            tabs.forEach(function(t){t.addEventListener('click',function(){
              var k=t.getAttribute('data-k');
              tabs.forEach(function(x){x.classList.toggle('active',x===t)});
              panels.forEach(function(p){p.classList.toggle('active',p.getAttribute('data-k')===k)});
            });});
          </script>
        """.trimIndent(),
    )

    /** Confirmation after a submission — the details are now waiting on the TV. */
    fun savedPage(payload: CompanionPayload, pin: String): String {
        val name = payload.name.ifBlank {
            when (payload.type) {
                SourceType.STALKER -> "My Portal"
                SourceType.M3U -> "My Playlist"
                else -> "My IPTV"
            }
        }
        return page(
            "OwnTV — Sent",
            """
              <div class="card">
                <h1>Sent to your TV ✓</h1>
                <p><strong>${name.escapeHtml()}</strong> (${payload.type.name}) is now on the TV's Add source
                   screen. Check the details there and press <strong>Start Import</strong> on the remote.</p>
                <p><a href="/?pin=$pin">Send another source</a></p>
              </div>
            """.trimIndent(),
        )
    }

    private fun autoRefreshSelect(): String = """
        <label>Auto refresh
          <select name="autoRefresh">
            <option value="OFF" selected>Off</option>
            <option value="STARTUP">Refresh at startup</option>
            <option value="HOURS_6">Every 6 hours</option>
            <option value="HOURS_12">Every 12 hours</option>
            <option value="HOURS_24">Every 24 hours</option>
            <option value="HOURS_48">Every 48 hours</option>
          </select>
        </label>
    """.trimIndent()

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
