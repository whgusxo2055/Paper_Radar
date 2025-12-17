(() => {
  const MIN_LEN = 2;
  const DEBOUNCE_MS = 200;

  function debounce(fn, ms) {
    let timer = null;
    return (...args) => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => fn(...args), ms);
    };
  }

  function ensureBox(input) {
    let box = input.parentElement.querySelector(".suggest-box");
    if (box) return box;

    box = document.createElement("div");
    box.className = "suggest-box hidden";
    input.parentElement.style.position = "relative";
    input.parentElement.appendChild(box);
    return box;
  }

  function hide(box) {
    box.classList.add("hidden");
    box.innerHTML = "";
  }

  function show(box, items, onPick) {
    box.innerHTML = "";
    items.forEach((item) => {
      const row = document.createElement("button");
      row.type = "button";
      row.className = "suggest-item";
      row.textContent = item.label;
      row.addEventListener("click", () => onPick(item));
      box.appendChild(row);
    });
    box.classList.toggle("hidden", items.length === 0);
  }

  async function fetchSuggest(endpoint, prefix) {
    const url = `${endpoint}?prefix=${encodeURIComponent(prefix)}&size=10`;
    const res = await fetch(url, { headers: { Accept: "application/json" } });
    if (!res.ok) return [];
    return res.json();
  }

  function attachSuggest(inputId, endpoint, applyValue) {
    const input = document.getElementById(inputId);
    if (!input) return;

    const box = ensureBox(input);
    const run = debounce(async () => {
      const prefix = input.value || "";
      if (prefix.length < MIN_LEN) {
        hide(box);
        return;
      }
      const items = await fetchSuggest(endpoint, prefix);
      show(box, items, (it) => {
        applyValue(it);
        hide(box);
      });
    }, DEBOUNCE_MS);

    input.addEventListener("input", run);
    input.addEventListener("blur", () => setTimeout(() => hide(box), 150));
    input.addEventListener("focus", run);
  }

  document.addEventListener("DOMContentLoaded", () => {
    attachSuggest("keyword", "/api/suggest/keyword", (it) => {
      const el = document.getElementById("keyword");
      if (el) el.value = it.value;
    });
    attachSuggest("author", "/api/suggest/author", (it) => {
      const el = document.getElementById("author");
      if (el) el.value = it.value;
    });
    attachSuggest("instId", "/api/suggest/institution", (it) => {
      const el = document.getElementById("instId");
      if (el) el.value = it.value;
    });
  });
})();

