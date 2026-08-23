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
    operator,
    registeredOwner,
    destCity,
    destName,
    originCity,
    originName,
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

    const flightNumber = typeof callsign === "string" && callsign.trim().length > 0 ?
      callsign.trim() :
      hex;

    const op = (typeof operator === "string" && operator.trim().length > 0 ? operator.trim() : undefined) ||
      (typeof registeredOwner === "string" && registeredOwner.trim().length > 0 ? registeredOwner.trim() : undefined);

    const title = op ?
      `${op} • ${flightNumber}` :
      (typeof callsign === "string" && callsign.trim().length > 0 ?
        `Flight ${flightNumber} Overhead` :
        `Aircraft ${hex} Overhead`);

    const originLocation = (typeof originCity === "string" &&
      originCity.trim().length > 0 ? originCity.trim() : undefined) ||
      (typeof originName === "string" && originName.trim().length > 0 ? originName.trim() : undefined) ||
      (typeof originIATA === "string" && originIATA.trim().length > 0 ? originIATA.trim() : undefined) ||
      (typeof originICAO === "string" && originICAO.trim().length > 0 ? originICAO.trim() : undefined);

    const distanceNum = typeof distanceKm === "number" ? distanceKm : undefined;
    const distanceText = distanceNum !== undefined ?
      `${distanceNum.toFixed(1)} km away` :
      undefined;

    let bodyText = "";
    if (originLocation && distanceText) {
      bodyText = `From ${originLocation} • ${distanceText}`;
    } else if (originLocation) {
      bodyText = `From ${originLocation}`;
    } else if (distanceText) {
      bodyText = `${distanceText}`;
    } else {
      bodyText = `${flightNumber} is nearby.`;
    }

    const dataPayload: Record<string, string> = {
      hex: String(hex),
    };
    if (typeof callsign === "string" && callsign.length > 0) dataPayload.callsign = callsign;
    if (distanceNum !== undefined) dataPayload.distanceKm = String(distanceNum);
    if (typeof altitude === "number") dataPayload.altitude = String(altitude);
    if (typeof model === "string") dataPayload.model = model;
    if (typeof manufacturer === "string") dataPayload.manufacturer = manufacturer;
    if (typeof registration === "string") dataPayload.registration = registration;
    if (typeof operator === "string" && operator.length > 0) dataPayload.operator = operator;
    if (typeof registeredOwner === "string" && registeredOwner.length > 0) {
      dataPayload.registeredOwner = registeredOwner;
    }
    if (typeof originCity === "string" && originCity.length > 0) dataPayload.originCity = originCity;
    if (typeof originName === "string" && originName.length > 0) dataPayload.originName = originName;
    if (typeof originIATA === "string" && originIATA.length > 0) dataPayload.originIATA = originIATA;
    if (typeof originICAO === "string" && originICAO.length > 0) dataPayload.originICAO = originICAO;
    if (typeof destCity === "string" && destCity.length > 0) dataPayload.destCity = destCity;
    if (typeof destName === "string" && destName.length > 0) dataPayload.destName = destName;
    if (typeof destIATA === "string" && destIATA.length > 0) dataPayload.destIATA = destIATA;
    if (typeof destICAO === "string" && destICAO.length > 0) dataPayload.destICAO = destICAO;
    const origin = (typeof originIATA === "string" && originIATA) || (typeof originICAO === "string" && originICAO);
    if (origin) dataPayload.origin = origin;
    const destCode = (typeof destIATA === "string" && destIATA) || (typeof destICAO === "string" && destICAO);
    if (destCode) dataPayload.destination = destCode;

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
