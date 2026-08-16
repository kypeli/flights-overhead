import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import "../lib/firebase.js";
import { overheadFlights } from "../lib/flights.js";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { createMockRequest, MockResponse, asResponse } from "./helpers.ts";

describe("overheadFlights endpoint (flights.ts)", () => {
  const auth = getAuth();
  const db = getFirestore();

  beforeEach(() => {
    auth.verifyIdToken = (async (token: string) => {
      if (token === "valid-token") {
        return { uid: "user-123" } as unknown as ReturnType<typeof auth.verifyIdToken> extends Promise<infer U>
          ? U
          : never;
      }
      throw new Error("Invalid token");
    }) as typeof auth.verifyIdToken;
  });

  it("rejects non-GET methods with 405 Method Not Allowed", async () => {
    const req = createMockRequest({
      method: "POST",
      headers: { authorization: "Bearer valid-token" },
    });
    const res = new MockResponse();

    await overheadFlights(req, asResponse(res));
    assert.strictEqual(res.statusCode, 405);
    assert.strictEqual(res.body, "Method Not Allowed");
  });

  it("rejects unauthenticated requests with 401 Unauthorized", async () => {
    const req = createMockRequest({
      method: "GET",
    });
    const res = new MockResponse();

    await overheadFlights(req, asResponse(res));
    assert.strictEqual(res.statusCode, 401);
  });

  it("retrieves overhead flights from active_flights collection and returns JSON array", async () => {
    const mockFlight1 = {
      hex: "4601F6",
      callsign: "FIN123",
      altitude: 35000,
      groundSpeed: 450,
      lat: 60.19,
      lon: 24.96,
    };
    const mockFlight2 = {
      hex: "4B1234",
      callsign: "SAS456",
      altitude: 28000,
      groundSpeed: 420,
      lat: 60.25,
      lon: 24.88,
    };

    let requestedCollection = "";
    db.collection = ((collectionName: string) => {
      requestedCollection = collectionName;
      return {
        get: async () => ({
          docs: [
            { data: () => mockFlight1 },
            { data: () => mockFlight2 },
          ],
        }),
      };
    }) as typeof db.collection;

    const req = createMockRequest({
      method: "GET",
      headers: { authorization: "Bearer valid-token" },
    });
    const res = new MockResponse();

    await overheadFlights(req, asResponse(res));

    assert.strictEqual(requestedCollection, "active_flights");
    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(res.body, [mockFlight1, mockFlight2]);
  });

  it("returns empty array when no active flights are present", async () => {
    db.collection = (() => ({
      get: async () => ({
        docs: [],
      }),
    })) as typeof db.collection;

    const req = createMockRequest({
      method: "GET",
      headers: { authorization: "Bearer valid-token" },
    });
    const res = new MockResponse();

    await overheadFlights(req, asResponse(res));

    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(res.body, []);
  });
});
