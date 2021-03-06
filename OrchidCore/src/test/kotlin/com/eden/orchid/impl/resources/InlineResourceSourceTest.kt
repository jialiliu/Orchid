package com.eden.orchid.impl.resources

import com.eden.common.util.EdenPair
import com.eden.orchid.api.OrchidContext
import com.eden.orchid.api.resources.resource.OrchidResource
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.HashMap

class InlineResourceSourceTest {

    private var context: OrchidContext? = null
    private var underTest: InlineResourceSource? = null

    @BeforeEach
    fun setup() {
        context = mock(OrchidContext::class.java)
        underTest = InlineResourceSource(context!!)
    }

    @Test
    fun testInlineResourceSource() {
        val input = "inline:extra.scss:This is my content"
        val expected = "This is my content"

        `when`(context!!.getEmbeddedData(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(EdenPair(expected, HashMap()))
        `when`(context!!.getOutputExtension("scss")).thenReturn("css")

        val output = underTest!!.getResourceEntry(input)

        assertThat<OrchidResource>(output, `is`(notNullValue()))
        assertThat(output!!.content, `is`(equalTo(expected)))
        assertThat(output.reference.extension, `is`(equalTo("scss")))
        assertThat(output.reference.outputExtension, `is`(equalTo("css")))
    }


}
