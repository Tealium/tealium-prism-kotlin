package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.data.DataObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateProcessorTests {

    @Test
    fun process_Replaces_Simple_Variable_When_Key_Exists() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{name}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, John!", result)
    }

    @Test
    fun process_Replaces_Nested_Variable_When_Nested_Key_Exists() {
        val context = DataObject.create {
            put("user", DataObject.create {
                put("name", "Alice")
            })
        }
        val template = "Welcome, {{user.name}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Welcome, Alice!", result)
    }

    @Test
    fun process_Replaces_Multiple_Variables_When_All_Keys_Exist() {
        val context = DataObject.create {
            put("firstName", "Bob")
            put("lastName", "Smith")
        }
        val template = "{{firstName}} {{lastName}} is here"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Bob Smith is here", result)
    }

    @Test
    fun process_Replaces_With_Empty_String_When_Key_Does_Not_Exist() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{missingKey}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, !", result)
    }

    @Test
    fun process_Replaces_With_Fallback_When_Key_Does_Not_Exist_And_Fallback_Provided() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{missingKey || Guest}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, Guest!", result)
    }

    @Test
    fun process_Replaces_With_Value_When_Key_Exists_And_Fallback_Provided() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{name || Guest}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, John!", result)
    }

    @Test
    fun process_Replaces_With_Fallback_When_Nested_Key_Does_Not_Exist_And_Fallback_Provided() {
        val context = DataObject.create {
            put("user", DataObject.create {
                put("id", "123")
            })
        }
        val template = "Welcome, {{user.name || valued customer}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Welcome, valued customer!", result)
    }

    @Test
    fun process_Trims_Whitespace_In_Template_When_Spaces_Around_Variables() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{ name }}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, John!", result)
    }

    @Test
    fun process_Trims_Whitespace_In_Template_When_Spaces_Around_Fallback() {
        val context = DataObject.create {
            put("other", "value")
        }
        val template = "Hello, {{ missingKey || Guest User }}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, Guest User!", result)
    }

    @Test
    fun process_Replaces_With_Fallback_When_Invalid_Json_Path() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{invalid..path || Guest}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, Guest!", result)
    }

    @Test
    fun process_Replaces_With_Empty_String_When_Invalid_JsonPath_And_No_Fallback() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{invalid..path}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, !", result)
    }

    @Test
    fun process_Returns_Original_Text_When_No_Template_Variables() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, World!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, World!", result)
    }

    @Test
    fun process_Handles_Empty_Template_When_Empty_String() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = ""
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("", result)
    }

    @Test
    fun process_Handles_Empty_Context_When_Context_Is_Empty() {
        val context = DataObject.EMPTY_OBJECT
        val template = "Hello, {{name || Guest}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, Guest!", result)
    }

    @Test
    fun process_Handles_Number_Values_When_Value_Is_Numeric() {
        val context = DataObject.create {
            put("age", 25)
            put("score", 98.5)
        }
        val template = "Age: {{age}}, Score: {{score}}"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Age: 25, Score: 98.5", result)
    }

    @Test
    fun process_Formats_Decimal_Values_Without_Scientific_Notation_When_Value_Is_Large_Decimal() {
        val context = DataObject.create {
            put("age", 25)
            put("score", 100000000000000.1) // 1.000000000000001E14
        }
        val template = "Age: {{age}}, Score: {{score}}"

        val result = TemplateProcessor.process(template, context)

        assertEquals("Age: 25, Score: 100000000000000.1", result)
    }

    @Test
    fun process_Handles_Boolean_Values_When_Value_Is_Boolean() {
        val context = DataObject.create {
            put("isActive", true)
            put("isComplete", false)
        }
        val template = "Active: {{isActive}}, Complete: {{isComplete}}"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Active: true, Complete: false", result)
    }

    @Test
    fun process_Handles_Complex_Nested_Structure_When_Deeply_Nested_Data() {
        val context = DataObject.create {
            put("company", DataObject.create {
                put("department", DataObject.create {
                    put("employee", DataObject.create {
                        put("name", "Sarah")
                        put("role", "Developer")
                    })
                })
            })
        }
        val template = "{{company.department.employee.name}} works as a {{company.department.employee.role}}"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Sarah works as a Developer", result)
    }

    @Test
    fun process_Handles_Mixed_Valid_And_Invalid_Paths_When_Some_Paths_Exist_And_Others_Do_Not() {
        val context = DataObject.create {
            put("user", DataObject.create {
                put("name", "Tom")
            })
        }
        val template = "Hello {{user.name}}, your {{user.email || email}} is not set"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello Tom, your email is not set", result)
    }

    @Test
    fun process_Handles_Consecutive_Template_Variables_When_No_Space_Between_Variables() {
        val context = DataObject.create {
            put("first", "Hello")
            put("second", "World")
        }
        val template = "{{first}}{{second}}"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("HelloWorld", result)
    }

    @Test
    fun process_Handles_Empty_Fallback_When_Fallback_Is_Empty() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{missingKey || }}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, !", result)
    }

    @Test
    fun process_Handles_Multi_Line_Template_When_Template_Spans_Multiple_Lines() {
        val context = DataObject.create {
            put("name", "Alice")
            put("department", "Engineering")
            put("project", "Mobile App")
        }
        val template = """
            Employee: {{name}}
            Department: {{department}}
            Current Project: {{project || No project assigned}}
            Status: {{status || Active}}
        """.trimIndent()
        
        val result = TemplateProcessor.process(template, context)
        
        val expected = """
            Employee: Alice
            Department: Engineering
            Current Project: Mobile App
            Status: Active
        """.trimIndent()
        
        assertEquals(expected, result)
    }

    @Test
    fun process_Uses_First_Fallback_When_Multiple_Fallback_Delimiters_Present() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{missingKey || first fallback || second fallback || third}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, first fallback!", result)
    }

    @Test
    fun process_Uses_Value_When_Key_Exists_And_Multiple_Fallback_Delimiters_Present() {
        val context = DataObject.create {
            put("name", "John")
        }
        val template = "Hello, {{name || first fallback || second fallback}}!"
        
        val result = TemplateProcessor.process(template, context)
        
        assertEquals("Hello, John!", result)
    }
}
