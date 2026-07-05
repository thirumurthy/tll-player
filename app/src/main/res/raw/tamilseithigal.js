(function () {
  // tamilseithigal.in – YouTube-embed live TV pages
  //
  // Strategy: find the YouTube <iframe> in the live DOM (it may be injected asynchronously
  // by the page's own JS/ads), then:
  //   1. Grant allow="autoplay; fullscreen; encrypted-media" + allowfullscreen BEFORE any
  //      (re)load that needs them, so the permission is attached in time.
  //   2. Style the iframe IN PLACE (never re-parented) using explicit pixel dimensions
  //      driven by visualViewport, kept in sync on resize/orientation change.
  //   3. Only then rewrite src to add autoplay=1 / mute=0 / playsinline=1, so any reload
  //      happens once size + position + permissions are already settled.
  //   4. Hide everything else via a <style> tag.
  //
  // Why explicit pixel sizing instead of 100vw/100vh: WebView viewport units can be
  // unreliable (overscan, nav bars, TV chrome), so we measure window.visualViewport
  // directly and set width/height in px, re-measuring on resize/scroll/orientation change.
  //
  // Why the iframe is never re-parented: re-parenting a live, already-loading/playing
  // iframe (plus resizing it at the same time as rewriting its src) is a common trigger
  // for the classic WebView bug where audio keeps playing but the video surface renders
  // black. Fixed positioning covers the viewport without moving the node anywhere.
  //
  // mediaPlaybackRequiresUserGesture=false at the WebView level makes autoplay reliable
  // without any synthetic click/gesture tricks.
  //
  // Idempotent: guarded by iframe.dataset.fullscreened so the MutationObserver won't
  // reprocess/reload the same iframe more than once.

  "use strict";

  var TAG = "[tamilseithigal]";
  var IFRAME_ID = "_owntv_yt_iframe";
  var STYLE_ID = "_owntv_fs_style";

  function mergeQueryParam(src, key, value) {
    var sep = src.indexOf("?") !== -1 ? "&" : "?";
    var re = new RegExp("([?&])" + key + "=[^&]*");
    if (re.test(src)) {
      return src.replace(re, "$1" + key + "=" + value);
    }
    return src + sep + key + "=" + value;
  }

  function forceQueryParam(src, key, value) {
    var target = key + "=" + value;
    return src.indexOf(target) !== -1 ? src : mergeQueryParam(src, key, value);
  }

  function applyFullscreen(iframe) {
    if (iframe.dataset.fullscreened) return; // idempotent guard
    iframe.dataset.fullscreened = "true";

    console.log(TAG + " iframe found, original src: " + iframe.src);

    var src = iframe.src;
    var isYoutubeEmbed = /youtube(?:-nocookie)?\.com\/embed/.test(src);
    if (!isYoutubeEmbed) {
      console.log(
        TAG +
          " iframe src does not look like a YouTube embed, skipping: " +
          src,
      );
      iframe.dataset.fullscreened = "";
      return;
    }

    // --- 1. Grant permissions BEFORE the reload that will need them ---
    iframe.setAttribute(
      "allow",
      "autoplay; fullscreen; encrypted-media; picture-in-picture",
    );
    iframe.setAttribute("allowfullscreen", "true");

    // --- 2. Style the iframe IN PLACE, sized in real pixels via visualViewport ---
    iframe.id = iframe.id || IFRAME_ID;

    function resizeIframe() {
      var vp = window.visualViewport;
      var w = vp ? Math.round(vp.width) : document.documentElement.clientWidth;
      var h = vp
        ? Math.round(vp.height)
        : document.documentElement.clientHeight;
      iframe.style.setProperty("position", "fixed", "important");
      iframe.style.setProperty("top", "0", "important");
      iframe.style.setProperty("left", "0", "important");
      iframe.style.setProperty("width", w + "px", "important");
      iframe.style.setProperty("height", h + "px", "important");
      iframe.style.setProperty("border", "none", "important");
      iframe.style.setProperty("margin", "0", "important");
      iframe.style.setProperty("padding", "0", "important");
      iframe.style.setProperty("z-index", "2147483647", "important");
      iframe.style.setProperty("background", "#000", "important");
      iframe.style.setProperty("display", "block", "important");
      iframe.style.setProperty("visibility", "visible", "important");
      iframe.style.setProperty("pointer-events", "auto", "important");
      console.log(
        TAG +
          " resized iframe to " +
          w +
          "x" +
          h +
          " (computed height: " +
          getComputedStyle(iframe).height +
          ")",
      );
    }

    resizeIframe();
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", resizeIframe);
      window.visualViewport.addEventListener("scroll", resizeIframe);
    }
    window.addEventListener("resize", resizeIframe);
    window.addEventListener("orientationchange", function () {
      setTimeout(resizeIframe, 100);
    });

    // --- 3. Hide everything else on the page ---
    if (!document.getElementById(STYLE_ID)) {
      var style = document.createElement("style");
      style.id = STYLE_ID;
      style.textContent = [
        "html, body { overflow: hidden !important; background: #000 !important; margin: 0 !important; }",
        "body *:not(#" + iframe.id + ") {",
        "  visibility: hidden !important;",
        "  pointer-events: none !important;",
        "}",
      ].join("\n");
      (document.head || document.documentElement).appendChild(style);
      console.log(TAG + " injected fullscreen style");
    }

    // --- 4. Rewrite src LAST, once size/position/permissions are already settled ---
    if (!src.includes("autoplay=1")) {
      src = mergeQueryParam(src, "autoplay", "1");
    }
    src = forceQueryParam(src, "mute", "0");
    if (!src.includes("playsinline=1")) {
      src = mergeQueryParam(src, "playsinline", "1");
    }

    if (src !== iframe.src) {
      console.log(TAG + " rewritten src: " + src);
      iframe.src = src;
    } else {
      console.log(TAG + " src already correct, no reload needed");
    }

    console.log(TAG + " fullscreen applied in place (no re-parenting)");
  }

  function findAndApply() {
    var iframes = document.querySelectorAll(
      'iframe[src*="youtube.com/embed"], iframe[src*="youtube-nocookie.com/embed"]',
    );
    if (iframes.length > 0) {
      console.log(
        TAG +
          " found " +
          iframes.length +
          " YouTube embed iframe(s), applying to first",
      );
      applyFullscreen(iframes[0]);
      return true;
    }
    return false;
  }

  // --- Immediate check (iframe may already be in the DOM) ---
  if (!findAndApply()) {
    console.log(TAG + " iframe not found yet, installing MutationObserver");

    var observer = new MutationObserver(function (mutations) {
      for (var i = 0; i < mutations.length; i++) {
        var nodes = mutations[i].addedNodes;
        for (var j = 0; j < nodes.length; j++) {
          var node = nodes[j];
          if (!node || node.nodeType !== 1) continue;
          if (node.tagName === "IFRAME") {
            var s = node.src || "";
            if (/youtube(?:-nocookie)?\.com\/embed/.test(s)) {
              console.log(
                TAG + " MutationObserver: YouTube iframe added directly",
              );
              applyFullscreen(node);
              observer.disconnect();
              return;
            }
          }
          var inner = node.querySelectorAll
            ? node.querySelectorAll(
                'iframe[src*="youtube.com/embed"], iframe[src*="youtube-nocookie.com/embed"]',
              )
            : [];
          if (inner.length > 0) {
            console.log(
              TAG +
                " MutationObserver: YouTube iframe found inside added subtree",
            );
            applyFullscreen(inner[0]);
            observer.disconnect();
            return;
          }
        }
        if (
          mutations[i].type === "attributes" &&
          mutations[i].target.tagName === "IFRAME"
        ) {
          var target = mutations[i].target;
          if (/youtube(?:-nocookie)?\.com\/embed/.test(target.src || "")) {
            console.log(
              TAG + " MutationObserver: YouTube iframe src attribute set",
            );
            applyFullscreen(target);
            observer.disconnect();
            return;
          }
        }
      }
    });

    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["src"],
    });

    setTimeout(function () {
      observer.disconnect();
      if (!findAndApply()) {
        console.log(
          TAG +
            " MutationObserver timed out, no YouTube iframe found after 30 s",
        );
      }
    }, 30000);
  }
})();
