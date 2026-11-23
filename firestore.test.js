const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated users cannot read or write profiles, favorites, or playlists", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Hacker",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("User profile: owner can create and update displayName; cross-user and immutable edits fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update of displayName succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Beats",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice",
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot read or modify Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Favorites: owner can add, list, and delete valid song; invalid sourceType or cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("srv_101")
      .set({
        userId: ALICE_UID,
        songId: "srv_101",
        title: "Saffron Echoes",
        artist: "Desi Ensemble",
        language: "Hindi",
        audioUrl: "https://example.com/track.mp3",
        sourceType: "REMOTE_LICENSED",
        createdAt: now,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );

  // Invalid sourceType rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("srv_102")
      .set({
        userId: ALICE_UID,
        songId: "srv_102",
        title: "Bad Source",
        artist: "Artist",
        language: "Punjabi",
        audioUrl: "https://example.com/track.mp3",
        sourceType: "PIRATED",
        createdAt: now,
      })
  );

  // Bob cannot read or delete Alice's favorites
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("srv_101")
      .delete()
  );

  // Owner can delete
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("srv_101")
      .delete()
  );
});

test("Playlists and tracks: owner CRUD succeeds and enforces parent playlist existence and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const playlistRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("playlists")
    .doc("pl_roadtrip");

  // Track cannot be added to non-existent playlist
  await assertFails(
    playlistRef.collection("tracks").doc("local_1").set({
      userId: ALICE_UID,
      songId: "local_1",
      title: "Road Song",
      artist: "Folk Artist",
      language: "Rajasthani",
      audioUrl: "content://media/external/audio/media/1",
      sourceType: "LOCAL_DEVICE",
      createdAt: created,
    })
  );

  // Create playlist
  await assertSucceeds(
    playlistRef.set({
      userId: ALICE_UID,
      name: "Desert Roadtrip",
      songCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Add track to existing playlist
  await assertSucceeds(
    playlistRef.collection("tracks").doc("local_1").set({
      userId: ALICE_UID,
      songId: "local_1",
      title: "Road Song",
      artist: "Folk Artist",
      language: "Rajasthani",
      audioUrl: "content://media/external/audio/media/1",
      sourceType: "LOCAL_DEVICE",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    playlistRef.update({
      songCount: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot list or mutate Alice's playlists
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_roadtrip")
      .collection("tracks")
      .get()
  );
});
