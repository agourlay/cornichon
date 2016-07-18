package com.github.agourlay.cornichon.http

case class CornichonHttpResponse(status: Int, headers: Seq[(String, String)] = Nil, body: String)
