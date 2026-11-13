const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-25c5a84a-e944-4210-81f2-b0e854eeba4f";
const ALICE_UID = "alice_engineer";
const BOB_UID = "bob_engineer";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDocumentPayload(uid) {
  return {
    userId: uid,
    title: "گزارش پیشرفت پل غیرهمسطح شهید خرازی",
    category: "گزارش پیشرفت فیزیکی",
    priority: "بالا",
    status: "در دست بررسی",
    projectArea: "منطقه ۲ - معاونت عمران",
    sourceType: "متن",
    originalExcerpt: "بتن‌ریزی عرشه محور غربی با حجم ۱۲۰ متر مکعب انجام شد.",
    summary: "پیشرفت فیزیکی پروژه مطابق برنامه زمان‌بندی در بخش عرشه پل گزارش شده است.",
    keyFindings: ["تکمیل آرماتوربندی دهانه دوم"],
    actionItems: ["ارسال نمونه بتن ۲۸ روزه به آزمایشگاه"],
    risksOrIssues: ["نیاز به کنترل ترافیک محور مجاور"],
    tags: ["بتن‌ریزی", "پل", "منطقه ۲"],
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

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

test("Unauthenticated user cannot read or write documents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("documents").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_1").set(validDocumentPayload(ALICE_UID))
  );
});

test("Authenticated owner can create, list, update status, and delete own document", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_1");

  await assertSucceeds(docRef.set(validDocumentPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("documents").get());
  await assertSucceeds(docRef.update({ status: "تأیید شده", updatedAt: new Date() }));
  await assertSucceeds(docRef.delete());
});

test("Cross-user access is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice").set(validDocumentPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("documents").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice").update({ status: "تأیید شده" })
  );
});

test("Shadow update with ghost field or invalid category is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const badPayload = validDocumentPayload(ALICE_UID);
  badPayload.ghostField = true;
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_bad").set(badPayload)
  );

  const invalidCatPayload = validDocumentPayload(ALICE_UID);
  invalidCatPayload.category = "نامعتبر";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_bad_cat").set(invalidCatPayload)
  );
});

test("Authenticated owner can create and delete chat messages, but update is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      role: "user",
      text: "ضوابط نشریه ۵۵ برای روکش آسفالت چیست؟",
      createdAt: new Date(),
    })
  );
  await assertFails(msgRef.update({ text: "edited" }));
  await assertSucceeds(msgRef.delete());
});
