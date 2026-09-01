const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");

initializeApp();

const GENERIC_RESULT = {
  message: "If this email belongs to a DriveAlert account, the request will appear in the app.",
};

function normalizeEmail(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function participantDetails(inviterRole, inviter, target) {
  if (inviterRole === "DRIVER") {
    return { driver: inviter, trustedContact: target };
  }
  return { driver: target, trustedContact: inviter };
}

function profileName(profile, fallbackEmail) {
  const parts = [profile?.firstName, profile?.middleName, profile?.lastName]
    .filter((part) => typeof part === "string" && part.trim().length > 0);
  return parts.join(" ") || fallbackEmail.split("@")[0];
}

exports.sendTrustedContactRequest = onCall(
  { region: "asia-southeast1", enforceAppCheck: false },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in to send a connection request.");
    }

    const email = normalizeEmail(request.data?.email);
    const inviterRole = request.data?.inviterRole;
    if (!email || !["DRIVER", "TRUSTED_CONTACT"].includes(inviterRole)) {
      throw new HttpsError("invalid-argument", "A valid email and inviter role are required.");
    }

    let targetAuthUser;
    try {
      targetAuthUser = await getAuth().getUserByEmail(email);
    } catch (error) {
      if (error?.code === "auth/user-not-found") return GENERIC_RESULT;
      throw error;
    }

    const inviterUid = request.auth.uid;
    if (targetAuthUser.uid === inviterUid) return GENERIC_RESULT;

    const inviterAuthUser = await getAuth().getUser(inviterUid);
    const { driver, trustedContact } = participantDetails(
      inviterRole,
      { uid: inviterUid, email: inviterAuthUser.email || "" },
      { uid: targetAuthUser.uid, email: targetAuthUser.email || email },
    );
    const connectionId = `${driver.uid}__${trustedContact.uid}`;
    const firestore = getFirestore();

    await firestore.runTransaction(async (transaction) => {
      const connectionRef = firestore.collection("trustedContactConnections").doc(connectionId);
      const driverProfileRef = firestore.collection("users").doc(driver.uid);
      const trustedProfileRef = firestore.collection("users").doc(trustedContact.uid);
      const [existing, driverProfile, trustedProfile] = await Promise.all([
        transaction.get(connectionRef),
        transaction.get(driverProfileRef),
        transaction.get(trustedProfileRef),
      ]);

      if (existing.exists && ["PENDING", "APPROVED"].includes(existing.get("status"))) return;

      transaction.set(connectionRef, {
        connectionId,
        driverUserId: driver.uid,
        trustedContactUserId: trustedContact.uid,
        requestedByUserId: inviterUid,
        driverName: profileName(driverProfile.data(), driver.email),
        driverEmail: driver.email,
        trustedContactName: profileName(trustedProfile.data(), trustedContact.email),
        trustedContactEmail: trustedContact.email,
        status: "PENDING",
        requestedAt: FieldValue.serverTimestamp(),
        approvedAt: null,
        declinedAt: null,
        revokedAt: null,
      });
    });

    return GENERIC_RESULT;
  },
);

exports._test = { normalizeEmail, participantDetails, profileName };
