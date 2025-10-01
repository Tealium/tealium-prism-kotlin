package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.data.DataObject
import org.junit.Assert.*

import org.junit.Test
import java.net.MalformedURLException

class HttpRequestTests {

    @Test
    fun equals_Returns_True_When_Equal() {
        val req1 = HttpRequest.get("http://localhost").build()
        val req2 = HttpRequest.get("http://localhost").build()

        assertEquals(req1, req2)
    }

    @Test
    fun equals_Returns_True_When_All_Values_Equal() {
        val req1 = HttpRequest.get("http://localhost")
            .body("test")
            .etag("etag")
            .gzip(true)
            .header("Accept-Encoding", "gzip")
            .build()
        val req2 = HttpRequest.get("http://localhost")
            .body("test")
            .etag("etag")
            .gzip(true)
            .header("Accept-Encoding", "gzip")
            .build()

        assertEquals(req1, req2)
    }

    @Test
    fun equals_Returns_False_When_Different() {
        val req1 = HttpRequest.get("http://localhost:80")
            .build()
        val req2 = HttpRequest.get("http://localhost:8080")
            .build()

        assertNotEquals(req1, req2)
    }

    @Test
    fun equals_Returns_False_When_Different_Methods() {
        val req1 = HttpRequest.post("http://localhost", "")
            .build()
        val req2 = HttpRequest.get("http://localhost")
            .body("")
            .build()

        assertNotEquals(req1, req2)
    }

    @Test
    fun post_Sets_Destination() {
        val req = HttpRequest.post("http://localhost", "body")
            .build()

        assertEquals("http://localhost", req.url.toString())
    }

    @Test
    fun post_Sets_Body() {
        val req = HttpRequest.post("http://localhost", "body")
            .build()

        assertEquals("body", req.body)
    }

    @Test
    fun get_Sets_Destination() {
        val req = HttpRequest.get("http://localhost")
            .build()

        assertEquals("http://localhost", req.url.toString())
    }

    @Test
    fun get_Sets_Etag_Header_When_Present() {
        val req = HttpRequest.get("http://localhost", "my etag")
            .build()

        assertEquals("my etag", req.headers[HttpRequest.Headers.ETAG])
    }

    @Test(expected = MalformedURLException::class)
    fun builder_Throws_When_Invalid_Url() {
        HttpRequest.Builder("://", HttpMethod.Post)
            .build()
    }

    @Test
    fun builder_Sets_ContentEncoding_Header_When_Gzipping() {
        val req = HttpRequest.Builder("http://localhost", HttpMethod.Get)
            .gzip(true)
            .build()

        assertEquals("gzip", req.headers[HttpRequest.Headers.CONTENT_ENCODING])
    }

    @Test
    fun builder_PrefersGzip_To_Header() {
        val req = HttpRequest.Builder("http://localhost", HttpMethod.Post)
            .gzip(true)
            .header(HttpRequest.Headers.CONTENT_ENCODING, "brotli")
            .build()

        assertEquals("gzip", req.headers[HttpRequest.Headers.CONTENT_ENCODING])
    }

    @Test
    fun builder_Sets_Etag_Header_When_Etag_Provided() {
        val req = HttpRequest.Builder("http://localhost", HttpMethod.Get)
            .etag("my etag")
            .build()

        assertEquals("my etag", req.headers[HttpRequest.Headers.ETAG])
    }

    @Test
    fun builder_PrefersEtag_To_Header() {
        val req = HttpRequest.Builder("http://localhost", HttpMethod.Post)
            .etag("correct etag")
            .header(HttpRequest.Headers.ETAG, "wrong etag")
            .build()

        assertEquals("correct etag", req.etag)
    }

    @Test
    fun builder_Sets_Body_When_Provided() {
        val req = HttpRequest.Builder("http://localhost", HttpMethod.Post)
            .body("my body")
            .build()

        assertEquals("my body", req.body)
    }

    @Test
    fun builder_Stringifies_Payload_When_Provided() {
        val req = HttpRequest.Builder("http://localhost", HttpMethod.Post)
            .body(DataObject.EMPTY_OBJECT)
            .build()

        assertEquals("{}", req.body)
    }
}