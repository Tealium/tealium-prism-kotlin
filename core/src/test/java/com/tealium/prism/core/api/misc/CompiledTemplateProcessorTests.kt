package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.get
import org.junit.Assert.assertEquals
import org.junit.Test

class CompiledTemplateProcessorTests : CommonTemplateProcessorTests() {
    override fun process(template: String, context: DataObject) =
        TemplateProcessor.compile(template).process(context)

    @Test
    fun substitutions_Contains_Single_Substitution_Path() {
        val templateText = """This template contains {{one}} variable."""
        val template = TemplateProcessor.compile(templateText)

        assertEquals(1, template.substitutions.size)
        assertEquals(JsonObjectPath["one"], template.substitutions.first())
    }

    @Test
    fun substitutions_Contains_Multiple_Substitution_Paths() {
        val templateText = """This template contains {{two}} {{variables}}."""
        val template = TemplateProcessor.compile(templateText)

        assertEquals(2, template.substitutions.size)
        assertEquals(JsonObjectPath["two"], template.substitutions[0])
        assertEquals(JsonObjectPath["variables"], template.substitutions[1])
    }

    @Test
    fun substitutions_Contains_Complex_Substitution_Paths() {
        val templateText = """{{one.two.three}} {{["one"]["two"]["three"]}}"""
        val template = TemplateProcessor.compile(templateText)

        assertEquals(2, template.substitutions.size)
        assertEquals(JsonObjectPath["one"]["two"]["three"], template.substitutions[0])
        assertEquals(JsonObjectPath["one"]["two"]["three"], template.substitutions[1])
    }

    @Test
    fun substitutions_Contains_Substitution_Paths_With_Fallback_Values() {
        val templateText = """{{one.two || three}} {{["one"]["two"] || three}}"""
        val template = TemplateProcessor.compile(templateText)

        assertEquals(2, template.substitutions.size)
        assertEquals(JsonObjectPath["one"]["two"], template.substitutions[0])
        assertEquals(JsonObjectPath["one"]["two"], template.substitutions[1])
    }

    @Test
    fun substitutions_Drops_Invalid_Substitution_Paths() {
        val templateText = """{{[0].key]}} {{one}} {{invalid-name["sub"]}} {{two}}"""
        val template = TemplateProcessor.compile(templateText)

        assertEquals(2, template.substitutions.size)
        assertEquals(JsonObjectPath["one"], template.substitutions[0])
        assertEquals(JsonObjectPath["two"], template.substitutions[1])
    }

    @Test
    fun substitutions_Drops_Blank_Substitution_Paths() {
        val templateText = """This {{}} contains empty {{}} substitutions"""
        val template = TemplateProcessor.compile(templateText)

        assertEquals(0, template.substitutions.size)
    }
}