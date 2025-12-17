(() => {
  async function postJson(url, body) {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`${res.status}`);
    return res.json();
  }

  function onClick(selector, handler) {
    document.addEventListener("click", (e) => {
      const el = e.target.closest(selector);
      if (!el) return;
      handler(el);
    });
  }

  onClick('button[data-action="enable-keyword"]', async (btn) => {
    await postJson("/api/admin/keywords/enable", { keyword: btn.dataset.keyword });
    location.reload();
  });

  onClick('button[data-action="disable-keyword"]', async (btn) => {
    await postJson("/api/admin/keywords/disable", { keyword: btn.dataset.keyword });
    location.reload();
  });

  onClick('button[data-action="enable-institution"]', async (btn) => {
    await postJson("/api/admin/institutions/enable", { instId: btn.dataset.instId });
    location.reload();
  });

  onClick('button[data-action="disable-institution"]', async (btn) => {
    await postJson("/api/admin/institutions/disable", { instId: btn.dataset.instId });
    location.reload();
  });

  onClick('button[data-action="ingest-run"]', async (btn) => {
    const msg = document.getElementById("ingestMsg");
    try {
      await postJson("/api/admin/ingest/run", { mode: btn.dataset.mode });
      if (msg) {
        msg.classList.remove("hidden");
        msg.textContent = `수집 시작됨: ${btn.dataset.mode}`;
      }
      setTimeout(() => location.reload(), 600);
    } catch (e) {
      if (msg) {
        msg.classList.remove("hidden");
        msg.textContent = "수집 시작 실패";
      }
    }
  });

  onClick('button[data-action="maintenance-backfill"]', async () => {
    const msg = document.getElementById("maintenanceMsg");
    const batch = document.getElementById("backfillBatch");
    const max = document.getElementById("backfillMax");
    const batchSize = batch ? Number(batch.value || "200") : 200;
    const maxDocs = max ? Number(max.value || "2000") : 2000;
    try {
      const res = await postJson("/api/admin/maintenance/recompute-work-links", {
        batchSize,
        maxDocs,
      });
      if (msg) {
        msg.classList.remove("hidden");
        msg.textContent = res.status === "busy" ? "이미 실행 중입니다." : "시작했습니다. 완료 후 새로고침하세요.";
      }
      setTimeout(() => location.reload(), 800);
    } catch (e) {
      if (msg) {
        msg.classList.remove("hidden");
        msg.textContent = "실행 실패";
      }
    }
  });

  async function fetchSuggestInstitutions(prefix) {
    const url = `/api/suggest/institution?prefix=${encodeURIComponent(prefix)}&size=10`;
    const res = await fetch(url, { headers: { Accept: "application/json" } });
    if (!res.ok) return [];
    return res.json();
  }

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

  document.addEventListener("DOMContentLoaded", () => {
    const search = document.getElementById("instSearch");
    const instId = document.getElementById("instIdAdmin");
    const displayName = document.getElementById("displayNameAdmin");
    const alias = document.getElementById("aliasAdmin");
    const addBtn = document.getElementById("addInstitutionBtn");
    const msg = document.getElementById("adminInstMsg");

    if (search) {
      const box = ensureBox(search);
      const run = debounce(async () => {
        const q = search.value || "";
        if (q.length < 2) {
          hide(box);
          return;
        }
        const items = await fetchSuggestInstitutions(q);
        show(box, items, (it) => {
          if (instId) instId.value = it.value;
          if (displayName) displayName.value = it.label;
          hide(box);
        });
      }, 200);
      search.addEventListener("input", run);
      search.addEventListener("blur", () => setTimeout(() => hide(box), 150));
      search.addEventListener("focus", run);
    }

    if (addBtn) {
      addBtn.addEventListener("click", async () => {
        if (msg) {
          msg.classList.add("hidden");
          msg.textContent = "";
        }
        try {
          const saved = await postJson("/api/admin/institutions/add", {
            instId: instId ? instId.value : "",
            displayName: displayName ? displayName.value : "",
            alias: alias ? alias.value : "",
          });
          if (msg) {
            msg.classList.remove("hidden");
            msg.textContent = `저장됨: ${saved.displayName} (${saved.id})`;
          }
          setTimeout(() => location.reload(), 400);
        } catch (e) {
          if (msg) {
            msg.classList.remove("hidden");
            msg.textContent = "저장 실패: 입력값을 확인하세요.";
          }
        }
      });
    }
  });
})();
