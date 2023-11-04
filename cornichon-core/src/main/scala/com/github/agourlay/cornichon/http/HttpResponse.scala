package com.github.agourlay.cornichon.http

case class HttpResponse(status: Int, headers: Vector[(String, String)], body: String)
