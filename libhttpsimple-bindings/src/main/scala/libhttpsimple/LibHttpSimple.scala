/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Lightbend, Inc.
 */

package libhttpsimple

import scala.scalanative.native
import scala.scalanative.native._
import scala.util.{ Failure, Success, Try }

object LibHttpSimple {
  private val CRLF = "\r\n"
  private val HttpHeaderAndBodyPartsSeparator = CRLF + CRLF
  private val HttpHeaderNameAndValueSeparator = ":"

  case class InternalNativeFailure(errorCode: Long, errorDescription: String) extends RuntimeException(s"$errorCode: $errorDescription")

  case class HttpResponse(statusCode: Long, headers: Map[String, String], body: Option[String])

  def get(url: String): Try[HttpResponse] =
    doHttp("GET", url, headers = Map.empty, requestBody = None)

  def get(url: String, headers: Map[String, String]): Try[HttpResponse] =
    doHttp("GET", url, headers, requestBody = None)

  def post(url: String): Try[HttpResponse] =
    doHttp("POST", url, headers = Map.empty, requestBody = None)

  def post(url: String, headers: Map[String, String]): Try[HttpResponse] =
    doHttp("GET", url, headers, requestBody = None)

  def post(url: String, headers: Map[String, String], requestBody: String): Try[HttpResponse] =
    doHttp("GET", url, headers, requestBody = Some(requestBody))

  def post(url: String, requestBody: String): Try[HttpResponse] =
    doHttp("GET", url, headers = Map.empty, requestBody = Some(requestBody))

  private def doHttp(method: String, url: String, headers: Map[String, String], requestBody: Option[String]): Try[HttpResponse] =
    native.Zone { implicit z =>
      val http_response_struct = nativebinding.httpsimple.do_http(
        native.toCString(method),
        native.toCString(url),
        native.toCString(httpHeadersToDelimitedString(headers)),
        native.toCString(requestBody.getOrElse(""))
      )

      val errorCode = nativebinding.httpsimple.get_error_code(http_response_struct).cast[Long]
      val result =
        if (errorCode == 0) {
          val httpStatus = nativebinding.httpsimple.get_http_status(http_response_struct).cast[Long]
          val rawHttpResponse = native.fromCString(nativebinding.httpsimple.get_raw_http_response(http_response_struct))
          val (header, body) = rawHttpResponseToHttpHeadersAndBody(rawHttpResponse)

          Success(HttpResponse(httpStatus, header, body))
        } else {
          Failure(InternalNativeFailure(errorCode, errorMessageFromCode(errorCode)))
        }

      // Always cleanup to free the memory
      nativebinding.httpsimple.cleanup_http_response(http_response_struct)

      result
    }

  private def httpHeadersToDelimitedString(headers: Map[String, String]): String =
    headers
      .map {
        case (headerName, headerValue) => s"$headerName$HttpHeaderNameAndValueSeparator $headerValue"
      }
      .mkString(CRLF)

  private def rawHttpResponseToHttpHeadersAndBody(input: String): (Map[String, String], Option[String]) = {
    def splitBySeparator(v: String, separator: String): (String, String) = {
      val lineBreakIndex = v.indexOf(separator)
      val (l, r) = v.splitAt(lineBreakIndex)
      l -> r.substring(separator.length)
    }

    Option(input) match {
      case v @ Some(rawResponse) =>
        val (headerText, responseBody) = splitBySeparator(rawResponse, HttpHeaderAndBodyPartsSeparator)

        // Exclude the first line which is the HTTP status line
        val headers = headerText.split(CRLF).toList.tail.foldLeft(Map.empty[String, String]) { (v, l) =>
          val (headerName, headerValue) = splitBySeparator(l, HttpHeaderNameAndValueSeparator)
          v + (headerName -> headerValue.trim)
        }

        headers -> Option(responseBody)
      case _ =>
        Map.empty[String, String] -> Option.empty[String]
    }
  }

  private def errorMessageFromCode(errorCode: Long): String =
    if (errorCode == 1)
      "failure to `malloc` when initializing `raw_response`"
    else if (errorCode == 2)
      "failure to `realloc` when writing HTTP response body into to `raw_response`"
    else if (errorCode == 77)
      "failure to invoke `curl_easy_perform`."
    else
      "Unknown failure invoking native code"

}
