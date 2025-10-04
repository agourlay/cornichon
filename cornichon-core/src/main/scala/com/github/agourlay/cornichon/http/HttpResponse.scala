package com.github.agourlay.cornichon.http

case class HttpResponse(status: Short, headers: Vector[(String, String)], body: String)
