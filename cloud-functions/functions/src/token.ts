import { db } from "./firebase";
import * as logger from "firebase-functions/logger";
import { FieldValue } from "firebase-admin/firestore";
import { ALLOWED_PLATFORMS, Platform, isValidDeviceId } from "./validation";
import { onPost } from "./http";

export const token = onPost(async (req, res, uid) => {
  // Parse and validate request body
  const body = (req.body ?? {}) as Record<string, unknown>;
  const { token: fcmToken, deviceId, platform = "android" } = body;

  if (typeof fcmToken !== "string" || fcmToken.length === 0) {
    res
      .status(400)
      .send("Bad Request: 'token' is required and must be a string");
    return;
  }

  if (
    deviceId !== undefined &&
    (typeof deviceId !== "string" || !isValidDeviceId(deviceId))
  ) {
    res.status(400).send("Bad Request: 'deviceId' is invalid");
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
    // Store/update the token in Firestore
    const docId = deviceId || fcmToken;
    const tokenRef = db.collection("fcm_tokens").doc(docId);

    await tokenRef.set(
      {
        token: fcmToken,
        uid: uid,
        deviceId: deviceId || null,
        platform: platform,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );

    // Avoid logging docId directly: without a deviceId it is the raw FCM
    // token, which is a credential.
    logger.info(
      `Successfully registered FCM token for user ${uid}` +
        (deviceId ? ` on device ${deviceId}` : ""),
    );
    res.status(200).json({ success: true });
  } catch (error) {
    logger.error("Error writing FCM token to Firestore:", error);
    res.status(500).send("Internal Server Error");
  }
});
