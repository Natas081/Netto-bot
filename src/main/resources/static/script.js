const form = document.querySelector("#chat-form");
const input = document.querySelector("#message-input");
const sendButton = document.querySelector("#send-button");
const clearButton = document.querySelector("#clear-button");
const inputError = document.querySelector("#input-error");
const messages = document.querySelector("#messages");
const activity = document.querySelector("#bot-activity");
const resultStyles = new Map([
    ["CONFIÁVEL", { tone: "tone-safe", title: "Confiável · Nenhuma ameaça detectada" }],
    ["SUSPEITO", { tone: "tone-suspicious", title: "Suspeito" }],
    ["PERIGOSO", { tone: "tone-dangerous", title: "Perigoso" }],
    ["EM_ANALISE", { tone: "tone-pending", title: "Em análise" }],
    ["INCONCLUSIVO", { tone: "tone-inconclusive", title: "Inconclusivo" }],
]);
const timeFormat = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" });
const dateFormat = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
let conversationController;
let conversationVersion = 0;
let busy = false;

function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function scrollToLatest() {
    messages.scrollTop = messages.scrollHeight;
}

function appendTime(bubble) {
    const now = new Date();
    const time = element("time", "message-time", timeFormat.format(now));
    time.dateTime = now.toISOString();
    time.title = dateFormat.format(now);
    bubble.append(time);
}

function addMessage(sender, text, isTip = false) {
    const row = element("div", `message message-${sender}`);
    const bubble = element("div", "bubble");
    if (sender === "bot") bubble.append(element("p", "message-author", "Netto"));
    if (isTip) {
        const heading = element("p", "tip-heading", "Dica de segurança");
        bubble.append(heading);
    }
    bubble.append(element("p", "message-text", text));
    appendTime(bubble);
    row.append(bubble);
    messages.append(row);
    scrollToLatest();
    return bubble;
}

function renderResult(bubble, result) {
    const style = resultStyles.get(result.status);
    bubble.replaceChildren(element("p", "message-author", "Netto"));
    bubble.append(element("p", `result-title ${style.tone}`, style.title));
    if (result.domain) bubble.append(element("p", "result-domain", result.domain));
    if (result.status === "EM_ANALISE") {
        const line = element("div", "pending-line");
        const typing = element("span", "typing-indicator");
        typing.setAttribute("aria-hidden", "true");
        typing.append(element("span"), element("span"), element("span"));
        line.append(typing, element("p", "message-text", result.reply));
        bubble.append(line);
    } else {
        bubble.append(element("p", "message-text", result.reply));
    }
    if (result.stats) {
        const stats = element("dl", "result-stats");
        const entries = [
            ["Maliciosos", "malicious", "tone-dangerous"],
            ["Suspeitos", "suspicious", "tone-suspicious"],
            ["Sem ameaças", "harmless", "tone-safe"],
            ["Sem classificação", "undetected", "tone-inconclusive"],
        ];
        for (const [label, name, tone] of entries) {
            const count = result.stats[name];
            if (!Number.isInteger(count) || count < 0) continue;
            const group = element("div", tone);
            group.append(element("dt", null, label), element("dd", null, String(count)));
            stats.append(group);
        }
        bubble.append(stats, element("p", "report-note", "Sem classificação indica mecanismos sem parecer sobre a URL."));
    }
    const metadata = [];
    if (result.source) metadata.push(`Fonte · ${result.source}`);
    if (result.analyzedAt) {
        const date = new Date(result.analyzedAt);
        if (!Number.isNaN(date.getTime())) metadata.push(`Última análise · ${dateFormat.format(date)}`);
    }
    if (metadata.length) bubble.append(element("p", "report-meta", metadata.join("\n")));
    appendTime(bubble);
    scrollToLatest();
}

function setBusy(value) {
    busy = value;
    sendButton.disabled = value;
    activity.textContent = value ? "Verificando o link…" : "Assistente de segurança digital";
    activity.classList.toggle("is-active", value);
}

