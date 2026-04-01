package com.example.aiplatform

import com.example.aiplatform.assistant.SupportRagBootstrapper
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportRagImportTest {

    @Test
    fun `faq json import builds support faq index`() = runTest {
        val root = Files.createTempDirectory("support-rag-faq").toFile()
        try {
            File(root, "faq.json").writeText(
                """
                {
                  "faq": [
                    {"id": "1", "question": "Почему не работает авторизация?", "answer": "Проверьте корректность email/password."},
                    {"id": "2", "question": "Где история платежей?", "answer": "В профиле, раздел Billing."}
                  ]
                }
                """.trimIndent()
            )

            val ragRepository = FakeRagRepository()
            val bootstrapper = SupportRagBootstrapper(
                projectRepository = FakeProjectRepository(
                    mapOf("p1" to Project("p1", "Support", "", ProjectTextModel.GPT_5_MINI, 0L, root.absolutePath))
                ),
                ragRepository = ragRepository
            )

            val result = bootstrapper.importFaq("p1").getOrThrow()

            assertEquals("support-faq-index-p1", result.first)
            assertTrue(result.second >= 2)
            assertTrue(ragRepository.savedIndexes.any { it.id == "support-faq-index-p1" && it.title == "Support FAQ" })
            assertTrue(ragRepository.savedChunks.any { it.source == "support_faq|faq.json" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `support docs import builds support docs index`() = runTest {
        val root = Files.createTempDirectory("support-rag-docs").toFile()
        try {
            File(root, "support_docs.md").writeText(
                """
                # Авторизация

                Если авторизация не работает, сначала проверьте статус аккаунта.

                # Биллинг

                Ошибка оплаты часто связана с ограничениями банка.
                """.trimIndent()
            )

            val ragRepository = FakeRagRepository()
            val bootstrapper = SupportRagBootstrapper(
                projectRepository = FakeProjectRepository(
                    mapOf("p1" to Project("p1", "Support", "", ProjectTextModel.GPT_5_MINI, 0L, root.absolutePath))
                ),
                ragRepository = ragRepository
            )

            val result = bootstrapper.importSupportDocs("p1").getOrThrow()

            assertEquals("support-docs-index-p1", result.first)
            assertTrue(result.second >= 1)
            assertTrue(ragRepository.savedIndexes.any { it.id == "support-docs-index-p1" && it.title == "Support Docs" })
            assertTrue(ragRepository.savedChunks.any { it.source == "support_docs|support_docs.md" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `missing file fallback is safe`() = runTest {
        val root = Files.createTempDirectory("support-rag-missing").toFile()
        try {
            val bootstrapper = SupportRagBootstrapper(
                projectRepository = FakeProjectRepository(
                    mapOf("p1" to Project("p1", "Support", "", ProjectTextModel.GPT_5_MINI, 0L, root.absolutePath))
                ),
                ragRepository = FakeRagRepository()
            )

            val error = bootstrapper.importFaq("p1").exceptionOrNull()

            assertTrue(error?.message?.contains("faq.json not found", ignoreCase = true) == true)
        } finally {
            root.deleteRecursively()
        }
    }

    private class FakeProjectRepository(
        private val projects: Map<String, Project>
    ) : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = emptyFlow()
        override suspend fun getProject(projectId: String): Project? = projects[projectId]
        override suspend fun createProject(project: Project) {}
        override suspend fun updateProjectModel(projectId: String, model: ProjectTextModel) {}
        override suspend fun updateProjectRootPath(projectId: String, rootPath: String) {}
    }

    private class FakeRagRepository : RagRepository {
        val savedIndexes = mutableListOf<RagIndex>()
        val savedChunks = mutableListOf<RagDocumentChunk>()

        override fun observeIndexes(projectId: String): Flow<List<RagIndex>> = emptyFlow()
        override suspend fun listActiveIndexes(projectId: String): List<RagIndex> =
            savedIndexes.filter { it.projectId == projectId && it.isActive }

        override suspend fun upsertIndex(index: RagIndex) {
            savedIndexes.removeAll { it.id == index.id }
            savedIndexes += index
        }

        override suspend fun addDocuments(index: RagIndex, chunks: List<RagDocumentChunk>) {
            savedChunks += chunks
        }

        override suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk> = emptyList()
    }
}
