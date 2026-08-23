import * as logger from "firebase-functions/logger";
import { getMessaging } from "firebase-admin/messaging";
import { db } from "./firebase";
import { onServicePost } from "./http";

/**
 * POST /pushNotification
 *
 * Dispatches a push notification to all registered client devices
 * when a flight enters the configured proximity zone.
 */
export const pushNotification = onServicePost(async (req, res, caller) => {
  logger.info(
    `pushNotification endpoint invoked by service caller ${caller.email || caller.sub}`,
  );
  const body = (req.body ?? {}) as Record<string, unknown>;
  const {
    hex,
    callsign,
    distanceKm,
    altitude,
    model,
    manufacturer,
    registration,
    originIATA,
    originICAO,
    destIATA,
    destICAO,
  } = body;

  if (typeof hex !== "string" || hex.trim().length === 0) {
    res.status(400).send("Bad Request: 'hex' is required and must be a string");
    return;
  }

  try {
    const tokensSnapshot = await db.collection("fcm_tokens").get();
    const tokens: string[] = [];

    for (const doc of tokensSnapshot.docs) {
      const data = doc.data();
      const token = (data.installationId as string | null);
      if (typeof token === "string" && token.length > 0) {
        tokens.push(token);
      }
    }

    if (tokens.length === 0) {
      logger.info("No registered devices found in fcm_tokens collection");
      res.status(200).json({ success: true, message: "No registered devices", sentCount: 0 });
      return;
    }

    const title = typeof callsign === "string" && callsign.trim().length > 0 ?
      `Flight ${callsign.trim()} Overhead` :
      `Aircraft ${hex} Overhead`;

    const distanceNum = typeof distanceKm === "number" ? distanceKm : undefined;
    const distanceText = distanceNum !== undefined ?
      ` is ${distanceNum.toFixed(1)} km away.` :
      " is nearby.";

    const bodyText = `${(typeof callsign === "string" && callsign.trim()) || hex}${distanceText}`;

    const dataPayload: Record<string, string> = {
      hex: String(hex),
    };
    if (typeof callsign === "string" && callsign.length > 0) dataPayload.callsign = callsign;
    if (distanceNum !== undefined) dataPayload.distanceKm = String(distanceNum);
    if (typeof altitude === "number") dataPayload.altitude = String(altitude);
    if (typeof model === "string") dataPayload.model = model;
    if (typeof manufacturer === "string") dataPayload.manufacturer = manufacturer;
    if (typeof registration === "string") dataPayload.registration = registration;
    const origin = (typeof originIATA === "string" && originIATA) || (typeof originICAO === "string" && originICAO);
    if (origin) dataPayload.origin = origin;
    const dest = (typeof destIATA === "string" && destIATA) || (typeof destICAO === "string" && destICAO);
    if (dest) dataPayload.destination = dest;

    const messaging = getMessaging();
    const response = await messaging.sendEachForMulticast({
      tokens,
      notification: {
        title,
        body: bodyText,
      },
      data: dataPayload,
    });

    logger.info(
      `Push notification dispatched for hex ${hex}. Successes: ${response.successCount}, ` +
      `Failures: ${response.failureCount}`,
    );

    res.status(200).json({
      success: true,
      sentCount: response.successCount,
      failureCount: response.failureCount,
    });
  } catch (error) {
    logger.error("Error dispatching push notifications:", error);
    res.status(500).send("Internal Server Error");
  }
});
