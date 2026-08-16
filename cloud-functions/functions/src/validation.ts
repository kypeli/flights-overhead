// Allowed client platforms for the FCM token registration.
export const ALLOWED_PLATFORMS = ["android", "ios", "web"] as const;
export type Platform = (typeof ALLOWED_PLATFORMS)[number];

/**
 * Validates a candidate Firestore document ID (e.g. installationId).
 * Firestore document IDs may not contain "/", be "." or "..", exceed 1500
 * bytes, or match the reserved "__.*__" pattern.
 *
 * @param {string} id The candidate document ID.
 * @return {boolean} True if the ID is safe to use as a document ID.
 */
export function isValidDocumentId(id: string): boolean {
  return (
    id.length > 0 &&
    id.length <= 1500 &&
    !id.includes("/") &&
    id !== "." &&
    id !== ".." &&
    !/^__.*__$/.test(id)
  );
}

/** Legacy alias for isValidDocumentId */
export const isValidDeviceId = isValidDocumentId;
