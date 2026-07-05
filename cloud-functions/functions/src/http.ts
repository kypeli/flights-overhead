import { onRequest, Request, HttpsFunction } from "firebase-functions/v2/https";
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
 * Wraps an authenticated-POST handler with shared boilerplate: CORS
 * preflight, POST-method enforcement, and Firebase ID-token verification.
 * The wrapped handler runs only once the caller is authenticated.
 *
 * @param {AuthenticatedHandler} handler The endpoint-specific logic.
 * @return {HttpsFunction} The deployable HTTPS function.
 */
export function onAuthenticatedPost(
  handler: AuthenticatedHandler
): HttpsFunction {
  return onRequest({ region: "europe-north1", cors: true }, async (req, res) => {
    // Handle CORS preflight requests
    if (req.method === "OPTIONS") {
      res.status(204).send();
      return;
    }

    // Enforce POST request method
    if (req.method !== "POST") {
      res.status(405).send("Method Not Allowed");
      return;
    }

    // Validate Authorization header
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      res.status(401).send("Unauthorized: Missing or invalid token format");
      return;
    }

    const idToken = authHeader.split("Bearer ")[1];
    let uid: string;
    try {
      const decodedToken = await getAuth().verifyIdToken(idToken);
      uid = decodedToken.uid;
    } catch (error) {
      logger.error("Error verifying ID token:", error);
      res.status(401).send("Unauthorized: Invalid ID token");
      return;
    }

    await handler(req, res, uid);
  });
}
