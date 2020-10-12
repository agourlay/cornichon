package com.github.agourlay.cornichon.http

case class HttpResponse(status: Int, headers: Seq[(String, String)] = Nil, body: String)
