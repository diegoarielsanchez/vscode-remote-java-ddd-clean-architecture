import { z } from "zod";
import { http } from "../../core/http";
import { sessionStore } from "../../core/session";

const LoginResponseSchema = z.object({
  token: z.string().min(1),
  username: z.string(),
  roles: z.array(z.string()).default([]),
});

/** identity-service via the gateway's /auth route. */
export const authRepository = {
  async login(username: string, password: string): Promise<void> {
    const res = await http("auth/login", {
      method: "POST",
      json: { username: username.trim(), password },
      schema: LoginResponseSchema,
    });
    sessionStore.start(res.token, { username: res.username, roles: res.roles });
  },

  logout() {
    sessionStore.end("logout");
  },
};
