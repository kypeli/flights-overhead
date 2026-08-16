import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { isValidDeviceId, ALLOWED_PLATFORMS } from "../lib/validation.js";

describe("Validation", () => {
  describe("ALLOWED_PLATFORMS", () => {
    it("contains exactly android, ios, and web", () => {
      assert.deepStrictEqual(ALLOWED_PLATFORMS, ["android", "ios", "web"]);
      assert.strictEqual(ALLOWED_PLATFORMS.length, 3);
    });
  });

  describe("isValidDeviceId", () => {
    it("returns true for standard alphanumeric and hyphenated device IDs", () => {
      assert.strictEqual(isValidDeviceId("device-123"), true);
      assert.strictEqual(isValidDeviceId("uuid-v4-550e8400-e29b-41d4-a716-446655440000"), true);
      assert.strictEqual(isValidDeviceId("pixel_8_pro"), true);
      assert.strictEqual(isValidDeviceId("a"), true);
      assert.strictEqual(isValidDeviceId("1234567890"), true);
    });

    it("returns true for maximum length of 1500 characters", () => {
      const maxLenId = "a".repeat(1500);
      assert.strictEqual(isValidDeviceId(maxLenId), true);
    });

    it("returns false for empty string", () => {
      assert.strictEqual(isValidDeviceId(""), false);
    });

    it("returns false for IDs exceeding 1500 characters", () => {
      const tooLongId = "a".repeat(1501);
      assert.strictEqual(isValidDeviceId(tooLongId), false);
    });

    it("returns false for IDs containing forward slash", () => {
      assert.strictEqual(isValidDeviceId("device/123"), false);
      assert.strictEqual(isValidDeviceId("/"), false);
      assert.strictEqual(isValidDeviceId("/leading"), false);
      assert.strictEqual(isValidDeviceId("trailing/"), false);
    });

    it("returns false for single dot and double dot reserved path names", () => {
      assert.strictEqual(isValidDeviceId("."), false);
      assert.strictEqual(isValidDeviceId(".."), false);
      // More than two dots or dot in name is allowed by Firestore
      assert.strictEqual(isValidDeviceId("..."), true);
      assert.strictEqual(isValidDeviceId("device.1"), true);
    });

    it("returns false for reserved __.*__ patterns", () => {
      assert.strictEqual(isValidDeviceId("__foo__"), false);
      assert.strictEqual(isValidDeviceId("____"), false);
      assert.strictEqual(isValidDeviceId("__name__"), false);
    });

    it("returns true when __ is only at start or only at end", () => {
      assert.strictEqual(isValidDeviceId("__prefix"), true);
      assert.strictEqual(isValidDeviceId("suffix__"), true);
      assert.strictEqual(isValidDeviceId("_single_underscore_"), true);
    });
  });
});
