import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http as mock, HttpResponse } from "msw";
import { afterEach, describe, expect, it } from "vitest";
import { sessionStore } from "../../core/session";
import { renderView } from "../../test/render";
import { server } from "../../test/server";
import { LoginView } from "./LoginView";

afterEach(() => sessionStore.end("logout"));

describe("LoginView", () => {
  it("starts a session on success and keeps the token out of web storage", async () => {
    server.use(mock.post("*/auth/login", () => HttpResponse.json({ token: "jwt-abc", username: "rep1", roles: ["MSR"] })));
    const user = userEvent.setup();
    renderView(<LoginView />);

    await user.type(screen.getByLabelText("Username"), "rep1");
    await user.type(screen.getByLabelText("Password"), "secret");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(sessionStore.get()).toEqual({ username: "rep1", roles: ["MSR"] }));
    expect(JSON.stringify({ ...localStorage })).not.toContain("jwt-abc");
    expect(JSON.stringify({ ...sessionStorage })).not.toContain("jwt-abc");
  });

  it("shows the generic error and clears the password on failure", async () => {
    server.use(mock.post("*/auth/login", () => HttpResponse.json({ error: "Invalid credentials" }, { status: 401 })));
    const user = userEvent.setup();
    renderView(<LoginView />);

    await user.type(screen.getByLabelText("Username"), "rep1");
    await user.type(screen.getByLabelText("Password"), "wrong");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid credentials");
    expect(screen.getByLabelText("Password")).toHaveValue("");
    expect(sessionStore.get()).toBeNull();
  });
});

