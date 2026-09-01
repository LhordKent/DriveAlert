const assert = require("node:assert/strict");
const { _test } = require("../index");

describe("connection request helpers", () => {
  it("normalizes only ordinary casing and whitespace", () => {
    assert.equal(_test.normalizeEmail("  Person+tag@GMAIL.com  "), "person+tag@gmail.com");
  });

  it("assigns Driver and Trusted Contact sides for either initiator", () => {
    const inviter = { uid: "inviter" };
    const target = { uid: "target" };
    assert.deepEqual(_test.participantDetails("DRIVER", inviter, target), {
      driver: inviter,
      trustedContact: target,
    });
    assert.deepEqual(_test.participantDetails("TRUSTED_CONTACT", inviter, target), {
      driver: target,
      trustedContact: inviter,
    });
  });
});
