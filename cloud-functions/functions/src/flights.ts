import { onGet } from "./http";
import { getFirestore } from "firebase-admin/firestore";

/**
 * GET /overheadFlights
 *
 * Retrieve the overhead flights. Requires a Firebase authenticated
 * user on the client side.
 */
export const overheadFlights = onGet(async (req, res) => {
  const db = getFirestore();
  const flightsRef = db.collection("active_flights");
  const snapshot = await flightsRef.get();
  const flights = snapshot.docs.map((doc) => doc.data());
  res.json(flights);
});
