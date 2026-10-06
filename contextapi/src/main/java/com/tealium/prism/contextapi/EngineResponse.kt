package com.tealium.prism.contextapi

/**
 * Represents the response structure from the ContextApi engine.
 * 
 * Field names match the ContextApi documentation:
 * - flags: Boolean attributes
 * - metrics: Number attributes  
 * - properties: String attributes
 */
data class EngineResponse(
    val audiences: List<String>? = null,
    val badges: List<String>? = null,
    val flags: Map<String, Boolean>? = null,
    val dates: Map<String, Long>? = null,
    val metrics: Map<String, Double>? = null,
    val properties: Map<String, String>? = null
)