function setInputError(text = "") {
    inputError.textContent = text;
    inputError.hidden = !text;
    input.setAttribute("aria-invalid", String(Boolean(text)));
}

async function requestJson(path, options, signal) {
    const response = await fetch(path, {
        ...options,
        cache: "no-store",
        signal: AbortSignal.any([signal, AbortSignal.timeout(25000)]),
    });
    if (!response.ok && response.status !== 400) throw new Error("Falha de comunicação");
    return response.json();
}

function waitForPoll(milliseconds, signal) {
    return new Promise((resolve, reject) => {
        if (signal.aborted) return reject(signal.reason);
        const cancelled = () => {
            clearTimeout(timer);
            reject(signal.reason);
        };
        const timer = setTimeout(() => {
            signal.removeEventListener("abort", cancelled);
            resolve();
        }, milliseconds);
        signal.addEventListener("abort", cancelled, { once: true });
    });
}

function checkResult(result) {
    if (!resultStyles.has(result.status) || typeof result.reply !== "string") {
        throw new Error("Resposta inesperada");
    }
}

async function startConversation() {
    conversationController?.abort();
    conversationController = new AbortController();
    const signal = conversationController.signal;
    const version = ++conversationVersion;
    setBusy(false);
    setInputError();
    input.value = "";
    messages.replaceChildren(element("p", "day-label", "Hoje"));
    addMessage("bot", "Olá! Eu sou o Netto, seu assistente de segurança digital. Envie um link e vou verificar se existem possíveis ameaças.");
    const tip = addMessage("bot", "Buscando uma dica…", true);
    try {
        const result = await requestJson("/api/tips/random", {}, signal);
        if (typeof result.tip !== "string" || !result.tip) throw new Error("Dica indisponível");
        if (version !== conversationVersion) return;
        tip.querySelector(".message-text").textContent = result.tip;
    } catch {
        if (signal.aborted) return;
        tip.querySelector(".message-text").textContent = "Não consegui carregar a dica agora. Você pode continuar enviando links para verificar.";
    }
    scrollToLatest();
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (busy) return;
    const message = input.value;
    input.focus();
    if (!message.trim()) {
        setInputError("Digite ou cole um link para verificar.");
        return;
    }
    const version = conversationVersion;
    const signal = conversationController.signal;
    setInputError();
    addMessage("user", message.trim());
    input.value = "";
    setBusy(true);
    const bubble = addMessage("bot", "Consultando o VirusTotal…");
    renderResult(bubble, { status: "EM_ANALISE", reply: "Consultando o VirusTotal…" });
    try {
        let result = await requestJson("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ message }),
        }, signal);
        let polls = 0;
        while (version === conversationVersion) {
            checkResult(result);
            renderResult(bubble, result);
            if (result.status !== "EM_ANALISE") break;
            if (!/^[0-9a-f-]{36}$/i.test(result.analysisId) || polls >= 10) {
                throw new Error("Acompanhamento encerrado");
            }
            const delay = Number.isFinite(result.retryAfterSeconds)
                ? Math.max(20, Math.min(60, result.retryAfterSeconds)) : 20;
            await waitForPoll(delay * 1000, signal);
            result = await requestJson(`/api/chat/analyses/${encodeURIComponent(result.analysisId)}`, {}, signal);
            polls++;
        }
    } catch {
        if (signal.aborted || version !== conversationVersion) return;
        renderResult(bubble, {
            status: "INCONCLUSIVO",
            reply: "Não consegui concluir a verificação agora. Isso não significa que o link seja seguro. Verifique sua conexão e tente novamente mais tarde.",
        });
        if (!input.value) input.value = message;
    } finally {
        if (version === conversationVersion) setBusy(false);
    }
});

input.addEventListener("input", () => setInputError());
clearButton.addEventListener("click", () => {
    startConversation();
    input.focus();
});
new ResizeObserver(scrollToLatest).observe(messages);
document.fonts.ready.then(scrollToLatest);
startConversation();
