(() => {
  function setMsg(el, text, type) {
    if (!el) return;
    el.classList.remove("hidden");
    el.classList.remove("info", "success", "warn", "danger");
    if (type) el.classList.add(type);
    el.textContent = text;
  }

  async function postJson(url, body) {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      let detail = "";
      try {
        const t = await res.text();
        detail = t ? ` (${t.slice(0, 200)})` : "";
      } catch (_) {}
      throw new Error(`${res.status}${detail}`);
    }
    try {
      return await res.json();
    } catch (_) {
      return {};
    }
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
      const root = document.querySelector("main.container");
      const hasSources = root ? root.dataset.hasSources === "true" : true;
      if (!hasSources) {
        setMsg(msg, "수집 대상(활성 키워드/기관)이 없습니다. 먼저 키워드 또는 기관을 활성화하세요.", "warn");
        return;
      }

      if (btn.dataset.mode === "full") {
        const fromEl = document.getElementById("fullFrom");
        const toEl = document.getElementById("fullTo");
        const from = fromEl ? fromEl.value : "";
        const to = toEl ? toEl.value : "";
        if (!from || !to) {
          setMsg(msg, "전체 수집은 from/to 날짜를 모두 입력하세요.", "warn");
          return;
        }
        if (from > to) {
          setMsg(msg, "from은 to보다 클 수 없습니다.", "warn");
          return;
        }

        const ok = confirm(
          `전체 수집(Full)은 시간이 오래 걸릴 수 있고 OpenAlex 레이트리밋에 걸릴 수 있습니다.\n\n범위: ${from} ~ ${to}\n\n계속 진행할까요?`
        );
        if (!ok) return;

        const res = await postJson("/api/admin/ingest/run", { mode: btn.dataset.mode, from, to });
        if (res && res.status === "error") {
          setMsg(msg, res.message || "전체 수집 시작 실패", "danger");
          return;
        }
        setMsg(msg, `수집을 시작했습니다. (모드: ${btn.dataset.mode}) 새로고침 버튼으로 진행 상황을 확인하세요.`, "info");
        return;
      }

      await postJson("/api/admin/ingest/run", { mode: btn.dataset.mode });
      setMsg(msg, `수집을 시작했습니다. (모드: ${btn.dataset.mode}) 새로고침 버튼으로 진행 상황을 확인하세요.`, "info");
    } catch (e) {
      setMsg(msg, `수집 시작 실패: ${e && e.message ? e.message : ""}`.trim(), "danger");
    }
  });

  onClick('button[data-action="ingest-cleanup"]', async (btn) => {
    const msg = document.getElementById("ingestMsg");
    const minutes = Number(btn.dataset.minutes || "30");
    const ok = confirm(
      `오래된 running job을 정리합니다.\n\n- 기준: ${minutes}분 이상 실행 중\n- 결과: failed로 마감\n\n계속 진행할까요?`
    );
    if (!ok) return;

    try {
      const res = await postJson("/api/admin/ingest/cleanup", { olderThanMinutes: minutes });
      setMsg(msg, `정리 완료: ${res.cleaned}건 (기준 ${res.olderThanMinutes}분)`, "success");
      setTimeout(() => location.reload(), 800);
    } catch (e) {
      setMsg(msg, `정리 실패: ${e && e.message ? e.message : ""}`.trim(), "danger");
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

  onClick('button[data-action="maintenance-normalize-inst-ids"]', async () => {
    const msg = document.getElementById("maintenanceInstIdMsg");
    const batch = document.getElementById("instIdBatch");
    const max = document.getElementById("instIdMax");
    const batchSize = batch ? Number(batch.value || "200") : 200;
    const maxDocs = max ? Number(max.value || "5000") : 5000;
    try {
      const res = await postJson("/api/admin/maintenance/normalize-work-institution-ids", {
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
    const keywordMsg = document.getElementById("keywordMsg");
    const manualKeyword = document.getElementById("manualKeyword");
    const manualBtn = document.querySelector('button[data-action="enable-keyword-manual"]');
    if (manualBtn) {
      manualBtn.addEventListener("click", async () => {
        const value = manualKeyword ? manualKeyword.value : "";
        if (!value || value.trim().length < 2) {
          setMsg(keywordMsg, "키워드를 2글자 이상 입력하세요.", "warn");
          return;
        }
        try {
          await postJson("/api/admin/keywords/enable", { keyword: value });
          setMsg(keywordMsg, "저장했습니다. 곧 새로고침합니다.", "success");
          setTimeout(() => location.reload(), 400);
        } catch (e) {
          setMsg(keywordMsg, `저장 실패: ${e && e.message ? e.message : ""}`.trim(), "danger");
        }
      });
      if (manualKeyword) {
        manualKeyword.addEventListener("keydown", (e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            manualBtn.click();
          }
        });
      }
    }

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
        if (msg) msg.classList.add("hidden");
        try {
          const saved = await postJson("/api/admin/institutions/add", {
            instId: instId ? instId.value : "",
            displayName: displayName ? displayName.value : "",
            alias: alias ? alias.value : "",
          });
          setMsg(msg, `저장했습니다: ${saved.displayName} (${saved.id})`, "success");
          setTimeout(() => location.reload(), 400);
        } catch (e) {
          setMsg(msg, "저장 실패: 기관 ID(OpenAlex) 형식/값을 확인하세요.", "danger");
        }
      });
    }

    const ingestRoot = document.querySelector("main.container");
    if (ingestRoot) {
      const runningCount = Number(ingestRoot.dataset.runningCount || "0");
      if (runningCount > 0) {
        setInterval(() => location.reload(), 15000);
      }
    }
  });

  onClick('button[data-action="ingest-refresh"]', async () => {
    location.reload();
  });
})();
