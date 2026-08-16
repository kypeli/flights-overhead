import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { token } from "../lib/token.js";
import { db } from "../lib/firebase.js";
import { getAuth } from "firebase-admin/auth";
import { createMockRequest, MockResponse, asResponse } from "./helpers.ts";

describe("token endpoint (token.ts)", () => {
  const auth = getAuth();

  beforeEach(() => {
    auth.verifyIdToken = (async (idToken: string) => {
      if (idToken === "valid-token") {
        return { uid: "user-abc" } as unknown as ReturnType<typeof auth.verifyIdToken> extends Promise<infer U>
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

    await token(req, asResponse(res));
    assert.strictEqual(res.statusCode, 405);
  });

  it("rejects unauthenticated requests with 401 Unauthorized", async () => {
    const req = createMockRequest({
      method: "POST",
      body: { token: "fcm-token-123" },
    });
    const res = new MockResponse();

    await token(req, asResponse(res));
    assert.strictEqual(res.statusCode, 401);
  });

  it("registers token with deviceId and explicit platform successfully", async () => {
    let savedCollection = "";
    let savedDocId = "";
    let savedData: unknown = null;
    let savedOptions: unknown = null;

    db.collection = ((collectionName: string) => {
      savedCollection = collectionName;
      return {
        doc: (docId: string) => {
          savedDocId = docId;
          return {
            set: async (data: unknown, options: unknown) => {
              savedData = data;
              savedOptions = options;
            },
          };
        },
      };
    }) as typeof db.collection;

    const req = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token" },
      body: {
        token: "sample-fcm-token",
        deviceId: "device-uuid-1",
        platform: "ios",
      },
    });
    const res = new MockResponse();

    await token(req, asResponse(res));

    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(res.body, { success: true });
    assert.strictEqual(savedCollection, "fcm_tokens");
    assert.strictEqual(savedDocId, "device-uuid-1");
    assert.deepStrictEqual(savedOptions, { merge: true });

    const doc = savedData as Record<string, unknown>;
    assert.strictEqual(doc.token, "sample-fcm-token");
    assert.strictEqual(doc.uid, "user-abc");
    assert.strictEqual(doc.deviceId, "device-uuid-1");
    assert.strictEqual(doc.platform, "ios");
    assert.ok(doc.updatedAt);
  });

  it("uses fcmToken as document ID and sets deviceId to null when deviceId is omitted", async () => {
    let savedDocId = "";
    let savedData: unknown = null;

    db.collection = (() => ({
      doc: (docId: string) => {
        savedDocId = docId;
        return {
          set: async (data: unknown) => {
            savedData = data;
          },
        };
      },
    })) as typeof db.collection;

    const req = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token" },
      body: {
        token: "fcm-standalone-token",
      },
    });
    const res = new MockResponse();

    await token(req, asResponse(res));

    assert.strictEqual(res.statusCode, 200);
    assert.strictEqual(savedDocId, "fcm-standalone-token");

    const doc = savedData as Record<string, unknown>;
    assert.strictEqual(doc.token, "fcm-standalone-token");
    assert.strictEqual(doc.deviceId, null);
    assert.strictEqual(doc.platform, "android"); // default
  });

  describe("validation failures", () => {
    it("returns 400 when token is missing or empty", async () => {
      const invalidBodies = [
        {},
        { token: "" },
        { token: 123 },
        { token: null },
      ];

      for (const body of invalidBodies) {
        const req = createMockRequest({
          method: "POST",
          headers: { authorization: "Bearer valid-token" },
          body,
        });
        const res = new MockResponse();

        await token(req, asResponse(res));
        assert.strictEqual(res.statusCode, 400);
        assert.strictEqual(res.body, "Bad Request: 'token' is required and must be a string");
      }
    });

    it("returns 400 when deviceId is invalid or non-string", async () => {
      const invalidBodies = [
        { token: "valid-tok", deviceId: "invalid/id" },
        { token: "valid-tok", deviceId: "." },
        { token: "valid-tok", deviceId: ".." },
        { token: "valid-tok", deviceId: "__reserved__" },
        { token: "valid-tok", deviceId: 123 },
      ];

      for (const body of invalidBodies) {
        const req = createMockRequest({
          method: "POST",
          headers: { authorization: "Bearer valid-token" },
          body,
        });
        const res = new MockResponse();

        await token(req, asResponse(res));
        assert.strictEqual(res.statusCode, 400);
        assert.strictEqual(res.body, "Bad Request: 'deviceId' is invalid");
      }
    });

    it("returns 400 when platform is unsupported", async () => {
      const invalidBodies = [
        { token: "valid-tok", platform: "windows" },
        { token: "valid-tok", platform: "macos" },
        { token: "valid-tok", platform: 123 },
      ];

      for (const body of invalidBodies) {
        const req = createMockRequest({
          method: "POST",
          headers: { authorization: "Bearer valid-token" },
          body,
        });
        const res = new MockResponse();

        await token(req, asResponse(res));
        assert.strictEqual(res.statusCode, 400);
        assert.strictEqual(
          res.body,
          "Bad Request: 'platform' must be one of android, ios, web",
        );
      }
    });
  });

  describe("database error handling", () => {
    it("returns 500 when Firestore write fails", async () => {
      db.collection = (() => ({
        doc: () => ({
          set: async () => {
            throw new Error("Firestore connection unavailable");
          },
        }),
      })) as typeof db.collection;

      const req = createMockRequest({
        method: "POST",
        headers: { authorization: "Bearer valid-token" },
        body: {
          token: "fcm-tok",
        },
      });
      const res = new MockResponse();

      await token(req, asResponse(res));
      assert.strictEqual(res.statusCode, 500);
      assert.strictEqual(res.body, "Internal Server Error");
    });
  });
});
