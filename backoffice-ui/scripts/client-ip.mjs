// Preloaded by `npm start` and the container (node --import). Appends the TCP peer address to
// X-Forwarded-For, as any reverse proxy does. Next only fills the header when it is absent, so
// without this a browser could send its own value and pick its gateway rate-limit bucket.
// src/server/client-ip.ts reads the address from the trusted (right-hand) end.
import http from "node:http";

const emit = http.Server.prototype.emit;
http.Server.prototype.emit = function (event, req, ...rest) {
  if (event === "request") {
    const peer = req.socket.remoteAddress?.replace(/^::ffff:/, "");
    const sent = req.headers["x-forwarded-for"];
    if (peer) req.headers["x-forwarded-for"] = sent ? `${sent}, ${peer}` : peer;
  }
  return emit.call(this, event, req, ...rest);
};
process.env.PEER_IP_APPENDED = "1";
