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
        return { uid: "user-abc", email: "user@example.com" } as unknown as
          ReturnType<typeof auth.verifyIdToken> extends Promise<infer U> ? U : never;
      }
      if (idToken === "valid-token-no-email") {
        return { uid: "user-no-email" } as unknown as
          ReturnType<typeof auth.verifyIdToken> extends Promise<infer U> ? U : never;
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
      body: { installationId: "fid-123" },
    });
    const res = new MockResponse();

    await token(req, asResponse(res));
    assert.strictEqual(res.statusCode, 401);
  });

  it("registers installationId with explicit platform, email, and device info successfully", async () => {
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
        installationId: "sample-installation-id",
        platform: "ios",
        device: {
          manufacturer: "Apple",
          model: "iPhone15,2",
          osVersion: "17.4",
          appVersion: "1.0",
        },
      },
    });
    const res = new MockResponse();

    await token(req, asResponse(res));

    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(res.body, { success: true });
    assert.strictEqual(savedCollection, "fcm_tokens");
    assert.strictEqual(savedDocId, "sample-installation-id");
    assert.deepStrictEqual(savedOptions, { merge: true });

    const doc = savedData as Record<string, unknown>;
    assert.strictEqual(doc.installationId, "sample-installation-id");
    assert.strictEqual(doc.uid, "user-abc");
    assert.strictEqual(doc.email, "user@example.com");
    assert.strictEqual(doc.platform, "ios");
    assert.deepStrictEqual(doc.device, {
      manufacturer: "Apple",
      model: "iPhone15,2",
      osVersion: "17.4",
      appVersion: "1.0",
    });
    assert.strictEqual(typeof doc.updatedAt, "string");
    assert.ok(
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z$/.test(
        doc.updatedAt as string,
      ),
    );
  });

  it("uses installationId as document ID and defaults platform to android without device info", async () => {
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
        installationId: "fid-standalone",
      },
    });
    const res = new MockResponse();

    await token(req, asResponse(res));

    assert.strictEqual(res.statusCode, 200);
    assert.strictEqual(savedDocId, "fid-standalone");

    const doc = savedData as Record<string, unknown>;
    assert.strictEqual(doc.installationId, "fid-standalone");
    assert.strictEqual(doc.uid, "user-abc");
    assert.strictEqual(doc.email, "user@example.com");
    assert.strictEqual(doc.platform, "android"); // default
    assert.strictEqual(doc.device, undefined);
    assert.strictEqual(typeof doc.updatedAt, "string");
    assert.ok(
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z$/.test(
        doc.updatedAt as string,
      ),
    );
  });

  it("falls back to body email or null when auth token has no email", async () => {
    let savedData: unknown = null;

    db.collection = (() => ({
      doc: () => ({
        set: async (data: unknown) => {
          savedData = data;
        },
      }),
    })) as typeof db.collection;

    const reqWithFallback = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token-no-email" },
      body: {
        installationId: "fid-fallback",
        email: "fallback@example.com",
      },
    });
    const resWithFallback = new MockResponse();

    await token(reqWithFallback, asResponse(resWithFallback));
    assert.strictEqual(resWithFallback.statusCode, 200);
    assert.strictEqual(
      (savedData as Record<string, unknown>).email,
      "fallback@example.com",
    );

    const reqWithoutEmail = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token-no-email" },
      body: {
        installationId: "fid-no-email",
      },
    });
    const resWithoutEmail = new MockResponse();

    await token(reqWithoutEmail, asResponse(resWithoutEmail));
    assert.strictEqual(resWithoutEmail.statusCode, 200);
    assert.strictEqual((savedData as Record<string, unknown>).email, null);
  });

  describe("validation failures", () => {
    it("returns 400 when installationId is missing or empty or non-string", async () => {
      const invalidBodies = [
        {},
        { installationId: "" },
        { installationId: 123 },
        { installationId: null },
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
          "Bad Request: 'installationId' is required and must be a string",
        );
      }
    });

    it("returns 400 when installationId is not a valid Firestore document ID", async () => {
      const invalidIds = [
        "invalid/id/with/slash",
        ".",
        "..",
        "__reserved__",
      ];

      for (const id of invalidIds) {
        const req = createMockRequest({
          method: "POST",
          headers: { authorization: "Bearer valid-token" },
          body: { installationId: id },
        });
        const res = new MockResponse();

        await token(req, asResponse(res));
        assert.strictEqual(res.statusCode, 400);
        assert.strictEqual(
          res.body,
          "Bad Request: 'installationId' is invalid",
        );
      }
    });

    it("returns 400 when platform is unsupported", async () => {
      const invalidBodies = [
        { installationId: "valid-fid", platform: "windows" },
        { installationId: "valid-fid", platform: "macos" },
        { installationId: "valid-fid", platform: 123 },
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
          installationId: "fid-test",
        },
      });
      const res = new MockResponse();

      await token(req, asResponse(res));
      assert.strictEqual(res.statusCode, 500);
      assert.strictEqual(res.body, "Internal Server Error");
    });
  });
});
