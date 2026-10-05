import { setupServer } from "msw/node";

/** Tests register handlers with server.use(...). Unhandled requests fail the test. */
export const server = setupServer();
