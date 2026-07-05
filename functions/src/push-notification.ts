import * as logger from "firebase-functions/logger";
import {onAuthenticatedPost} from "./http";

/**
 * POST /pushNotification
 *
 * Scaffold for a future push-notification endpoint. Shared concerns (CORS
 * preflight, POST enforcement, ID-token verification) are handled by
 * onAuthenticatedPost. The request payload contract is intentionally
 * undefined and must be designed when the endpoint is implemented.
 */
export const pushNotification = onAuthenticatedPost(async (req, res, uid) => {
  // TODO: define + validate the request payload, then perform the
  // push-notification logic. Payload shape is intentionally unspecified.
  logger.info(`pushNotification endpoint invoked by user ${uid}`);
  res.status(501).send("Not Implemented");
});
