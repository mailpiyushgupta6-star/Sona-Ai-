const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- SECURITY BOUNDS TESTS ---

test("Unauthenticated user: cannot read user profile or conversations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get());
});

test("Authenticated user: can create and read own conversation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");

  await assertSucceeds(
    convRef.set({
      id: "conv_1",
      userId: ALICE_UID,
      title: "My First Chat",
      modelId: "gemini-3.5-flash",
      isPinned: false,
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );

  await assertSucceeds(convRef.get());
});

test("Cross-user access: Bob cannot read or write Alice's conversation", async () => {
  // Seed Alice's conversation with admin context
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").set({
      id: "alice_conv",
      userId: ALICE_UID,
      title: "Alice Private Chat",
      modelId: "gemini-3.5-flash",
      isPinned: false,
      createdAt: new Date(),
      updatedAt: new Date()
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const targetDoc = bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv");

  await assertFails(targetDoc.get());
  await assertFails(targetDoc.set({
    id: "alice_conv",
    userId: BOB_UID,
    title: "Hacked",
    createdAt: new Date(),
    updatedAt: new Date()
  }));
});

test("Schema validation: rejects conversation missing required fields or with spoofed userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_invalid");

  // Spoofed userId
  await assertFails(
    convRef.set({
      id: "conv_invalid",
      userId: BOB_UID, // Spoofed!
      title: "Invalid Chat",
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );

  // Missing title
  await assertFails(
    convRef.set({
      id: "conv_invalid",
      userId: ALICE_UID,
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("Authenticated user: can create message in own conversation and Bob cannot read it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").collection("messages").doc("msg_1");

  await assertSucceeds(
    msgRef.set({
      id: "msg_1",
      conversationId: "conv_1",
      userId: ALICE_UID,
      role: "user",
      content: "Hello Sona",
      status: "COMPLETED",
      createdAt: new Date()
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").collection("messages").doc("msg_1").get()
  );
});
