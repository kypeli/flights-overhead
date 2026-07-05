// Allowed client platforms for the FCM token registration.
export const ALLOWED_PLATFORMS = ["android", "ios", "web"] as const;
export type Platform = (typeof ALLOWED_PLATFORMS)[number];

/**
 * Validates a client-supplied deviceId for use as a Firestore document ID.
 * Firestore document IDs may not contain "/", be "." or "..", exceed 1500
 * bytes, or match the reserved "__.*__" pattern.
 *
 * @param {string} id The candidate device ID.
 * @return {boolean} True if the ID is safe to use as a document ID.
 */
export function isValidDeviceId(id: string): boolean {
  return (
    id.length > 0 &&
    id.length <= 1500 &&
    !id.includes("/") &&
    id !== "." &&
    id !== ".." &&
    !/^__.*__$/.test(id)
  );
}
