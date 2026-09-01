package com.ghealth.tools.core.network

import com.ghealth.tools.core.network.api.ProjectApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.http.Query

class ProjectApiArchivedQueryTest {

    @Test
    fun `project list exposes the archived query parameter`() {
        val method = ProjectApi::class.java.methods.single {
            it.name == "getProjects" && it.parameterCount == 2
        }
        val query = method.parameterAnnotations.first()
            .filterIsInstance<Query>()
            .single()

        assertEquals("archived", query.value)
    }
}
