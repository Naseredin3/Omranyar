package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DocumentCategories
import com.example.data.model.DocumentPriorities
import com.example.data.model.DocumentStatuses
import com.example.data.model.SourceTypes
import com.example.data.model.TechnicalDocument
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OmranFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleDocument(uid: String) = TechnicalDocument(
        userId = uid,
        title = "گزارش آزمایش مقاومت بتن فونداسیون پل",
        category = DocumentCategories.QUALITY_CONTROL,
        priority = DocumentPriorities.HIGH,
        status = DocumentStatuses.UNDER_REVIEW,
        projectArea = "منطقه ۵ - تقاطع غیرهمسطح",
        sourceType = SourceTypes.TEXT,
        originalExcerpt = "نتایج آزمایش شکست ۲۸ روزه نمونه‌های بتن رده C35",
        summary = "مقاومت نمونه‌های بتن در محدوده استاندارد نشریه ۵۵ تایید شده است.",
        keyFindings = listOf("میانگین مقاومت ۳۸ مگاپاسکال"),
        actionItems = listOf("تایید نهایی توسط ناظر مقیم"),
        risksOrIssues = listOf("عدم تاخیر در عمل‌آوری"),
        tags = listOf("بتن", "کنترل کیفیت", "منطقه ۵")
    )

    @Test
    fun createAndObserveDocument_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = OmranFirestoreRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createDocument(uid, sampleDocument(uid))
        }
        assertTrue("Expected document creation to succeed", createResult.isSuccess)
        val docId = createResult.getOrThrow()
        assertTrue(docId.isNotBlank())

        val docs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDocuments(uid).first { list -> list.any { it.id == docId } }
        }
        val created = docs.first { it.id == docId }
        assertEquals(DocumentCategories.QUALITY_CONTROL, created.category)
        assertNotNull(created.createdAt)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateDocumentStatusAndCategory(
                userId = uid,
                documentId = docId,
                status = DocumentStatuses.APPROVED,
                priority = DocumentPriorities.NORMAL,
                category = DocumentCategories.QUALITY_CONTROL
            )
        }
        assertTrue("Expected status update to succeed", updateResult.isSuccess)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteDocument(uid, docId)
        }
        assertTrue("Expected delete to succeed", deleteResult.isSuccess)
    }

    @Test
    fun getDocumentById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = OmranFirestoreRepository(firestore)
        val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createDocument(aliceUid, sampleDocument(aliceUid)).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getDocumentById(aliceUid, docId)
        }
        assertTrue("Expected cross-user read to fail", bobRead.isFailure)
        val firestoreEx = bobRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeDocuments_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = OmranFirestoreRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeDocuments("some_user_uid").first()
            }
            fail("Expected unauthenticated observeDocuments to throw PERMISSION_DENIED")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    @Test
    fun chatMessages_authenticatedOwnerCreateObserveClear_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = OmranFirestoreRepository(firestore)

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addChatMessage(
                userId = uid,
                role = "user",
                text = "ضوابط روکش آسفالت در معابر اصفهان چیست؟",
                modelUsed = "gemini-3.8-flash"
            )
        }
        assertTrue("Expected chat message creation to succeed", msgResult.isSuccess)
        val msgId = msgResult.getOrThrow()

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeChatMessages(uid).first { list -> list.any { it.id == msgId } }
        }
        assertTrue(messages.any { it.id == msgId })

        val clearResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.clearChatHistory(uid)
        }
        assertTrue("Expected chat clear to succeed", clearResult.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_omran@isfahan.test"
        const val BOB_EMAIL = "bob_omran@isfahan.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
