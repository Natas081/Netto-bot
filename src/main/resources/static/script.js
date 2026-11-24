const form = document.querySelector("#chat-form");
const input = document.querySelector("#message-input");
const sendButton = document.querySelector("#send-button");
const inputError = document.querySelector("#input-error");
const messages = document.querySelector("#messages");
const resultTones = new Map([
    ["CONFIÁVEL", "tone-safe"],
    ["SUSPEITO", "tone-suspicious"],
    ["PERIGOSO", "tone-dangerous"],
]);

function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
}

function addMessage(sender, text, status) {
    const isBot = sender === "bot";
    const row = createElement("div", `message message-${sender}`);
    const content = createElement("div", "message-content");
    const bubble = createElement("div", "bubble");

    if (isBot) {
        const avatar = createElement("span", "message-avatar");
        avatar.setAttribute("aria-hidden", "true");
        avatar.append(createElement("i", "icon icon-shield"));
        row.append(avatar);
    }

    const tone = resultTones.get(status);
    if (tone) {
        const label = createElement("div", `message-status ${tone}`);
        const dot = createElement("span", "status-dot");
        dot.setAttribute("aria-hidden", "true");
        label.append(dot, status);
        bubble.append(label);
    }

    bubble.append(createElement("p", null, text));
    content.append(createElement("p", "message-author", isBot ? "LinkGuard Bot" : "Você"), bubble);
    row.append(content);
    messages.append(row);
    messages.scrollTop = messages.scrollHeight;
    return row;
}

function setInputError(message = "") {
    inputError.textContent = message;
    inputError.hidden = !message;
    if (message) {
        input.setAttribute("aria-invalid", "true");
    } else {
        input.removeAttribute("aria-invalid");
    }
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (sendButton.disabled) return;

    const message = input.value;
    input.focus();
    if (!message.trim()) {
        setInputError("Digite ou cole um link para enviar.");
        return;
    }

    setInputError();
    addMessage("user", message.trim());
    input.value = "";
    sendButton.disabled = true;
    const pending = addMessage("bot", "Analisando o link…");
    pending.classList.add("pending");

    let reply;
    try {
        const response = await fetch("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ message }),
            signal: AbortSignal.timeout(10000),
        });
        if (!response.ok && response.status !== 400) {
            throw new Error(`HTTP ${response.status}`);
        }
        reply = await response.json();
        if (typeof reply.reply !== "string") {
            throw new Error("Resposta inesperada do servidor");
        }
    } catch {
        reply = { reply: "O bot não conseguiu responder. Verifique se o servidor está em execução e tente novamente." };
        if (!input.value) input.value = message;
    } finally {
        pending.remove();
        sendButton.disabled = false;
    }

    addMessage("bot", reply.reply, reply.status);
});

input.addEventListener("input", () => setInputError());

document.querySelectorAll(".example").forEach((button) => {
    button.addEventListener("click", () => {
        input.value = button.querySelector("code").textContent;
        setInputError();
        input.focus();
    });
});
