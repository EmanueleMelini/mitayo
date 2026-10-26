// Shared by every page: picks the language (saved choice, else the browser's) and wires the
// IT/EN switch. Pages listen to "mitayo:lang" to redraw what they build at runtime.
(function () {
  const LANGS = ["it", "en"];
  const KEY = "mitayo.lang";

  function saved() {
    try {
      return localStorage.getItem(KEY);
    } catch (e) {
      return null;
    }
  }

  function browserLang() {
    const tags = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || ""];
    for (const tag of tags) {
      const lang = String(tag).toLowerCase().split("-")[0];
      if (LANGS.includes(lang)) return lang;
    }
    return "en";
  }

  function apply(lang) {
    const root = document.documentElement;
    root.dataset.lang = lang;
    root.lang = lang;
    document.querySelectorAll(".lang button").forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.lang === lang));
    });
    const title = root.dataset["title" + lang.toUpperCase()];
    if (title) document.title = title;
    document.dispatchEvent(new CustomEvent("mitayo:lang", { detail: lang }));
  }

  window.mitayoLang = () => document.documentElement.dataset.lang;

  const first = saved();
  apply(LANGS.includes(first) ? first : browserLang());

  document.addEventListener("DOMContentLoaded", () => {
    apply(window.mitayoLang());
    document.querySelectorAll(".lang button").forEach((button) => {
      button.addEventListener("click", () => {
        try {
          localStorage.setItem(KEY, button.dataset.lang);
        } catch (e) {
          // Private window or blocked storage: the choice lasts until the page is closed
        }
        apply(button.dataset.lang);
      });
    });
  });
})();
