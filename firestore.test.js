const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const TEACHER_UID = "teacher_uid_2";
const STRANGER_UID = "stranger_uid_3";
const ACADEMY_ID = ADMIN_UID;

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastTime = () => new Date(Date.now() - 5000);

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

test("Unauthenticated user cannot read or write academy data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("academies").doc(ACADEMY_ID).get());
  await assertFails(
    unauthDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("students")
      .get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "a@b.com",
      displayName: "Admin",
      role: "admin",
      academyId: ACADEMY_ID,
      assignedGroupIds: [],
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );
});

test("Admin cannot escalate into another academy, but owns own academy", async () => {
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  await assertFails(
    strangerDb.collection("users").doc(STRANGER_UID).set({
      userId: STRANGER_UID,
      email: "s@b.com",
      displayName: "Stranger",
      academyId: ACADEMY_ID,
      role: "admin",
      assignedGroupIds: [],
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "admin@elite.edu",
      displayName: "مدير الأكاديمية",
      academyId: ACADEMY_ID,
      role: "admin",
      assignedGroupIds: [],
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).set({
      ownerUid: ADMIN_UID,
      academyName: "أكاديمية النخبة",
      subtitle: "التميز التعليمي",
      currencySymbol: "ج.م",
      primaryCalendar: "gregorian",
      absenceAlertThreshold: 3,
      evaluationCriteria: ["الفهم", "الواجبات", "المشاركة", "الاختبارات"],
      darkModeDefault: false,
      pinEnabled: false,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  // Reject ghost field / @DocumentId id field on create
  await assertFails(
    adminDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("groups")
      .doc("grp_bad")
      .set({
        id: "grp_bad",
        name: "مجموعة أ",
        gradeLevel: 1,
        teacherName: "أ. أحمد",
        teacherUids: [],
        scheduleText: "السبت والثلاثاء 4م",
        maxCapacity: 20,
        monthlyFee: 300,
        createdAt: pastTime(),
        updatedAt: pastTime(),
      })
  );
});

test("Full Admin & Teacher workflow: invite claim, scoped queries, attendance, evaluations, and payment isolation", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const now = pastTime();

  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).set({
      ownerUid: ADMIN_UID,
      academyName: "أكاديمية النخبة",
      subtitle: "نحو مستقبل أفضل",
      currencySymbol: "ج.م",
      primaryCalendar: "gregorian",
      absenceAlertThreshold: 3,
      evaluationCriteria: ["الفهم", "الواجبات", "المشاركة", "الاختبارات"],
      darkModeDefault: false,
      pinEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("groups")
      .doc("grp_1")
      .set({
        name: "مجموعة التفوق",
        gradeLevel: 6,
        teacherName: "أ. محمد علي",
        teacherUids: [],
        scheduleText: "الأحد والأربعاء 5:00 م",
        maxCapacity: 25,
        monthlyFee: 450,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    adminDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("students")
      .doc("stu_1")
      .set({
        fullName: "يوسف محمود حسن",
        guardianName: "محمود حسن",
        phone: "01012345678",
        enrollmentDateMillis: Date.now() - 86400000,
        gradeLevel: 6,
        groupId: "grp_1",
        teacherUids: [],
        notes: "",
        archived: false,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    adminDb.collection("invites").doc("ELITE2026").set({
      academyId: ACADEMY_ID,
      teacherName: "أ. محمد علي",
      assignedGroupIds: ["grp_1"],
      status: "pending",
      createdBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Teacher reads invite, registers membership, claims invite, and syncs teacherUids on assigned group/student
  await assertSucceeds(teacherDb.collection("invites").doc("ELITE2026").get());
  await assertSucceeds(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("members")
      .doc(TEACHER_UID)
      .set({
        uid: TEACHER_UID,
        displayName: "أ. محمد علي",
        email: "teacher@elite.edu",
        role: "teacher",
        assignedGroupIds: ["grp_1"],
        inviteCode: "ELITE2026",
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertSucceeds(
    teacherDb.collection("invites").doc("ELITE2026").update({
      status: "claimed",
      claimedByUid: TEACHER_UID,
      updatedAt: pastTime(),
    })
  );
  await assertSucceeds(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("groups")
      .doc("grp_1")
      .update({
        teacherUids: [TEACHER_UID],
        updatedAt: pastTime(),
      })
  );
  await assertSucceeds(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("students")
      .doc("stu_1")
      .update({
        teacherUids: [TEACHER_UID],
        updatedAt: pastTime(),
      })
  );

  // Scoped teacher query succeeds; unfiltered query fails
  await assertSucceeds(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("students")
      .where("teacherUids", "array-contains", TEACHER_UID)
      .get()
  );
  await assertFails(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("students")
      .get()
  );

  // Teacher records attendance and evaluation for assigned group
  await assertSucceeds(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("attendance")
      .doc("2026-11-17_stu_1")
      .set({
        studentId: "stu_1",
        studentName: "يوسف محمود حسن",
        groupId: "grp_1",
        teacherUids: [TEACHER_UID],
        dateKey: "2026-11-17",
        status: "present",
        recordedByUid: TEACHER_UID,
        notes: "",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("evaluations")
      .doc("eval_1")
      .set({
        studentId: "stu_1",
        studentName: "يوسف محمود حسن",
        groupId: "grp_1",
        teacherUids: [TEACHER_UID],
        subject: "الرياضيات",
        scores: { "الفهم": 23, "الواجبات": 24, "المشاركة": 25, "الاختبارات": 22 },
        totalScore: 94,
        maxTotalScore: 100,
        percentage: 94.0,
        gradeLabel: "ممتاز",
        evaluatedDateMillis: Date.now() - 1000,
        notes: "مستوى رائع",
        recordedByUid: TEACHER_UID,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Teacher CANNOT read or create payments
  await assertFails(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("payments")
      .get()
  );
  await assertFails(
    teacherDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("payments")
      .doc("pay_1")
      .set({
        studentId: "stu_1",
        studentName: "يوسف محمود حسن",
        groupId: "grp_1",
        monthKey: "2026-11",
        amountDue: 450,
        amountPaid: 450,
        paymentMethod: "cash",
        receiptNumber: "EL-202611-001",
        paidAtMillis: Date.now() - 1000,
        recordedByUid: TEACHER_UID,
        notes: "",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Admin CAN create payment
  await assertSucceeds(
    adminDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("payments")
      .doc("pay_1")
      .set({
        studentId: "stu_1",
        studentName: "يوسف محمود حسن",
        groupId: "grp_1",
        monthKey: "2026-11",
        amountDue: 450,
        amountPaid: 450,
        paymentMethod: "cash",
        receiptNumber: "EL-202611-001",
        paidAtMillis: Date.now() - 1000,
        recordedByUid: ADMIN_UID,
        notes: "تم السداد كاملاً",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Admin can read, update, and permanently delete a student; Teacher cannot delete
  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("students").doc("stu_1").get()
  );
  await assertFails(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("students").doc("stu_1").delete()
  );
  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("students").doc("stu_1").delete()
  );

  // Admin can create, update, and delete distinct subjects; invalid colorKey rejected; Teacher can list/read but cannot mutate
  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("subjects").doc("sub_math").set({
      name: "الرياضيات",
      normalizedName: "الرياضيات",
      code: "MATH",
      colorKey: "navy",
      description: "الجبر والهندسة والحساب",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("subjects").doc("sub_bad").set({
      name: "الفيزياء",
      normalizedName: "الفيزياء",
      code: "PHY",
      colorKey: "invalid_color",
      description: "",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("subjects").get()
  );
  await assertFails(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("subjects").doc("sub_sci").set({
      name: "العلوم",
      normalizedName: "العلوم",
      code: "SCI",
      colorKey: "emerald",
      description: "",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("subjects").doc("sub_math").update({
      colorKey: "gold",
      updatedAt: pastTime(),
    })
  );
  await assertFails(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("subjects").doc("sub_math").delete()
  );
  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("subjects").doc("sub_math").delete()
  );

  // Admin can create and update a group linked to a subject and teacher; invalid subjectColorKey rejected
  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("groups").doc("grp_linked").set({
      name: "مجموعة العباقرة - فيزياء",
      gradeLevel: 12,
      subjectId: "sub_phy",
      subjectName: "الفيزياء",
      subjectColorKey: "info",
      teacherId: "tch_1",
      teacherName: "أ. طارق حسن",
      teacherUids: [],
      scheduleText: "الإثنين والخميس 6م",
      maxCapacity: 30,
      monthlyFee: 500,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("groups").doc("grp_bad_color").set({
      name: "مجموعة غير صالحة",
      gradeLevel: 12,
      subjectId: "sub_phy",
      subjectName: "الفيزياء",
      subjectColorKey: "unknown",
      teacherName: "أ. طارق حسن",
      teacherUids: [],
      scheduleText: "",
      maxCapacity: 20,
      monthlyFee: 300,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("groups").doc("grp_linked").update({
      subjectId: "sub_chem",
      subjectName: "الكيمياء",
      subjectColorKey: "emerald",
      updatedAt: pastTime(),
    })
  );

  // Teacher joining via invite with allowTeacherGroupChoice can create their own group and select subject
  const teacher2Uid = "teacher_uid_chosen";
  const teacher2Db = testEnv.authenticatedContext(teacher2Uid).firestore();
  await assertSucceeds(
    adminDb.collection("invites").doc("ELITE9901").set({
      academyId: ACADEMY_ID,
      academyName: "أكاديمية النخبة",
      teacherName: "أ. سارة محمود",
      subjectName: "اللغة العربية",
      groupName: "",
      gradeLevel: 5,
      allowTeacherGroupChoice: true,
      availableGroupOptions: [],
      availableSubjectNames: ["اللغة العربية", "الرياضيات"],
      assignedGroupIds: [],
      status: "pending",
      createdBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    teacher2Db
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("members")
      .doc(teacher2Uid)
      .set({
        uid: teacher2Uid,
        displayName: "أ. سارة محمود",
        email: "sara@elite.edu",
        role: "teacher",
        assignedGroupIds: ["grp_sara_new"],
        inviteCode: "ELITE9901",
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertSucceeds(
    teacher2Db
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("groups")
      .doc("grp_sara_new")
      .set({
        name: "مجموعة النور - لغة عربية",
        gradeLevel: 5,
        subjectName: "اللغة العربية",
        subjectColorKey: "gold",
        teacherName: "أ. سارة محمود",
        teacherUids: [teacher2Uid],
        scheduleText: "",
        maxCapacity: 25,
        monthlyFee: 300,
        createdAt: now,
        updatedAt: now,
      })
  );
  // Stranger/teacher cannot create a group outside their assignedGroupIds
  await assertFails(
    teacher2Db
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("groups")
      .doc("grp_unauthorized")
      .set({
        name: "مجموعة غير مصرح بها",
        gradeLevel: 5,
        subjectName: "الرياضيات",
        subjectColorKey: "navy",
        teacherName: "أ. سارة محمود",
        teacherUids: [teacher2Uid],
        scheduleText: "",
        maxCapacity: 25,
        monthlyFee: 300,
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertSucceeds(
    teacher2Db.collection("invites").doc("ELITE9901").update({
      status: "claimed",
      claimedByUid: teacher2Uid,
      subjectName: "اللغة العربية",
      groupName: "مجموعة النور - لغة عربية",
      gradeLevel: 5,
      assignedGroupIds: ["grp_sara_new"],
      updatedAt: pastTime(),
    })
  );

  // Admin can create a student with prescribedFee and partial payment; negative prescribedFee is rejected
  await assertSucceeds(
    adminDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("students")
      .doc("stu_fee_1")
      .set({
        fullName: "مريم أحمد",
        guardianName: "أحمد علي",
        phone: "01099887766",
        enrollmentDateMillis: Date.now() - 1000,
        gradeLevel: 5,
        teacherUids: [],
        prescribedFee: 500,
        notes: "",
        archived: false,
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertFails(
    adminDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("students")
      .doc("stu_fee_bad")
      .set({
        fullName: "طالب برسوم سالبة",
        guardianName: "",
        phone: "01099887766",
        enrollmentDateMillis: Date.now() - 1000,
        gradeLevel: 5,
        teacherUids: [],
        prescribedFee: -50,
        notes: "",
        archived: false,
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertSucceeds(
    adminDb
      .collection("academies")
      .doc(ACADEMY_ID)
      .collection("payments")
      .doc("pay_partial_1")
      .set({
        studentId: "stu_fee_1",
        studentName: "مريم أحمد",
        monthKey: "2026-11",
        amountDue: 500,
        amountPaid: 200,
        paymentMethod: "cash",
        receiptNumber: "EL-202611-2222",
        paidAtMillis: Date.now() - 1000,
        recordedByUid: ADMIN_UID,
        notes: "سداد جزئي عند التسجيل",
        createdAt: now,
        updatedAt: now,
      })
  );
});



