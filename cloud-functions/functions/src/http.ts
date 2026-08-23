import { onRequest, type Request, type HttpsFunction } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { getAuth } from "firebase-admin/auth";
import { OAuth2Client } from "google-auth-library";
import type { Response } from "express";

// Caller identity for a verified Firebase user token.
export interface AuthenticatedUser {
  uid: string;
  email?: string;
}

// Handler for an authenticated endpoint. `user` contains the verified
// caller's Firebase identity (UID and optional email).
export type AuthenticatedHandler = (
  req: Request,
  res: Response,
  user: AuthenticatedUser,
) => void | Promise<void>;

// Caller identity for a verified Google Service Account OIDC token.
export interface ServiceCaller {
  email?: string;
  sub: string;
}

// Handler for a service-authenticated POST endpoint.
export type ServiceAuthenticatedHandler = (
  req: Request,
  res: Response,
  caller: ServiceCaller,
) => void | Promise<void>;

// OAuth2Client instance for verifying Google OIDC ID tokens.
export const oauth2Client = new OAuth2Client();

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
 * Wraps a service-authenticated handler with shared boilerplate: CORS preflight, method enforcement, and
 * Google Cloud Service Account OIDC ID-token verification.
 * The wrapped handler runs only once the caller's service account token is verified.
 *
 * @param {ServiceAuthenticatedHandler} handler
 * @return {HttpsFunction}
 */
export function onServicePost(handler: ServiceAuthenticatedHandler): HttpsFunction {
  return onRequest(
    { region: "europe-north1", cors: true },
    async (req, res) => {
      if (req.method !== "POST") {
        res.status(405).send("Method Not Allowed");
        return;
      }

      const caller = await onServiceAuthenticated(req, res);
      if (!caller) return;

      return handler(req, res, caller);
    },
  );
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

      const user = await onAuthenticated(req, res);
      if (!user) return;

      return handler(req, res, user);
    },
  );
}

/**
 * Verifies the Firebase ID token and returns the caller's user identity.
 *
 * @param {Request} req The incoming HTTP request.
 * @param {Response} res The outgoing HTTP response.
 * @return {Promise<AuthenticatedUser | null>} The caller's user identity, or null if verification fails.
 */
async function onAuthenticated(
  req: Request,
  res: Response,
): Promise<AuthenticatedUser | null> {
  // Validate Authorization header
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    res.status(401).send("Unauthorized: Missing or invalid token format");
    return null;
  }

  const idToken = authHeader.split("Bearer ")[1];
  try {
    const decodedToken = await getAuth().verifyIdToken(idToken);
    return {
      uid: decodedToken.uid,
      email: decodedToken.email,
    };
  } catch (error) {
    logger.error("Error verifying ID token:", error);
    res.status(401).send("Unauthorized: Invalid ID token");
    return null;
  }
}

/**
 * Verifies the Google Service Account OIDC ID token and returns the caller identity.
 *
 * @param {Request} req The incoming HTTP request.
 * @param {Response} res The outgoing HTTP response.
 * @return {Promise<ServiceCaller | null>} The caller identity, or null if verification fails.
 */
async function onServiceAuthenticated(
  req: Request,
  res: Response,
): Promise<ServiceCaller | null> {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    res.status(401).send("Unauthorized: Missing or invalid token format");
    return null;
  }

  const idToken = authHeader.split("Bearer ")[1];
  try {
    const ticket = await oauth2Client.verifyIdToken({
      idToken: idToken,
    });
    const payload = ticket.getPayload();
    if (!payload || !payload.sub) {
      logger.error("Google ID token payload is missing or invalid");
      res.status(401).send("Unauthorized: Invalid ID token payload");
      return null;
    }
    return {
      email: payload.email,
      sub: payload.sub,
    };
  } catch (error) {
    logger.error("Error verifying Google service ID token:", error);
    res.status(401).send("Unauthorized: Invalid ID token");
    return null;
  }
}

