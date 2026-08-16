import { onRequest, type Request, type HttpsFunction } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { getAuth } from "firebase-admin/auth";
import type { Response } from "express";

// Handler for an authenticated POST endpoint. `uid` is the verified
// caller's Firebase UID.
export type AuthenticatedHandler = (
  req: Request,
  res: Response,
  uid: string,
) => void | Promise<void>;

/**
 * Wraps an authenticated handler with shared boilerplate: CORS preflight, method enforcement, and
 * Firebase ID-token verification.
 * The wrapped handler runs only once the caller is authenticated.
 *
 * @param {AuthenticatedHandler} handler
 * @return {HttpsFunction}
 */
export function onGet(handler: AuthenticatedHandler): HttpsFunction {
  return onAuthenticatedMethod("GET", handler);
}

/**
 * Wraps an authenticated handler with shared boilerplate: CORS preflight, method enforcement, and
 * Firebase ID-token verification.
 * The wrapped handler runs only once the caller is authenticated.
 *
 * @param {AuthenticatedHandler} handler
 * @return {HttpsFunction}
 */
export function onPost(handler: AuthenticatedHandler): HttpsFunction {
  return onAuthenticatedMethod("POST", handler);
}

/**
 * Wraps an authenticated method handler with shared boilerplate: CORS
 * preflight, method enforcement, and Firebase ID-token verification.
 * The wrapped handler runs only once the caller is authenticated.
 *
 * @param {string} method HTTP method to enforce (e.g. "GET", "POST")
 * @param {AuthenticatedHandler} handler Handler function to run once the caller is authenticated
 * @return {HttpsFunction} An HTTPS function that handles the authenticated request
 */
function onAuthenticatedMethod(
  method: string,
  handler: AuthenticatedHandler,
): HttpsFunction {
  return onRequest(
    { region: "europe-north1", cors: true },
    async (req, res) => {
      if (req.method !== method) {
        res.status(405).send("Method Not Allowed");
        return;
      }

      const uid = await onAuthenticated(req, res);
      if (!uid) return;

      return handler(req, res, uid);
    },
  );
}

/**
 * Verifies the Firebase ID token and returns the caller's UID.
 *
 * @param {Request} req The incoming HTTP request.
 * @param {Response} res The outgoing HTTP response.
 * @return {Promise<string | null>} The caller's UID, or null if verification fails.
 */
async function onAuthenticated(
  req: Request,
  res: Response,
): Promise<string | null> {
  // Validate Authorization header
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    res.status(401).send("Unauthorized: Missing or invalid token format");
    return null;
  }

  const idToken = authHeader.split("Bearer ")[1];
  try {
    const decodedToken = await getAuth().verifyIdToken(idToken);
    return decodedToken.uid;
  } catch (error) {
    logger.error("Error verifying ID token:", error);
    res.status(401).send("Unauthorized: Invalid ID token");
    return null;
  }
}
