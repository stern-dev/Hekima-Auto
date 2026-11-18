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

function nowTs() {
  return new Date();
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

test("Unauthenticated user cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("savedVehicles").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("savedVehicles").doc("v1").set({
      userId: ALICE_UID,
    })
  );
});

test("User can create and update their own UserProfile with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowTs();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Amina Otieno",
      email: "amina@example.com",
      themeMode: "system",
      preferredCurrency: "KES",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "dark",
      updatedAt: nowTs(),
    })
  );
});

test("Shadow update with ghost field or invalid themeMode fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowTs();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Amina",
      themeMode: "neon",
      preferredCurrency: "KES",
      isAdmin: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Cross-user isolation blocks Bob from reading or writing Alice savedVehicles and importedListings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("savedVehicles").doc("axio_2018").set({
      userId: ALICE_UID,
      listingId: "seed_axio_1",
      make: "Toyota",
      model: "Axio",
      year: 2018,
      askingPriceKes: 1380000,
      mileageKm: 74000,
      transmission: "Automatic",
      fuelType: "Petrol",
      engineCc: 1500,
      condition: "Good",
      importStatus: "Foreign Used",
      location: "Nairobi",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("savedVehicles").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("savedVehicles").doc("axio_2018").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("importedListings").doc("imp_1").set({
      userId: BOB_UID,
      sourceId: "src_1",
      sourceName: "Upload",
      make: "Mazda",
      model: "CX-5",
      year: 2018,
      askingPriceKes: 2850000,
      mileageKm: 65000,
      engineCc: 2000,
      transmission: "Automatic",
      fuelType: "Petrol",
      condition: "Excellent",
      importStatus: "Foreign Used",
      location: "Mombasa",
      demandScore: 86,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Owner can write and list comparisons, marketAnalyses, importedListings, and dataSources", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("comparisons").doc("cmp_1").set({
      userId: ALICE_UID,
      title: "2018 SUV & Sedan Benchmark",
      vehicleIds: ["v1", "v2"],
      vehicleSummaries: ["Toyota Axio 2018", "Mazda CX-5 2018"],
      winnerSummary: "Toyota Axio retains 78% value after 5 years.",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("marketAnalyses").doc("ma_1").set({
      userId: ALICE_UID,
      make: "Subaru",
      model: "Forester",
      year: 2018,
      mileageKm: 82000,
      estimatedPriceKes: 2790000,
      lowPriceKes: 2580000,
      highPriceKes: 2990000,
      annualDepreciationPct: 8.4,
      fiveYearProjectedKes: 1790000,
      summary: "Strong AWD demand in Nairobi and highland counties.",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dataSources").doc("ds_1").set({
      userId: ALICE_UID,
      name: "Nairobi Yard Feb CSV",
      sourceType: "csv",
      rowsImported: 24,
      duplicatesSkipped: 2,
      invalidRowsSkipped: 1,
      isActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("comparisons").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("marketAnalyses").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dataSources").get()
  );
});

test("Owner can manage watchlists and feed dataSources while invalid/cross-user writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dataSources").doc("ds_feed").set({
      userId: ALICE_UID,
      name: "Mombasa Live Feed",
      sourceType: "feed",
      rowsImported: 12,
      duplicatesSkipped: 0,
      invalidRowsSkipped: 0,
      isActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlists").doc("wl_1").set({
      userId: ALICE_UID,
      make: "Toyota",
      model: "Axio",
      minYear: 2017,
      maxYear: 2019,
      maxBudgetKes: 1320000,
      preferredImportStatus: "Foreign Used",
      isActive: true,
      pushAlertsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlists").doc("wl_1").update({
      isActive: false,
      pushAlertsEnabled: false,
      maxBudgetKes: 1350000,
      updatedAt: nowTs(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlists").doc("wl_1").update({
      pushAlertsEnabled: "always",
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlists").get()
  );

  // Invalid budget bounds & shadow field should fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlists").doc("wl_bad").set({
      userId: ALICE_UID,
      make: "Toyota",
      model: "Axio",
      minYear: 2019,
      maxYear: 2017,
      maxBudgetKes: 5000,
      preferredImportStatus: "All",
      isActive: true,
      extraField: "hack",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user read & write blocked
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("watchlists").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("watchlists").doc("wl_1").delete()
  );
});

test("Owner can manage inspections and feedSubscriptions while invalid/cross-user writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_1").set({
      userId: ALICE_UID,
      listingId: "seed_axio_1",
      vehicleTitle: "Toyota Axio 2018",
      location: "Nairobi",
      askingPriceKes: 1380000,
      overallScore: 84,
      verdictTier: "Buy Ready",
      estimatedRepairKes: 35000,
      recommendedOfferKes: 1345000,
      checklistSummary: ["Engine & CVT: Pass", "JEVIC / TIMS: Pass"],
      notes: "Clean frame and matching chassis.",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_1").update({
      overallScore: 72,
      verdictTier: "Negotiate Repairs",
      estimatedRepairKes: 65000,
      recommendedOfferKes: 1315000,
      updatedAt: nowTs(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_bad").set({
      userId: ALICE_UID,
      listingId: "seed_axio_1",
      vehicleTitle: "Toyota Axio 2018",
      location: "Nairobi",
      askingPriceKes: 1380000,
      overallScore: 150,
      verdictTier: "Unknown Tier",
      estimatedRepairKes: 0,
      recommendedOfferKes: 1380000,
      checklistSummary: ["Engine: Pass"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("feedSubscriptions").doc("sub_1").set({
      userId: ALICE_UID,
      name: "Mombasa Yard JSON",
      feedUrl: "https://example.co.ke/inventory.json",
      syncIntervalHours: 12,
      autoSyncEnabled: true,
      alertsEnabled: true,
      lastSyncedAtEpochMs: 1700000000000,
      lastStatusMessage: "Synced 8 units",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("feedSubscriptions").doc("sub_bad").set({
      userId: ALICE_UID,
      name: "Insecure Feed",
      feedUrl: "http://example.co.ke/inventory.json",
      syncIntervalHours: 1,
      autoSyncEnabled: true,
      alertsEnabled: true,
      lastSyncedAtEpochMs: 0,
      lastStatusMessage: "Bad",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("inspections").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("feedSubscriptions").doc("sub_1").delete()
  );
});

test("Shared /marketListings enforces auth read, seller ownership write/delete, and strict schema validation", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowTs();

  // Unauthenticated reads/writes blocked
  await assertFails(unauthDb.collection("marketListings").get());
  await assertFails(unauthDb.collection("marketListings").doc("yard_1").get());

  // Signed-in Alice can publish a valid live yard listing
  await assertSucceeds(
    aliceDb.collection("marketListings").doc("yard_1").set({
      sellerUserId: ALICE_UID,
      sellerName: "Amina Motors Kiambu Rd",
      make: "Toyota",
      model: "Harrier",
      year: 2019,
      askingPriceKes: 3850000,
      mileageKm: 58000,
      engineCc: 2000,
      transmission: "Automatic",
      fuelType: "Petrol",
      condition: "Excellent",
      importStatus: "Foreign Used",
      location: "Nairobi",
      demandScore: 88,
      listingOrigin: "live_yard",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Another signed-in user (Bob) can read/list shared marketListings
  await assertSucceeds(bobDb.collection("marketListings").get());
  await assertSucceeds(bobDb.collection("marketListings").doc("yard_1").get());

  // Bob cannot update or delete Alice's listing
  await assertFails(
    bobDb.collection("marketListings").doc("yard_1").update({
      askingPriceKes: 3500000,
      updatedAt: nowTs(),
    })
  );
  await assertFails(bobDb.collection("marketListings").doc("yard_1").delete());

  // Alice cannot spoof another sellerUserId or shadow fields
  await assertFails(
    aliceDb.collection("marketListings").doc("yard_spoof").set({
      sellerUserId: BOB_UID,
      sellerName: "Spoof",
      make: "Toyota",
      model: "Axio",
      year: 2018,
      askingPriceKes: 1350000,
      mileageKm: 60000,
      engineCc: 1500,
      transmission: "Automatic",
      fuelType: "Petrol",
      condition: "Good",
      importStatus: "Foreign Used",
      location: "Mombasa",
      demandScore: 80,
      listingOrigin: "live_yard",
      isAdminFeatured: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice can update her own listing price and delete it
  await assertSucceeds(
    aliceDb.collection("marketListings").doc("yard_1").update({
      askingPriceKes: 3790000,
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(aliceDb.collection("marketListings").doc("yard_1").delete());
});

