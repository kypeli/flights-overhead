import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import "../lib/firebase.js";
import { pushNotification } from "../lib/push-notification.js";
import { getAuth } from "firebase-admin/auth";
import { createMockRequest, MockResponse, asResponse } from "./helpers.ts";

describe("pushNotification endpoint (push-notification.ts)", () => {
  const auth = getAuth();

  beforeEach(() => {
    auth.verifyIdToken = (async (idToken: string) => {
      if (idToken === "valid-token") {
        return { uid: "user-push-test" } as unknown as ReturnType<typeof auth.verifyIdToken> extends Promise<infer U>
          ? U
          : never;
      }
      throw new Error("Invalid ID token");
    }) as typeof auth.verifyIdToken;
  });

  it("rejects non-POST methods with 405 Method Not Allowed", async () => {
    const req = createMockRequest({
      method: "GET",
      headers: { authorization: "Bearer valid-token" },
    });
    const res = new MockResponse();

    await pushNotification(req, asResponse(res));
    assert.strictEqual(res.statusCode, 405);
  });

  it("rejects unauthenticated requests with 401 Unauthorized", async () => {
    const req = createMockRequest({
      method: "POST",
      body: {},
    });
    const res = new MockResponse();

    await pushNotification(req, asResponse(res));
    assert.strictEqual(res.statusCode, 401);
  });

  it("returns 501 Not Implemented for authenticated POST invocations", async () => {
    const req = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token" },
      body: { title: "Alert" },
    });
    const res = new MockResponse();

    await pushNotification(req, asResponse(res));
    assert.strictEqual(res.statusCode, 501);
    assert.strictEqual(res.body, "Not Implemented");
  });
});
