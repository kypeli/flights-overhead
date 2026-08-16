import { EventEmitter } from "node:events";
import type { Request } from "firebase-functions/v2/https";
import type { Response } from "express";

/**
 * Options to initialize a mock Request object.
 */
export interface MockRequestOptions {
  method?: string;
  headers?: Record<string, string>;
  body?: unknown;
  query?: Record<string, string>;
}

/**
 * Creates a mock HTTP Request object suitable for Firebase Functions v2 testing.
 *
 * @param {MockRequestOptions} options Configuration options for the request.
 * @return {Request} Mocked Express/Firebase Request.
 */
export function createMockRequest(options: MockRequestOptions = {}): Request {
  const headers = options.headers ?? {};
  return {
    method: options.method ?? "GET",
    headers: headers,
    body: options.body,
    query: options.query ?? {},
    header(name: string) {
      return headers[name.toLowerCase()];
    },
    get(name: string) {
      return headers[name.toLowerCase()];
    },
  } as unknown as Request;
}

/**
 * Mock implementation of an Express Response object.
 */
export class MockResponse extends EventEmitter {
  public statusCode = 200;
  public headers: Record<string, string> = {};
  public body: unknown = null;

  /**
   * Sets the HTTP status code.
   *
   * @param {number} code The status code.
   * @return {this}
   */
  status(code: number): this {
    this.statusCode = code;
    return this;
  }

  /**
   * Sets an HTTP header.
   *
   * @param {string} name Header name.
   * @param {string} value Header value.
   * @return {this}
   */
  setHeader(name: string, value: string): this {
    this.headers[name.toLowerCase()] = value;
    return this;
  }

  /**
   * Gets an HTTP header.
   *
   * @param {string} name Header name.
   * @return {string | undefined}
   */
  getHeader(name: string): string | undefined {
    return this.headers[name.toLowerCase()];
  }

  /**
   * Sends a response body and emits the 'finish' event.
   *
   * @param {unknown} data Response body data.
   * @return {this}
   */
  send(data: unknown): this {
    this.body = data;
    this.emit("finish");
    return this;
  }

  /**
   * Sends a JSON response body and emits the 'finish' event.
   *
   * @param {unknown} data JSON serializable data.
   * @return {this}
   */
  json(data: unknown): this {
    this.body = data;
    this.emit("finish");
    return this;
  }

  /**
   * Ends the response and emits the 'finish' event.
   *
   * @return {this}
   */
  end(): this {
    this.emit("finish");
    return this;
  }
}

/**
 * Casts a MockResponse instance to Express Response for typing in handlers.
 *
 * @param {MockResponse} res The mock response instance.
 * @return {Response}
 */
export function asResponse(res: MockResponse): Response {
  return res as unknown as Response;
}
