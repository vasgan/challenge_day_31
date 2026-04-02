package com.example.aiplatform

import com.example.aiplatform.data.mcp.support.SupportMcpServer
import com.example.aiplatform.data.mcp.support.SupportMcpToolExecutorImpl
import com.example.aiplatform.data.mcp.support.SupportToolRegistry
import com.example.aiplatform.data.support.SupportJsonDataSource
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.repository.ProjectRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportMcpServerTest {

    @Test
    fun `list and get users`() = runTest {
        val fixture = fixture()

        val users = fixture.server.supportListUsers("project-1").getOrThrow()
        val user = fixture.server.supportGetUser("project-1", "u1").getOrThrow()

        assertEquals(2, users.size)
        assertEquals("u1", user.id)
        assertEquals("Ivan", user.name)
    }

    @Test
    fun `list and get tickets plus user tickets`() = runTest {
        val fixture = fixture()

        val tickets = fixture.server.supportListTickets("project-1").getOrThrow()
        val ticket = fixture.server.supportGetTicket("project-1", "t1").getOrThrow()
        val userTickets = fixture.server.supportGetUserTickets("project-1", "u1").getOrThrow()

        assertEquals(2, tickets.size)
        assertEquals("t1", ticket.id)
        assertEquals(1, userTickets.size)
        assertEquals("u1", userTickets.first().userId)
    }

    @Test
    fun `support context includes user ticket and related tickets`() = runTest {
        val fixture = fixture()

        val context = fixture.server.supportBuildContext(
            projectId = "project-1",
            userId = "u1",
            ticketId = "t1"
        ).getOrThrow()

        assertEquals("u1", context.user?.id)
        assertEquals("t1", context.ticket?.id)
        assertTrue(context.relatedTickets.any { it.id == "t1" })
    }

    @Test
    fun `project isolation keeps support data separated`() = runTest {
        val fixture = fixture()

        val p1Users = fixture.server.supportListUsers("project-1").getOrThrow()
        val p2Users = fixture.server.supportListUsers("project-2").getOrThrow()
        val p2Error = fixture.server.supportGetTicket("project-2", "t1").exceptionOrNull()

        assertTrue(p1Users.any { it.id == "u1" })
        assertTrue(p2Users.any { it.id == "u9" })
        assertTrue(p2Users.none { it.id == "u1" })
        assertTrue(p2Error?.message?.contains("Ticket not found", ignoreCase = true) == true)
    }

    private fun fixture(): Fixture {
        val root1 = Files.createTempDirectory("support-mcp-p1").toFile().apply {
            File(this, "user.json").writeText(
                """
                [
                  {"id":"u1","name":"Ivan","email":"ivan@example.com","segment":"pro"},
                  {"id":"u2","name":"Kate","email":"kate@example.com","segment":"free"}
                ]
                """.trimIndent()
            )
            File(this, "ticket.json").writeText(
                """
                {
                  "tickets": [
                    {"id":"t1","userId":"u1","subject":"Login fails","status":"open","priority":"high"},
                    {"id":"t2","userId":"u2","subject":"Billing question","status":"closed","priority":"low"}
                  ]
                }
                """.trimIndent()
            )
        }
        val root2 = Files.createTempDirectory("support-mcp-p2").toFile().apply {
            File(this, "user.json").writeText(
                """
                [{"id":"u9","name":"Other User","segment":"enterprise"}]
                """.trimIndent()
            )
            File(this, "ticket.json").writeText(
                """
                [{"id":"t9","userId":"u9","subject":"Slow page","status":"open"}]
                """.trimIndent()
            )
        }

        val projectRepository = FakeProjectRepository(
            mapOf(
                "project-1" to Project("project-1", "P1", "", ProjectTextModel.GPT_5_MINI, 0L, root1.absolutePath),
                "project-2" to Project("project-2", "P2", "", ProjectTextModel.GPT_5_MINI, 0L, root2.absolutePath)
            )
        )
        val registry = SupportToolRegistry(projectRepository, SupportJsonDataSource())
        val server = SupportMcpServer(SupportMcpToolExecutorImpl(registry))
        return Fixture(server, listOf(root1, root2))
    }

    private data class Fixture(
        val server: SupportMcpServer,
        val roots: List<File>
    ) {
        @Suppress("unused")
        fun cleanup() {
            roots.forEach { it.deleteRecursively() }
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
}
