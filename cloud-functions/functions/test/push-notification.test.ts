import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import "../lib/firebase.js";
import { pushNotification } from "../lib/push-notification.js";
import { oauth2Client } from "../lib/http.js";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { createMockRequest, MockResponse, asResponse } from "./helpers.ts";

describe("pushNotification endpoint (push-notification.ts)", () => {
  const db = getFirestore();
  const messaging = getMessaging();

  beforeEach(() => {
    oauth2Client.verifyIdToken = (async (options: { idToken: string; audience?: string | string[] }) => {
      if (options.idToken === "valid-token") {
        return {
          getPayload: () => ({
            email: "service-account@flights-overhead.iam.gserviceaccount.com",
            sub: "sa-12345",
          }),
        } as unknown as ReturnType<typeof oauth2Client.verifyIdToken> extends Promise<infer U>
          ? U
          : never;
      }
      throw new Error("Invalid ID token");
    }) as typeof oauth2Client.verifyIdToken;
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
      body: { hex: "4601F6" },
    });
    const res = new MockResponse();

    await pushNotification(req, asResponse(res));
    assert.strictEqual(res.statusCode, 401);
  });

  it("rejects missing or invalid hex with 400 Bad Request", async () => {
    const invalidBodies = [
      {},
      { hex: "" },
      { hex: "   " },
      { hex: 123 },
    ];

    for (const body of invalidBodies) {
      const req = createMockRequest({
        method: "POST",
        headers: { authorization: "Bearer valid-token" },
        body,
      });
      const res = new MockResponse();

      await pushNotification(req, asResponse(res));
      assert.strictEqual(res.statusCode, 400);
      assert.strictEqual(res.body, "Bad Request: 'hex' is required and must be a string");
    }
  });

  it("returns 200 with sentCount 0 when no device tokens are registered", async () => {
    db.collection = (() => ({
      get: async () => ({
        docs: [],
      }),
    })) as typeof db.collection;

    const req = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token" },
      body: { hex: "4601F6", callsign: "FIN123", distanceKm: 5.4 },
    });
    const res = new MockResponse();

    await pushNotification(req, asResponse(res));
    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(res.body, { success: true, message: "No registered devices", sentCount: 0 });
  });

  it("dispatches multicast FCM message to registered device tokens", async () => {
    let capturedMulticastMessage: unknown = null;

    db.collection = ((collectionName: string) => {
      assert.strictEqual(collectionName, "fcm_tokens");
      return {
        get: async () => ({
          docs: [
            { id: "token-1", data: () => ({ installationId: "token-1" }) },
            { id: "token-2", data: () => ({ installationId: "token-2" }) },
          ],
        }),
      };
    }) as typeof db.collection;

    messaging.sendEachForMulticast = (async (message: unknown) => {
      capturedMulticastMessage = message;
      return {
        successCount: 2,
        failureCount: 0,
        responses: [],
      };
    }) as typeof messaging.sendEachForMulticast;

    const req = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token" },
      body: {
        hex: "4601F6",
        callsign: "FIN123",
        distanceKm: 8.2,
        altitude: 4500,
        model: "A320",
        manufacturer: "Airbus",
        originIATA: "HEL",
        destIATA: "OUL",
      },
    });
    const res = new MockResponse();

    await pushNotification(req, asResponse(res));
    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(res.body, {
      success: true,
      sentCount: 2,
      failureCount: 0,
    });

    const msg = capturedMulticastMessage as {
      tokens: string[];
      notification: { title: string; body: string };
      data: Record<string, string>;
    };

    assert.ok(msg);
    assert.deepStrictEqual(msg.tokens, ["token-1", "token-2"]);
    assert.strictEqual(msg.notification.title, "Flight FIN123 Overhead");
    assert.strictEqual(msg.notification.body, "FIN123 is 8.2 km away.");
    assert.strictEqual(msg.data.hex, "4601F6");
    assert.strictEqual(msg.data.callsign, "FIN123");
    assert.strictEqual(msg.data.distanceKm, "8.2");
    assert.strictEqual(msg.data.altitude, "4500");
    assert.strictEqual(msg.data.model, "A320");
    assert.strictEqual(msg.data.manufacturer, "Airbus");
    assert.strictEqual(msg.data.origin, "HEL");
    assert.strictEqual(msg.data.destination, "OUL");
  });

  it("handles fallback title and body when callsign and distance are omitted", async () => {
    let capturedMulticastMessage: unknown = null;

    db.collection = (() => ({
      get: async () => ({
        docs: [
          { id: "token-abc", data: () => ({ installationId: "token-abc" }) },
        ],
      }),
    })) as typeof db.collection;

    messaging.sendEachForMulticast = (async (message: unknown) => {
      capturedMulticastMessage = message;
      return {
        successCount: 1,
        failureCount: 0,
        responses: [],
      };
    }) as typeof messaging.sendEachForMulticast;

    const req = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token" },
      body: {
        hex: "4006EA",
      },
    });
    const res = new MockResponse();

    await pushNotification(req, asResponse(res));
    assert.strictEqual(res.statusCode, 200);

    const msg = capturedMulticastMessage as {
      tokens: string[];
      notification: { title: string; body: string };
      data: Record<string, string>;
    };

    assert.ok(msg);
    assert.strictEqual(msg.notification.title, "Aircraft 4006EA Overhead");
    assert.strictEqual(msg.notification.body, "4006EA is nearby.");
    assert.strictEqual(msg.data.hex, "4006EA");
  });

  it("returns 500 when database or messaging query throws an error", async () => {
    db.collection = (() => ({
      get: async () => {
        throw new Error("Database timeout");
      },
    })) as typeof db.collection;

    const req = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token" },
      body: { hex: "4601F6" },
    });
    const res = new MockResponse();

    await pushNotification(req, asResponse(res));
    assert.strictEqual(res.statusCode, 500);
    assert.strictEqual(res.body, "Internal Server Error");
  });
});
