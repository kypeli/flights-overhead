import { db } from "./firebase";
import * as logger from "firebase-functions/logger";
import { ALLOWED_PLATFORMS, Platform } from "./validation";
import { onPost } from "./http";

export const token = onPost(async (req, res, uid) => {
  // Parse and validate request body
  const body = (req.body ?? {}) as Record<string, unknown>;
  const { installationId, platform = "android" } = body;

  if (typeof installationId !== "string" || installationId.length === 0) {
    res
      .status(400)
      .send("Bad Request: 'installationId' is required and must be a string");
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

  try {
    // Store/update the installation ID in Firestore keyed by caller's Firebase UID
    const docId = uid;
    const tokenRef = db.collection("fcm_tokens").doc(docId);

    await tokenRef.set(
      {
        installationId: installationId,
        uid: uid,
        platform: platform,
        updatedAt: new Date().toISOString(),
      },
      { merge: true },
    );

    logger.info(
      `Successfully registered installation ID for user ${uid}`,
    );
    res.status(200).json({ success: true });
  } catch (error) {
    logger.error("Error writing installation ID to Firestore:", error);
    res.status(500).send("Internal Server Error");
  }
});
