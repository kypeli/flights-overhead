import { db } from "./firebase";
import * as logger from "firebase-functions/logger";
import { ALLOWED_PLATFORMS, Platform, isValidDocumentId } from "./validation";
import { onPost } from "./http";

export const token = onPost(async (req, res, user) => {
  // Parse and validate request body
  const body = (req.body ?? {}) as Record<string, unknown>;
  const { installationId, platform = "android", device } = body;

  if (typeof installationId !== "string" || installationId.length === 0) {
    res
      .status(400)
      .send("Bad Request: 'installationId' is required and must be a string");
    return;
  }

  if (!isValidDocumentId(installationId)) {
    res.status(400).send("Bad Request: 'installationId' is invalid");
    return;
  }

  if (
    typeof platform !== "string" ||
    !ALLOWED_PLATFORMS.includes(platform as Platform)
  ) {
    res
      .status(400)
      .send(
        `Bad Request: 'platform' must be one of ${ALLOWED_PLATFORMS.join(", ")}`,
      );
    return;
  }

  // Extract email from verified auth token with fallback to request body
  const bodyEmail =
    typeof body.email === "string" && body.email.trim().length > 0 ?
      body.email.trim() :
      null;
  const email = user.email ?? bodyEmail;

  // Sanitize optional device info object
  let deviceData: Record<string, unknown> | undefined = undefined;
  if (
    typeof device === "object" &&
    device !== null &&
    !Array.isArray(device)
  ) {
    const rawDevice = device as Record<string, unknown>;
    const sanitized: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(rawDevice)) {
      if (
        typeof value === "string" ||
        typeof value === "number" ||
        typeof value === "boolean"
      ) {
        sanitized[key] = value;
      }
    }
    if (Object.keys(sanitized).length > 0) {
      deviceData = sanitized;
    }
  }

  try {
    // Store/update the installation ID in Firestore keyed by installationId
    const docId = installationId;
    const tokenRef = db.collection("fcm_tokens").doc(docId);

    const documentData: Record<string, unknown> = {
      installationId: installationId,
      uid: user.uid,
      email: email,
      platform: platform,
      updatedAt: new Date().toISOString(),
    };

    if (deviceData) {
      documentData.device = deviceData;
    }

    await tokenRef.set(documentData, { merge: true });

    logger.info(
      `Successfully registered installation ID ${installationId} for user ${user.uid}`,
    );
    res.status(200).json({ success: true });
  } catch (error) {
    logger.error("Error writing installation ID to Firestore:", error);
    res.status(500).send("Internal Server Error");
  }
});

