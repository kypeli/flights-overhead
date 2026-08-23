import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import "../lib/firebase.js";
import { onGet, onPost, onServicePost, oauth2Client } from "../lib/http.js";
import { getAuth } from "firebase-admin/auth";
import { createMockRequest, MockResponse, asResponse } from "./helpers.ts";

describe("HTTP Middleware & Authentication (http.ts)", () => {
  const auth = getAuth();

  beforeEach(() => {
    // Setup default auth verifyIdToken mock behavior
    auth.verifyIdToken = (async (token: string) => {
      if (token === "valid-token-user-1") {
        return { uid: "user-1" } as unknown as
          ReturnType<typeof auth.verifyIdToken> extends Promise<infer U> ? U : never;
      }
      if (token === "valid-token-user-2") {
        return { uid: "user-2" } as unknown as
          ReturnType<typeof auth.verifyIdToken> extends Promise<infer U> ? U : never;
      }
      throw new Error("Decoding Firebase ID token failed");
    }) as typeof auth.verifyIdToken;

    // Setup default oauth2Client verifyIdToken mock behavior
    oauth2Client.verifyIdToken = (async (options: { idToken: string; audience?: string | string[] }) => {
      if (options.idToken === "valid-service-token") {
        return {
          getPayload: () => ({
            email: "service-account@flights-overhead.iam.gserviceaccount.com",
            sub: "sa-12345",
          }),
        } as unknown as ReturnType<typeof oauth2Client.verifyIdToken> extends Promise<infer U>
          ? U
          : never;
      }
      throw new Error("Invalid service ID token");
    }) as typeof oauth2Client.verifyIdToken;
  });

  describe("onGet", () => {
    it("returns 405 Method Not Allowed when called with non-GET methods", async () => {
      const handler = onGet(async (req, res) => {
        res.status(200).send("OK");
      });

      const methods = ["POST", "PUT", "DELETE", "PATCH"];
      for (const method of methods) {
        const req = createMockRequest({
          method,
          headers: { authorization: "Bearer valid-token-user-1" },
        });
        const res = new MockResponse();
        await handler(req, asResponse(res));

        assert.strictEqual(res.statusCode, 405);
        assert.strictEqual(res.body, "Method Not Allowed");
      }
    });

    it("returns 401 when Authorization header is missing", async () => {
      let handlerCalled = false;
      const handler = onGet(async (req, res) => {
        handlerCalled = true;
        res.status(200).send("OK");
      });

      const req = createMockRequest({ method: "GET" });
      const res = new MockResponse();
      await handler(req, asResponse(res));

      assert.strictEqual(handlerCalled, false);
      assert.strictEqual(res.statusCode, 401);
      assert.strictEqual(res.body, "Unauthorized: Missing or invalid token format");
    });

    it("returns 401 when Authorization header does not start with Bearer", async () => {
      let handlerCalled = false;
      const handler = onGet(async (req, res) => {
        handlerCalled = true;
        res.status(200).send("OK");
      });

      const invalidHeaders = [
        "Basic token123",
        "Token token123",
        "bearer lowercase",
        "Bearer",
      ];

      for (const authHeader of invalidHeaders) {
        const req = createMockRequest({
          method: "GET",
          headers: { authorization: authHeader },
        });
        const res = new MockResponse();
        await handler(req, asResponse(res));

        assert.strictEqual(handlerCalled, false);
        assert.strictEqual(res.statusCode, 401);
        assert.strictEqual(res.body, "Unauthorized: Missing or invalid token format");
      }
    });

    it("returns 401 when token verification fails", async () => {
      let handlerCalled = false;
      const handler = onGet(async (req, res) => {
        handlerCalled = true;
        res.status(200).send("OK");
      });

      const req = createMockRequest({
        method: "GET",
        headers: { authorization: "Bearer invalid-or-expired-token" },
      });
      const res = new MockResponse();
      await handler(req, asResponse(res));

      assert.strictEqual(handlerCalled, false);
      assert.strictEqual(res.statusCode, 401);
      assert.strictEqual(res.body, "Unauthorized: Invalid ID token");
    });

    it("passes authenticated uid to handler on successful verification", async () => {
      let receivedUid = "";
      const handler = onGet(async (req, res, uid) => {
        receivedUid = uid;
        res.status(200).json({ success: true, user: uid });
      });

      const req = createMockRequest({
        method: "GET",
        headers: { authorization: "Bearer valid-token-user-1" },
      });
      const res = new MockResponse();
      await handler(req, asResponse(res));

      assert.strictEqual(receivedUid, "user-1");
      assert.strictEqual(res.statusCode, 200);
      assert.deepStrictEqual(res.body, { success: true, user: "user-1" });
    });
  });

  describe("onPost", () => {
    it("returns 405 Method Not Allowed when called with GET or other methods", async () => {
      const handler = onPost(async (req, res) => {
        res.status(200).send("OK");
      });

      const methods = ["GET", "PUT", "DELETE"];
      for (const method of methods) {
        const req = createMockRequest({
          method,
          headers: { authorization: "Bearer valid-token-user-2" },
        });
        const res = new MockResponse();
        await handler(req, asResponse(res));

        assert.strictEqual(res.statusCode, 405);
        assert.strictEqual(res.body, "Method Not Allowed");
      }
    });

    it("verifies credentials and calls handler with POST method", async () => {
      let receivedUid = "";
      const handler = onPost(async (req, res, uid) => {
        receivedUid = uid;
        res.status(200).json({ received: req.body });
      });

      const req = createMockRequest({
        method: "POST",
        headers: { authorization: "Bearer valid-token-user-2" },
        body: { key: "value" },
      });
      const res = new MockResponse();
      await handler(req, asResponse(res));

      assert.strictEqual(receivedUid, "user-2");
      assert.strictEqual(res.statusCode, 200);
      assert.deepStrictEqual(res.body, { received: { key: "value" } });
    });
  });

  describe("onServicePost", () => {
    it("returns 405 Method Not Allowed when called with GET or other methods", async () => {
      const handler = onServicePost(async (req, res) => {
        res.status(200).send("OK");
      });

      const methods = ["GET", "PUT", "DELETE"];
      for (const method of methods) {
        const req = createMockRequest({
          method,
          headers: { authorization: "Bearer valid-service-token" },
        });
        const res = new MockResponse();
        await handler(req, asResponse(res));

        assert.strictEqual(res.statusCode, 405);
        assert.strictEqual(res.body, "Method Not Allowed");
      }
    });

    it("returns 401 when Authorization header is missing", async () => {
      let handlerCalled = false;
      const handler = onServicePost(async (req, res) => {
        handlerCalled = true;
        res.status(200).send("OK");
      });

      const req = createMockRequest({ method: "POST" });
      const res = new MockResponse();
      await handler(req, asResponse(res));

      assert.strictEqual(handlerCalled, false);
      assert.strictEqual(res.statusCode, 401);
      assert.strictEqual(res.body, "Unauthorized: Missing or invalid token format");
    });

    it("returns 401 when service token verification fails", async () => {
      let handlerCalled = false;
      const handler = onServicePost(async (req, res) => {
        handlerCalled = true;
        res.status(200).send("OK");
      });

      const req = createMockRequest({
        method: "POST",
        headers: { authorization: "Bearer invalid-token" },
      });
      const res = new MockResponse();
      await handler(req, asResponse(res));

      assert.strictEqual(handlerCalled, false);
      assert.strictEqual(res.statusCode, 401);
      assert.strictEqual(res.body, "Unauthorized: Invalid ID token");
    });

    it("passes authenticated service caller to handler on successful verification", async () => {
      let receivedEmail = "";
      let receivedSub = "";
      const handler = onServicePost(async (req, res, caller) => {
        receivedEmail = caller.email ?? "";
        receivedSub = caller.sub;
        res.status(200).json({ success: true, sub: caller.sub });
      });

      const req = createMockRequest({
        method: "POST",
        headers: { authorization: "Bearer valid-service-token" },
        body: { data: "test" },
      });
      const res = new MockResponse();
      await handler(req, asResponse(res));

      assert.strictEqual(receivedEmail, "service-account@flights-overhead.iam.gserviceaccount.com");
      assert.strictEqual(receivedSub, "sa-12345");
      assert.strictEqual(res.statusCode, 200);
      assert.deepStrictEqual(res.body, { success: true, sub: "sa-12345" });
    });
  });
});
